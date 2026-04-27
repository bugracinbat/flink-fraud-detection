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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

public class SimulationServer {

    public static final ConcurrentLinkedQueue<CdrEvent> cdrQueue = new ConcurrentLinkedQueue<>();
    public static final ConcurrentLinkedQueue<LocationEvent> locationQueue = new ConcurrentLinkedQueue<>();
    private static final CopyOnWriteArrayList<HttpExchange> sseClients = new CopyOnWriteArrayList<>();
    private static final Gson gson = new Gson();
    private static final long startedAt = System.currentTimeMillis();
    private static final AtomicLong simulationsTriggered = new AtomicLong();
    private static final AtomicLong alertsBroadcast = new AtomicLong();
    private static final Map<String, String> scenarios = new LinkedHashMap<>();

    static {
        scenarios.put("SIM_CLONE", "Location velocity pattern using impossible Germany to Turkiye travel.");
        scenarios.put("SEQUENTIAL_DIALING", "Rapid calls to sequential destination numbers.");
        scenarios.put("STATICAL_RULE", "New SIM with low data usage and many distinct callees.");
        scenarios.put("CALL_FORWARDING", "One callee redirecting to many different forwarded numbers.");
    }

    public static void startServer() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        
        server.createContext("/api/simulate", new SimulateHandler());
        server.createContext("/api/alerts", new AlertsHandler());
        server.createContext("/api/status", new StatusHandler());
        
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.start();
        System.out.println("Simulation Server started on port 8080");
    }

    public static void broadcastAlert(FraudAlert alert) {
        alertsBroadcast.incrementAndGet();
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

                long baseTime = System.currentTimeMillis();
                int cdrEvents = 0;
                int locationEvents = 0;
                int expectedAlerts = 1;

                switch (scenario) {
                    case "SIM_CLONE":
                        locationQueue.offer(new LocationEvent("905559998877", "Germany", baseTime));
                        locationQueue.offer(new LocationEvent("905559998877", "Turkiye", baseTime + (5 * 60 * 1000)));
                        locationEvents = 2;
                        break;
                    case "SEQUENTIAL_DIALING":
                        String seqCaller = "905554443322";
                        long seqBase = 5550000;
                        for (int i = 0; i < 5; i++) {
                            long ts = baseTime + (i * 1000);
                            cdrQueue.offer(new CdrEvent(seqCaller, "90" + (seqBase + i), ts, 5, null, "Cell-B", "IMEI-2", 50.0, 100));
                        }
                        cdrEvents = 5;
                        break;
                    case "STATICAL_RULE":
                        String staticCaller = "905553332211";
                        for (int i = 0; i < 11; i++) {
                            long ts = baseTime + (i * 1000);
                            cdrQueue.offer(new CdrEvent(staticCaller, "9055599900" + (i < 10 ? "0" + i : i), ts, 30, null, "Cell-C", "IMEI-3", 2.5, 2));
                        }
                        cdrEvents = 11;
                        break;
                    case "CALL_FORWARDING":
                        String callerA = "905551111111";
                        String calleeB = "905552222222";
                        for (int i = 0; i < 6; i++) {
                            long ts = baseTime + (i * 1000);
                            String forwardedTo = "90555888880" + i;
                            cdrQueue.offer(new CdrEvent(callerA, calleeB, ts, 0, forwardedTo, "Cell-D", "IMEI-4", 200.0, 500));
                        }
                        cdrEvents = 6;
                        break;
                }

                simulationsTriggered.incrementAndGet();
                Map<String, Object> response = new LinkedHashMap<>();
                response.put("status", "ok");
                response.put("scenario", scenario);
                response.put("description", scenarios.get(scenario));
                response.put("cdrEvents", cdrEvents);
                response.put("locationEvents", locationEvents);
                response.put("expectedAlerts", expectedAlerts);
                response.put("queuedCdrEvents", cdrQueue.size());
                response.put("queuedLocationEvents", locationQueue.size());
                sendJson(exchange, 200, response);
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

            Map<String, Object> status = new LinkedHashMap<>();
            status.put("status", "ok");
            status.put("uptimeMs", System.currentTimeMillis() - startedAt);
            status.put("connectedClients", sseClients.size());
            status.put("queuedCdrEvents", cdrQueue.size());
            status.put("queuedLocationEvents", locationQueue.size());
            status.put("simulationsTriggered", simulationsTriggered.get());
            status.put("alertsBroadcast", alertsBroadcast.get());
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
}
