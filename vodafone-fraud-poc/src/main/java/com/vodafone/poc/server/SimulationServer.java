package com.vodafone.poc.server;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.vodafone.poc.event.CdrEvent;
import com.vodafone.poc.event.FraudAlert;
import com.vodafone.poc.event.LocationEvent;
import com.vodafone.poc.generator.TelecomData;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class SimulationServer {

    public static final ConcurrentLinkedQueue<CdrEvent> cdrQueue = new ConcurrentLinkedQueue<>();
    public static final ConcurrentLinkedQueue<LocationEvent> locationQueue = new ConcurrentLinkedQueue<>();
    private static final CopyOnWriteArrayList<HttpExchange> sseClients = new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<HttpExchange> eventClients = new CopyOnWriteArrayList<>();
    private static final Gson gson = new Gson();
    private static final long startedAt = System.currentTimeMillis();
    private static final AtomicLong simulationsTriggered = new AtomicLong();
    private static final AtomicLong alertsBroadcast = new AtomicLong();
    private static final AtomicLong eventsScheduled = new AtomicLong();
    private static final AtomicLong eventsEmitted = new AtomicLong();
    private static final AtomicLong activeRunSequence = new AtomicLong();
    private static final Random random = new Random();
    private static final ScheduledExecutorService simulationExecutor = Executors.newScheduledThreadPool(4);
    private static final ConcurrentHashMap<String, SimulationRun> activeRuns = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, SimulationRun> runsById = new ConcurrentHashMap<>();
    private static final Deque<SimulationRun> completedRuns = new ArrayDeque<>();
    private static final Map<String, String> scenarios = new LinkedHashMap<>();
    private static final int COMPLETED_RUN_HISTORY_LIMIT = 20;

    static {
        scenarios.put("SIM_CLONE", "Location velocity pattern using impossible country jumps.");
        scenarios.put("SEQUENTIAL_DIALING", "Rapid calls to sequential destination numbers.");
        scenarios.put("STATICAL_RULE", "New SIM with low data usage and many distinct callees.");
        scenarios.put("CALL_FORWARDING", "One callee redirecting to many different forwarded numbers.");
        scenarios.put("NEAR_MISS_SIM_CLONE", "Benign repeated location pings that should not trigger velocity fraud.");
        scenarios.put("NEAR_MISS_SEQUENTIAL", "Three sequential calls, just below the four-number threshold.");
        scenarios.put("NEAR_MISS_STATICAL_RULE", "New SIM with only nine distinct callees, below the SQL threshold.");
        scenarios.put("NEAR_MISS_CALL_FORWARDING", "Three forwarded destinations, below the forwarding threshold.");
    }

    public static void startServer() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

        server.createContext("/api/simulate", new SimulateHandler());
        server.createContext("/api/alerts", new AlertsHandler());
        server.createContext("/api/events", new EventsHandler());
        server.createContext("/api/process-event", new ProcessEventHandler());
        server.createContext("/api/status", new StatusHandler());

        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        System.out.println("Simulation Server started on port 8080");
    }

    public static void broadcastAlert(FraudAlert alert) {
        alertsBroadcast.incrementAndGet();
        recordActualAlert(alert);
        String json = gson.toJson(alert);
        String sseData = "data: " + json + "\n\n";
        byte[] bytes = sseData.getBytes(StandardCharsets.UTF_8);

        for (HttpExchange client : sseClients) {
            try {
                OutputStream os = client.getResponseBody();
                os.write(bytes);
                os.flush();
            } catch (IOException e) {
                sseClients.remove(client);
            }
        }

        Map<String, Object> streamEvent = new LinkedHashMap<>();
        streamEvent.put("streamType", "ALERT");
        streamEvent.put("timestamp", alert.getTimestamp());
        streamEvent.put("runId", alert.getRunId());
        streamEvent.put("msisdn", alert.getMsisdn());
        streamEvent.put("fraudType", alert.getFraudType());
        streamEvent.put("description", alert.getDescription());
        broadcastStreamEvent(streamEvent);
    }

    private static void recordActualAlert(FraudAlert alert) {
        String runId = alert.getRunId();
        if (runId == null || runId.isEmpty()) {
            return;
        }

        SimulationRun run = runsById.get(runId);
        if (run != null) {
            run.markAlert(alert);
        }
    }

    private static void broadcastStreamEvent(Map<String, Object> event) {
        String json = gson.toJson(event);
        String sseData = "data: " + json + "\n\n";
        byte[] bytes = sseData.getBytes(StandardCharsets.UTF_8);

        for (HttpExchange client : eventClients) {
            try {
                OutputStream os = client.getResponseBody();
                os.write(bytes);
                os.flush();
            } catch (IOException e) {
                eventClients.remove(client);
            }
        }
    }

    private static Map<String, Object> cdrStreamEvent(CdrEvent event, boolean background, String source) {
        Map<String, Object> streamEvent = new LinkedHashMap<>();
        streamEvent.put("streamType", "CDR");
        streamEvent.put("timestamp", event.getTimestamp());
        streamEvent.put("runId", event.getRunId());
        streamEvent.put("source", source);
        streamEvent.put("background", background);
        streamEvent.put("msisdn", event.getMsisdn());
        streamEvent.put("callee", event.getCallee());
        streamEvent.put("duration", event.getDuration());
        streamEvent.put("forwardedTo", event.getForwardedTo());
        streamEvent.put("cellSite", event.getCellSite());
        streamEvent.put("dataUsageMb", event.getDataUsageMb());
        streamEvent.put("simAgeDays", event.getSimAgeDays());
        return streamEvent;
    }

    private static Map<String, Object> locationStreamEvent(LocationEvent event, boolean background, String source) {
        Map<String, Object> streamEvent = new LinkedHashMap<>();
        streamEvent.put("streamType", "LOCATION");
        streamEvent.put("timestamp", event.getTimestamp());
        streamEvent.put("runId", event.getRunId());
        streamEvent.put("source", source);
        streamEvent.put("background", background);
        streamEvent.put("msisdn", event.getMsisdn());
        streamEvent.put("location", event.getLocation());
        return streamEvent;
    }

    private static SimulationProfile profileFor(String intensity) {
        switch (intensity) {
            case "HIGH":
                return new SimulationProfile("HIGH", 100, 8, 200, 80, 3, 80);
            case "MEDIUM":
                return new SimulationProfile("MEDIUM", 25, 4, 50, 24, 2, 220);
            case "LOW":
            default:
                return new SimulationProfile("LOW", 5, 2, 10, 6, 1, 350);
        }
    }

    private static String normalizeIntensity(JsonObject jsonObject) {
        if (jsonObject == null || !jsonObject.has("intensity")) {
            return "LOW";
        }

        String intensity = jsonObject.get("intensity").getAsString().toUpperCase(Locale.ROOT);
        if ("LOW".equals(intensity) || "MEDIUM".equals(intensity) || "HIGH".equals(intensity)) {
            return intensity;
        }
        return "LOW";
    }

    private static SimulationPlan createPlan(String scenario, String intensity) {
        SimulationProfile profile = profileFor(intensity);
        List<ScheduledEvent> events = new ArrayList<>();
        int fraudCdrEvents = 0;
        int fraudLocationEvents = 0;
        int expectedAlerts = 1;

        switch (scenario) {
            case "SIM_CLONE":
                fraudLocationEvents = profile.simCloneJumps + 1;
                expectedAlerts = profile.simCloneJumps;
                addSimCloneEvents(events, profile);
                break;
            case "SEQUENTIAL_DIALING":
                fraudCdrEvents = profile.sequentialCalls;
                expectedAlerts = Math.max(1, profile.sequentialCalls / 4);
                addSequentialDialingEvents(events, profile);
                break;
            case "STATICAL_RULE":
                fraudCdrEvents = profile.staticDistinctCallees;
                expectedAlerts = 1;
                addStaticRuleEvents(events, profile);
                break;
            case "CALL_FORWARDING":
                fraudCdrEvents = profile.forwardingCalls;
                expectedAlerts = Math.max(1, profile.forwardingCalls / 4);
                addCallForwardingEvents(events, profile);
                break;
            case "NEAR_MISS_SIM_CLONE":
                fraudLocationEvents = 3;
                expectedAlerts = 0;
                addNearMissSimCloneEvents(events, profile);
                break;
            case "NEAR_MISS_SEQUENTIAL":
                fraudCdrEvents = 3;
                expectedAlerts = 0;
                addNearMissSequentialEvents(events, profile);
                break;
            case "NEAR_MISS_STATICAL_RULE":
                fraudCdrEvents = 9;
                expectedAlerts = 0;
                addNearMissStaticRuleEvents(events, profile);
                break;
            case "NEAR_MISS_CALL_FORWARDING":
                fraudCdrEvents = 3;
                expectedAlerts = 0;
                addNearMissCallForwardingEvents(events, profile);
                break;
            default:
                throw new IllegalArgumentException("Unknown simulation scenario: " + scenario);
        }

        int fraudEvents = fraudCdrEvents + fraudLocationEvents;
        addBackgroundEvents(events, profile, Math.max(6, fraudEvents * profile.backgroundNoiseRatio));

        long durationMs = 0;
        int backgroundCdrEvents = 0;
        int backgroundLocationEvents = 0;
        for (ScheduledEvent event : events) {
            durationMs = Math.max(durationMs, event.delayMs);
            if (event.background) {
                if (event.cdrEvent != null) {
                    backgroundCdrEvents++;
                } else {
                    backgroundLocationEvents++;
                }
            }
        }

        return new SimulationPlan(
                scenario,
                profile,
                events,
                fraudCdrEvents,
                fraudLocationEvents,
                backgroundCdrEvents,
                backgroundLocationEvents,
                expectedAlerts,
                durationMs
        );
    }

    private static void addSimCloneEvents(List<ScheduledEvent> events, SimulationProfile profile) {
        String msisdn = TelecomData.fraudMsisdn(0);

        for (int i = 0; i <= profile.simCloneJumps; i++) {
            long delay = i * profile.spacingMs;
            String location = TelecomData.IMPOSSIBLE_ROAMING_LOCATIONS[i % TelecomData.IMPOSSIBLE_ROAMING_LOCATIONS.length];
            events.add(ScheduledEvent.location(delay, new LocationEvent(msisdn, location, 0), false));
        }
    }

    private static void addSequentialDialingEvents(List<ScheduledEvent> events, SimulationProfile profile) {
        String caller = TelecomData.fraudMsisdn(1);
        long seqBase = Long.parseLong(TelecomData.sequentialCalleeBase(activeRunSequence.incrementAndGet()));

        for (int i = 0; i < profile.sequentialCalls; i++) {
            long delay = i * profile.spacingMs;
            String callee = String.valueOf(seqBase + i);
            events.add(ScheduledEvent.cdr(delay, new CdrEvent(caller, callee, 0, 4 + random.nextInt(8), null, TelecomData.cellSite(random), TelecomData.imei(random), TelecomData.dataUsageMb(random), 180 + random.nextInt(900)), false));
        }
    }

    private static void addStaticRuleEvents(List<ScheduledEvent> events, SimulationProfile profile) {
        String caller = TelecomData.fraudMsisdn(2);
        long calleeBase = 905549900000L + (activeRunSequence.incrementAndGet() * 1000);

        for (int i = 0; i < profile.staticDistinctCallees; i++) {
            long delay = i * profile.spacingMs;
            events.add(ScheduledEvent.cdr(delay, new CdrEvent(caller, String.valueOf(calleeBase + i), 0, 18 + random.nextInt(45), null, TelecomData.cellSite(random), TelecomData.imei(random), 0.2 + random.nextDouble() * 3.5, 1 + random.nextInt(3)), false));
        }
    }

    private static void addCallForwardingEvents(List<ScheduledEvent> events, SimulationProfile profile) {
        String callerA = TelecomData.fraudMsisdn(3);
        String calleeB = TelecomData.fraudMsisdn(4);
        long forwardedBase = 905428880000L + (activeRunSequence.incrementAndGet() * 1000);

        for (int i = 0; i < profile.forwardingCalls; i++) {
            long delay = i * profile.spacingMs;
            String forwardedTo = String.valueOf(forwardedBase + i);
            events.add(ScheduledEvent.cdr(delay, new CdrEvent(callerA, calleeB, 0, 0, forwardedTo, TelecomData.cellSite(random), TelecomData.imei(random), TelecomData.dataUsageMb(random), 500 + random.nextInt(1200)), false));
        }
    }

    private static void addNearMissSimCloneEvents(List<ScheduledEvent> events, SimulationProfile profile) {
        String msisdn = TelecomData.fraudMsisdn(5);
        for (int i = 0; i < 3; i++) {
            long delay = i * profile.spacingMs;
            events.add(ScheduledEvent.location(delay, new LocationEvent(msisdn, "Istanbul/Kadikoy", 0), false));
        }
    }

    private static void addNearMissSequentialEvents(List<ScheduledEvent> events, SimulationProfile profile) {
        String caller = TelecomData.fraudMsisdn(5);
        long seqBase = 905550900000L + (activeRunSequence.incrementAndGet() * 1000);
        for (int i = 0; i < 3; i++) {
            long delay = i * profile.spacingMs;
            events.add(ScheduledEvent.cdr(delay, new CdrEvent(caller, String.valueOf(seqBase + i), 0, 5 + random.nextInt(12), null, TelecomData.cellSite(random), TelecomData.imei(random), 35.0, 120), false));
        }
    }

    private static void addNearMissStaticRuleEvents(List<ScheduledEvent> events, SimulationProfile profile) {
        String caller = TelecomData.fraudMsisdn(6);
        long calleeBase = 905559880000L + (activeRunSequence.incrementAndGet() * 1000);
        for (int i = 0; i < 9; i++) {
            long delay = i * profile.spacingMs;
            events.add(ScheduledEvent.cdr(delay, new CdrEvent(caller, String.valueOf(calleeBase + i), 0, 20 + random.nextInt(40), null, TelecomData.cellSite(random), TelecomData.imei(random), 2.5, 2), false));
        }
    }

    private static void addNearMissCallForwardingEvents(List<ScheduledEvent> events, SimulationProfile profile) {
        String callerA = TelecomData.fraudMsisdn(7);
        String calleeB = TelecomData.turkishMobile(random);
        long forwardedBase = 905558770000L + (activeRunSequence.incrementAndGet() * 1000);
        for (int i = 0; i < 3; i++) {
            long delay = i * profile.spacingMs;
            events.add(ScheduledEvent.cdr(delay, new CdrEvent(callerA, calleeB, 0, 0, String.valueOf(forwardedBase + i), TelecomData.cellSite(random), TelecomData.imei(random), 150.0, 500), false));
        }
    }

    private static void addBackgroundEvents(List<ScheduledEvent> events, SimulationProfile profile, int count) {
        long totalDuration = Math.max(profile.spacingMs * 8L, maxDelay(events));
        long step = Math.max(120L, totalDuration / Math.max(1, count));

        for (int i = 0; i < count; i++) {
            long delay = Math.max(50L, (i * step) + random.nextInt((int) Math.min(step, 250L)));
            if (i % 5 == 0) {
                events.add(ScheduledEvent.location(delay, backgroundLocationEvent(), true));
            } else {
                events.add(ScheduledEvent.cdr(delay, backgroundCdrEvent(i), true));
            }
        }
    }

    private static long maxDelay(List<ScheduledEvent> events) {
        long max = 0;
        for (ScheduledEvent event : events) {
            max = Math.max(max, event.delayMs);
        }
        return max;
    }

    private static CdrEvent backgroundCdrEvent(int index) {
        String caller = TelecomData.normalMsisdn(random);
        String callee = TelecomData.turkishMobile(random);
        long duration = TelecomData.voiceDurationSeconds(random);
        String forwardedTo = null;
        double dataUsage = TelecomData.dataUsageMb(random);
        int simAge = TelecomData.simAgeDays(random);

        if (index % 9 == 0) {
            duration = 0; // Dropped or missed call.
        } else if (index % 11 == 0) {
            duration = 0;
            callee = "DATA";
            dataUsage = TelecomData.roundOneDecimal(180.0 + random.nextDouble() * 2000.0);
        } else if (index % 13 == 0) {
            forwardedTo = TelecomData.turkishMobile(random);
            duration = 8 + random.nextInt(20);
        } else if (index % 7 == 0) {
            duration = 3 + random.nextInt(12);
        }

        return new CdrEvent(caller, callee, 0, duration, forwardedTo, TelecomData.cellSite(random), TelecomData.imei(random), dataUsage, simAge);
    }

    private static LocationEvent backgroundLocationEvent() {
        int userIndex = random.nextInt(TelecomData.NORMAL_MSISDNS.length);
        return new LocationEvent(TelecomData.NORMAL_MSISDNS[userIndex], TelecomData.homeLocation(userIndex), 0);
    }

    private static SimulationRun schedulePlan(SimulationPlan plan) {
        String runId = plan.scenario + "-" + UUID.randomUUID().toString().substring(0, 8);
        SimulationRun run = new SimulationRun(runId, plan);
        activeRuns.put(runId, run);
        runsById.put(runId, run);
        simulationsTriggered.incrementAndGet();
        eventsScheduled.addAndGet(plan.events.size());

        for (ScheduledEvent event : plan.events) {
            simulationExecutor.schedule(() -> {
                long now = System.currentTimeMillis();
                if (event.cdrEvent != null) {
                    CdrEvent cdr = event.cdrEvent;
                    cdr.setTimestamp(now);
                    cdr.setRunId(run.runId);
                    cdrQueue.offer(cdr);
                    broadcastStreamEvent(cdrStreamEvent(cdr, event.background, "simulation"));
                } else {
                    LocationEvent location = event.locationEvent;
                    location.setTimestamp(now);
                    location.setRunId(run.runId);
                    locationQueue.offer(location);
                    broadcastStreamEvent(locationStreamEvent(location, event.background, "simulation"));
                }

                eventsEmitted.incrementAndGet();
                if (run.markEventEmitted() >= run.totalEvents) {
                    run.completedAt = System.currentTimeMillis();
                    activeRuns.remove(run.runId);
                    recordCompletedRun(run);
                }
            }, event.delayMs, TimeUnit.MILLISECONDS);
        }

        return run;
    }

    private static synchronized void recordCompletedRun(SimulationRun run) {
        completedRuns.remove(run);
        completedRuns.addFirst(run);
        while (completedRuns.size() > COMPLETED_RUN_HISTORY_LIMIT) {
            SimulationRun removed = completedRuns.removeLast();
            runsById.remove(removed.runId);
        }
    }

    private static boolean handleCors(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
        if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return true;
        }
        return false;
    }

    private static void sendJson(HttpExchange exchange, int statusCode, Object body) throws IOException {
        String response = gson.toJson(body);
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    static class SimulateHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCors(exchange)) return;

            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8);
                JsonObject jsonObject = gson.fromJson(isr, JsonObject.class);
                String scenario = jsonObject != null && jsonObject.has("scenario") ? jsonObject.get("scenario").getAsString() : "";

                if (!scenarios.containsKey(scenario)) {
                    Map<String, Object> error = new LinkedHashMap<>();
                    error.put("status", "error");
                    error.put("message", "Unknown simulation scenario: " + scenario);
                    error.put("availableScenarios", scenarios.keySet());
                    sendJson(exchange, 400, error);
                    return;
                }

                String intensity = normalizeIntensity(jsonObject);
                SimulationPlan plan = createPlan(scenario, intensity);
                SimulationRun run = schedulePlan(plan);
                sendJson(exchange, 202, run.toResponse());
                return;
            }

            sendJson(exchange, 405, Map.of("status", "error", "message", "Method not allowed"));
        }
    }

    static class StatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCors(exchange)) return;

            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, Map.of("status", "error", "message", "Method not allowed"));
                return;
            }

            List<Map<String, Object>> runs = new ArrayList<>();
            for (SimulationRun run : activeRuns.values()) {
                runs.add(run.toStatus());
            }
            List<Map<String, Object>> history = new ArrayList<>();
            synchronized (SimulationServer.class) {
                for (SimulationRun run : completedRuns) {
                    history.add(run.toStatus());
                }
            }

            Map<String, Object> status = new LinkedHashMap<>();
            status.put("status", "ok");
            status.put("uptimeMs", System.currentTimeMillis() - startedAt);
            status.put("connectedClients", sseClients.size());
            status.put("eventStreamClients", eventClients.size());
            status.put("queuedCdrEvents", cdrQueue.size());
            status.put("queuedLocationEvents", locationQueue.size());
            status.put("simulationsTriggered", simulationsTriggered.get());
            status.put("alertsBroadcast", alertsBroadcast.get());
            status.put("eventsScheduled", eventsScheduled.get());
            status.put("eventsEmitted", eventsEmitted.get());
            status.put("activeRuns", runs);
            status.put("completedRuns", history);
            status.put("scenarios", scenarios);
            sendJson(exchange, 200, status);
        }
    }

    static class AlertsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCors(exchange)) return;

            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, Map.of("status", "error", "message", "Method not allowed"));
                return;
            }

            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.getResponseHeaders().add("Cache-Control", "no-cache");
            exchange.getResponseHeaders().add("Connection", "keep-alive");
            exchange.sendResponseHeaders(200, 0);

            sseClients.add(exchange);
        }
    }

    static class EventsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCors(exchange)) return;

            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, Map.of("status", "error", "message", "Method not allowed"));
                return;
            }

            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.getResponseHeaders().add("Cache-Control", "no-cache");
            exchange.getResponseHeaders().add("Connection", "keep-alive");
            exchange.sendResponseHeaders(200, 0);

            eventClients.add(exchange);
        }
    }

    static class ProcessEventHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCors(exchange)) return;

            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, Map.of("status", "error", "message", "Method not allowed"));
                return;
            }

            InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8);
            JsonObject jsonObject = gson.fromJson(isr, JsonObject.class);
            if (jsonObject == null || !jsonObject.has("streamType")) {
                sendJson(exchange, 400, Map.of("status", "error", "message", "streamType is required"));
                return;
            }

            String streamType = jsonObject.get("streamType").getAsString().toUpperCase(Locale.ROOT);
            long now = System.currentTimeMillis();
            String runId = jsonObject.has("runId") ? jsonObject.get("runId").getAsString() : "MANUAL-" + UUID.randomUUID().toString().substring(0, 8);

            if ("CDR".equals(streamType)) {
                String msisdn = jsonObject.has("msisdn") ? jsonObject.get("msisdn").getAsString() : TelecomData.normalMsisdn(random);
                String callee = jsonObject.has("callee") ? jsonObject.get("callee").getAsString() : TelecomData.turkishMobile(random);
                long duration = jsonObject.has("duration") ? jsonObject.get("duration").getAsLong() : 30;
                String forwardedTo = jsonObject.has("forwardedTo") && !jsonObject.get("forwardedTo").isJsonNull() ? jsonObject.get("forwardedTo").getAsString() : null;
                String cellSite = jsonObject.has("cellSite") ? jsonObject.get("cellSite").getAsString() : TelecomData.cellSite(random);
                String imei = jsonObject.has("imei") ? jsonObject.get("imei").getAsString() : TelecomData.imei(random);
                double dataUsageMb = jsonObject.has("dataUsageMb") ? jsonObject.get("dataUsageMb").getAsDouble() : TelecomData.dataUsageMb(random);
                int simAgeDays = jsonObject.has("simAgeDays") ? jsonObject.get("simAgeDays").getAsInt() : TelecomData.simAgeDays(random);

                CdrEvent event = new CdrEvent(msisdn, callee, now, duration, forwardedTo, cellSite, imei, dataUsageMb, simAgeDays);
                event.setRunId(runId);
                cdrQueue.offer(event);
                eventsScheduled.incrementAndGet();
                eventsEmitted.incrementAndGet();
                Map<String, Object> streamEvent = cdrStreamEvent(event, false, "manual");
                broadcastStreamEvent(streamEvent);
                sendJson(exchange, 202, streamEvent);
                return;
            }

            if ("LOCATION".equals(streamType)) {
                String msisdn = jsonObject.has("msisdn") ? jsonObject.get("msisdn").getAsString() : TelecomData.normalMsisdn(random);
                String location = jsonObject.has("location") ? jsonObject.get("location").getAsString() : TelecomData.normalLocation(random);
                LocationEvent event = new LocationEvent(msisdn, location, now);
                event.setRunId(runId);
                locationQueue.offer(event);
                eventsScheduled.incrementAndGet();
                eventsEmitted.incrementAndGet();
                Map<String, Object> streamEvent = locationStreamEvent(event, false, "manual");
                broadcastStreamEvent(streamEvent);
                sendJson(exchange, 202, streamEvent);
                return;
            }

            sendJson(exchange, 400, Map.of("status", "error", "message", "streamType must be CDR or LOCATION"));
        }
    }

    private static class SimulationProfile {
        private final String intensity;
        private final int sequentialCalls;
        private final int simCloneJumps;
        private final int staticDistinctCallees;
        private final int forwardingCalls;
        private final int backgroundNoiseRatio;
        private final long spacingMs;

        private SimulationProfile(String intensity, int sequentialCalls, int simCloneJumps, int staticDistinctCallees, int forwardingCalls, int backgroundNoiseRatio, long spacingMs) {
            this.intensity = intensity;
            this.sequentialCalls = sequentialCalls;
            this.simCloneJumps = simCloneJumps;
            this.staticDistinctCallees = staticDistinctCallees;
            this.forwardingCalls = forwardingCalls;
            this.backgroundNoiseRatio = backgroundNoiseRatio;
            this.spacingMs = spacingMs;
        }
    }

    private static class SimulationPlan {
        private final String scenario;
        private final SimulationProfile profile;
        private final List<ScheduledEvent> events;
        private final int fraudCdrEvents;
        private final int fraudLocationEvents;
        private final int backgroundCdrEvents;
        private final int backgroundLocationEvents;
        private final int expectedAlerts;
        private final long durationMs;

        private SimulationPlan(String scenario, SimulationProfile profile, List<ScheduledEvent> events, int fraudCdrEvents, int fraudLocationEvents, int backgroundCdrEvents, int backgroundLocationEvents, int expectedAlerts, long durationMs) {
            this.scenario = scenario;
            this.profile = profile;
            this.events = events;
            this.fraudCdrEvents = fraudCdrEvents;
            this.fraudLocationEvents = fraudLocationEvents;
            this.backgroundCdrEvents = backgroundCdrEvents;
            this.backgroundLocationEvents = backgroundLocationEvents;
            this.expectedAlerts = expectedAlerts;
            this.durationMs = durationMs;
        }
    }

    private static class SimulationRun {
        private final String runId;
        private final String scenario;
        private final String intensity;
        private final long startedAt;
        private final long durationMs;
        private final int totalEvents;
        private final int fraudCdrEvents;
        private final int fraudLocationEvents;
        private final int backgroundCdrEvents;
        private final int backgroundLocationEvents;
        private final int expectedAlerts;
        private final AtomicInteger emittedEvents = new AtomicInteger();
        private final AtomicInteger actualAlerts = new AtomicInteger();
        private volatile long completedAt;
        private volatile long firstAlertAt;
        private volatile long lastAlertAt;

        private SimulationRun(String runId, SimulationPlan plan) {
            this.runId = runId;
            this.scenario = plan.scenario;
            this.intensity = plan.profile.intensity;
            this.startedAt = System.currentTimeMillis();
            this.durationMs = plan.durationMs;
            this.totalEvents = plan.events.size();
            this.fraudCdrEvents = plan.fraudCdrEvents;
            this.fraudLocationEvents = plan.fraudLocationEvents;
            this.backgroundCdrEvents = plan.backgroundCdrEvents;
            this.backgroundLocationEvents = plan.backgroundLocationEvents;
            this.expectedAlerts = plan.expectedAlerts;
        }

        private int markEventEmitted() {
            return emittedEvents.incrementAndGet();
        }

        private void markAlert(FraudAlert alert) {
            actualAlerts.incrementAndGet();
            long alertTime = alert.getTimestamp();
            if (firstAlertAt == 0 || alertTime < firstAlertAt) {
                firstAlertAt = alertTime;
            }
            if (alertTime > lastAlertAt) {
                lastAlertAt = alertTime;
            }
        }

        private Map<String, Object> toResponse() {
            Map<String, Object> response = toStatus();
            response.put("status", "scheduled");
            response.put("description", scenarios.get(scenario));
            return response;
        }

        private Map<String, Object> toStatus() {
            Map<String, Object> status = new LinkedHashMap<>();
            status.put("runId", runId);
            status.put("scenario", scenario);
            status.put("intensity", intensity);
            status.put("startedAt", startedAt);
            status.put("durationMs", durationMs);
            status.put("totalEvents", totalEvents);
            status.put("emittedEvents", emittedEvents.get());
            status.put("fraudCdrEvents", fraudCdrEvents);
            status.put("fraudLocationEvents", fraudLocationEvents);
            status.put("backgroundCdrEvents", backgroundCdrEvents);
            status.put("backgroundLocationEvents", backgroundLocationEvents);
            status.put("expectedAlerts", expectedAlerts);
            status.put("actualAlerts", actualAlerts.get());
            status.put("alertDelta", actualAlerts.get() - expectedAlerts);
            status.put("firstAlertLatencyMs", firstAlertAt > 0 ? firstAlertAt - startedAt : null);
            status.put("lastAlertAt", lastAlertAt > 0 ? lastAlertAt : null);
            status.put("completed", completedAt > 0);
            status.put("completedAt", completedAt > 0 ? completedAt : null);
            return status;
        }
    }

    private static class ScheduledEvent {
        private final long delayMs;
        private final CdrEvent cdrEvent;
        private final LocationEvent locationEvent;
        private final boolean background;

        private ScheduledEvent(long delayMs, CdrEvent cdrEvent, LocationEvent locationEvent, boolean background) {
            this.delayMs = delayMs;
            this.cdrEvent = cdrEvent;
            this.locationEvent = locationEvent;
            this.background = background;
        }

        private static ScheduledEvent cdr(long delayMs, CdrEvent event, boolean background) {
            return new ScheduledEvent(delayMs, event, null, background);
        }

        private static ScheduledEvent location(long delayMs, LocationEvent event, boolean background) {
            return new ScheduledEvent(delayMs, null, event, background);
        }
    }
}
