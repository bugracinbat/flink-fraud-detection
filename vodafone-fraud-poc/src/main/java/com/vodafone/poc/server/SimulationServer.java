package com.vodafone.poc.server;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.vodafone.poc.event.CdrEvent;
import com.vodafone.poc.event.FraudAlert;
import com.vodafone.poc.event.LocationEvent;

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
        String msisdn = "905559998877";
        String[] locations = {"Germany", "Turkiye", "Netherlands", "Turkiye", "France", "Turkiye", "Spain", "Turkiye", "Italy"};

        for (int i = 0; i <= profile.simCloneJumps; i++) {
            long delay = i * profile.spacingMs;
            String location = locations[i % locations.length];
            events.add(ScheduledEvent.location(delay, new LocationEvent(msisdn, location, 0), false));
        }
    }

    private static void addSequentialDialingEvents(List<ScheduledEvent> events, SimulationProfile profile) {
        String caller = "905554443322";
        long seqBase = 905550000000L + (activeRunSequence.incrementAndGet() * 1000);

        for (int i = 0; i < profile.sequentialCalls; i++) {
            long delay = i * profile.spacingMs;
            String callee = String.valueOf(seqBase + i);
            events.add(ScheduledEvent.cdr(delay, new CdrEvent(caller, callee, 0, 5, null, "Cell-B", "IMEI-2", 50.0, 100), false));
        }
    }

    private static void addStaticRuleEvents(List<ScheduledEvent> events, SimulationProfile profile) {
        String caller = "905553332211";
        long calleeBase = 905559990000L + (activeRunSequence.incrementAndGet() * 1000);

        for (int i = 0; i < profile.staticDistinctCallees; i++) {
            long delay = i * profile.spacingMs;
            events.add(ScheduledEvent.cdr(delay, new CdrEvent(caller, String.valueOf(calleeBase + i), 0, 30, null, "Cell-C", "IMEI-3", 2.5, 2), false));
        }
    }

    private static void addCallForwardingEvents(List<ScheduledEvent> events, SimulationProfile profile) {
        String callerA = "905551111111";
        String calleeB = "905552222222";
        long forwardedBase = 905558888000L + (activeRunSequence.incrementAndGet() * 1000);

        for (int i = 0; i < profile.forwardingCalls; i++) {
            long delay = i * profile.spacingMs;
            String forwardedTo = String.valueOf(forwardedBase + i);
            events.add(ScheduledEvent.cdr(delay, new CdrEvent(callerA, calleeB, 0, 0, forwardedTo, "Cell-D", "IMEI-4", 200.0, 500), false));
        }
    }

    private static void addNearMissSimCloneEvents(List<ScheduledEvent> events, SimulationProfile profile) {
        String msisdn = "905559998866";
        for (int i = 0; i < 3; i++) {
            long delay = i * profile.spacingMs;
            events.add(ScheduledEvent.location(delay, new LocationEvent(msisdn, "Istanbul", 0), false));
        }
    }

    private static void addNearMissSequentialEvents(List<ScheduledEvent> events, SimulationProfile profile) {
        String caller = "905554443311";
        long seqBase = 905550900000L + (activeRunSequence.incrementAndGet() * 1000);
        for (int i = 0; i < 3; i++) {
            long delay = i * profile.spacingMs;
            events.add(ScheduledEvent.cdr(delay, new CdrEvent(caller, String.valueOf(seqBase + i), 0, 6, null, "Cell-NM", "IMEI-NM1", 40.0, 120), false));
        }
    }

    private static void addNearMissStaticRuleEvents(List<ScheduledEvent> events, SimulationProfile profile) {
        String caller = "905553332200";
        long calleeBase = 905559880000L + (activeRunSequence.incrementAndGet() * 1000);
        for (int i = 0; i < 9; i++) {
            long delay = i * profile.spacingMs;
            events.add(ScheduledEvent.cdr(delay, new CdrEvent(caller, String.valueOf(calleeBase + i), 0, 30, null, "Cell-NM", "IMEI-NM2", 2.5, 2), false));
        }
    }

    private static void addNearMissCallForwardingEvents(List<ScheduledEvent> events, SimulationProfile profile) {
        String callerA = "905551111100";
        String calleeB = "905552222200";
        long forwardedBase = 905558770000L + (activeRunSequence.incrementAndGet() * 1000);
        for (int i = 0; i < 3; i++) {
            long delay = i * profile.spacingMs;
            events.add(ScheduledEvent.cdr(delay, new CdrEvent(callerA, calleeB, 0, 0, String.valueOf(forwardedBase + i), "Cell-NM", "IMEI-NM3", 150.0, 500), false));
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
        String[] callers = {"905551234567", "905552468135", "905553579246", "905554681357", "905555792468", "905556813579"};
        String[] cellSites = {"Cell-A", "Cell-E", "Cell-F", "Cell-G", "Cell-H"};
        String caller = callers[random.nextInt(callers.length)];
        String callee = "90555" + (1000000 + random.nextInt(8000000));
        long duration = 20 + random.nextInt(260);
        String forwardedTo = null;
        double dataUsage = 20.0 + random.nextInt(900);
        int simAge = 30 + random.nextInt(900);

        if (index % 9 == 0) {
            duration = 0; // Dropped or missed call.
        } else if (index % 11 == 0) {
            duration = 0;
            callee = "DATA_SESSION_" + random.nextInt(1000);
            dataUsage = 80.0 + random.nextInt(1200);
        } else if (index % 13 == 0) {
            forwardedTo = "905557" + (100000 + random.nextInt(800000));
            duration = 8 + random.nextInt(20);
        } else if (index % 7 == 0) {
            duration = 3 + random.nextInt(12);
        }

        return new CdrEvent(caller, callee, 0, duration, forwardedTo, cellSites[random.nextInt(cellSites.length)], "IMEI-N" + random.nextInt(500), dataUsage, simAge);
    }

    private static LocationEvent backgroundLocationEvent() {
        String[] users = {"905557001001", "905557001002", "905557001003", "905557001004", "905557001005"};
        String[] locations = {"Istanbul", "Ankara", "Izmir", "Bursa", "Antalya"};
        int userIndex = random.nextInt(users.length);
        return new LocationEvent(users[userIndex], locations[userIndex], 0);
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
                } else {
                    LocationEvent location = event.locationEvent;
                    location.setTimestamp(now);
                    location.setRunId(run.runId);
                    locationQueue.offer(location);
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
