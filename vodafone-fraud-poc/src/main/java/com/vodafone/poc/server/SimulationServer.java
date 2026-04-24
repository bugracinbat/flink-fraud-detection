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
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

public class SimulationServer {

    public static final ConcurrentLinkedQueue<CdrEvent> cdrQueue = new ConcurrentLinkedQueue<>();
    public static final ConcurrentLinkedQueue<LocationEvent> locationQueue = new ConcurrentLinkedQueue<>();
    private static final CopyOnWriteArrayList<HttpExchange> sseClients = new CopyOnWriteArrayList<>();
    private static final Gson gson = new Gson();

    public static void startServer() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        
        server.createContext("/api/simulate", new SimulateHandler());
        server.createContext("/api/alerts", new AlertsHandler());
        
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.start();
        System.out.println("Simulation Server started on port 8080");
    }

    public static void broadcastAlert(FraudAlert alert) {
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

    private static void handleCors(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
        if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        }
    }

    static class SimulateHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            handleCors(exchange);
            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) return;

            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8);
                JsonObject jsonObject = gson.fromJson(isr, JsonObject.class);
                String scenario = jsonObject.has("scenario") ? jsonObject.get("scenario").getAsString() : "";

                long baseTime = System.currentTimeMillis();

                switch (scenario) {
                    case "SIM_CLONE":
                        locationQueue.offer(new LocationEvent("905559998877", "Germany", baseTime));
                        locationQueue.offer(new LocationEvent("905559998877", "Turkiye", baseTime + (5 * 60 * 1000)));
                        break;
                    case "SEQUENTIAL_DIALING":
                        String seqCaller = "905554443322";
                        long seqBase = 5550000;
                        for (int i = 0; i < 5; i++) {
                            long ts = baseTime + (i * 1000);
                            cdrQueue.offer(new CdrEvent(seqCaller, "90" + (seqBase + i), ts, 5, null, "Cell-B", "IMEI-2", 50.0, 100));
                        }
                        break;
                    case "STATICAL_RULE":
                        String staticCaller = "905553332211";
                        for (int i = 0; i < 11; i++) {
                            long ts = baseTime + (i * 1000);
                            cdrQueue.offer(new CdrEvent(staticCaller, "9055599900" + (i < 10 ? "0" + i : i), ts, 30, null, "Cell-C", "IMEI-3", 2.5, 2));
                        }
                        break;
                    case "CALL_FORWARDING":
                        String callerA = "905551111111";
                        String calleeB = "905552222222";
                        for (int i = 0; i < 6; i++) {
                            long ts = baseTime + (i * 1000);
                            String forwardedTo = "90555888880" + i;
                            cdrQueue.offer(new CdrEvent(callerA, calleeB, ts, 0, forwardedTo, "Cell-D", "IMEI-4", 200.0, 500));
                        }
                        break;
                }

                String response = "{\"status\":\"ok\"}";
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.getBytes().length);
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes());
                os.close();
            }
        }
    }

    static class AlertsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            handleCors(exchange);
            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) return;

            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.getResponseHeaders().add("Cache-Control", "no-cache");
            exchange.getResponseHeaders().add("Connection", "keep-alive");
            exchange.sendResponseHeaders(200, 0);
            
            sseClients.add(exchange);
        }
    }
}
