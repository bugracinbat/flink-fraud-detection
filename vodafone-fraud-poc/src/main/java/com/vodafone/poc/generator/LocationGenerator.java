package com.vodafone.poc.generator;

import com.vodafone.poc.event.LocationEvent;
import org.apache.flink.streaming.api.functions.source.SourceFunction;

public class LocationGenerator implements SourceFunction<LocationEvent> {
    private volatile boolean isRunning = true;

    @Override
    public void run(SourceContext<LocationEvent> ctx) throws Exception {
        long baseTime = System.currentTimeMillis();

        // 1. Normal location update
        ctx.collectWithTimestamp(new LocationEvent("905551112233", "Turkiye", baseTime), baseTime);

        Thread.sleep(1000);

        // 2. Scenario 1: SIM Clone / Velocity
        // Same MSISDN seen in Germany, then Turkiye within 5 minutes (300000 ms)
        long cloneTime1 = baseTime + 10000;
        ctx.collectWithTimestamp(new LocationEvent("905559998877", "Germany", cloneTime1), cloneTime1);
        
        Thread.sleep(1000);

        long cloneTime2 = cloneTime1 + (5 * 60 * 1000); // 5 minutes later
        ctx.collectWithTimestamp(new LocationEvent("905559998877", "Turkiye", cloneTime2), cloneTime2);

        // Keep it running so Flink doesn't exit immediately
        while (isRunning) {
            Thread.sleep(5000);
        }
    }

    @Override
    public void cancel() {
        isRunning = false;
    }
}
