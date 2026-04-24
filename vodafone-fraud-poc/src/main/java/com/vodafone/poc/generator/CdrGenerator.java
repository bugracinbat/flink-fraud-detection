package com.vodafone.poc.generator;

import com.vodafone.poc.event.CdrEvent;
import com.vodafone.poc.server.SimulationServer;
import org.apache.flink.streaming.api.functions.source.SourceFunction;

public class CdrGenerator implements SourceFunction<CdrEvent> {
    private volatile boolean isRunning = true;

    @Override
    public void run(SourceContext<CdrEvent> ctx) throws Exception {
        long baseTime = System.currentTimeMillis();
        int normalCounter = 0;

        while (isRunning) {
            // Check for simulated events
            CdrEvent event = SimulationServer.cdrQueue.poll();
            if (event != null) {
                ctx.collectWithTimestamp(event, event.getTimestamp());
            } else {
                // Generate normal background noise
                if (normalCounter % 10 == 0) {
                    long ts = System.currentTimeMillis();
                    ctx.collectWithTimestamp(new CdrEvent("905551234567", "905559876543", ts, 120, null, "Cell-A", "IMEI-1", 100.0, 365), ts);
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
