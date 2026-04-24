package com.vodafone.poc.generator;

import com.vodafone.poc.event.LocationEvent;
import com.vodafone.poc.server.SimulationServer;
import org.apache.flink.streaming.api.functions.source.SourceFunction;

public class LocationGenerator implements SourceFunction<LocationEvent> {
    private volatile boolean isRunning = true;

    @Override
    public void run(SourceContext<LocationEvent> ctx) throws Exception {
        long baseTime = System.currentTimeMillis();
        int normalCounter = 0;

        while (isRunning) {
            // Check for simulated events
            LocationEvent event = SimulationServer.locationQueue.poll();
            if (event != null) {
                ctx.collectWithTimestamp(event, event.getTimestamp());
            } else {
                // Normal location update
                if (normalCounter % 20 == 0) {
                    long ts = System.currentTimeMillis();
                    ctx.collectWithTimestamp(new LocationEvent("905551112233", "Turkiye", ts), ts);
                }
                normalCounter++;
                Thread.sleep(100);
            }
        }
    }

    @Override
    public void cancel() {
        isRunning = false;
    }
}
