package com.vodafone.poc.generator;

import com.vodafone.poc.event.CdrEvent;
import org.apache.flink.streaming.api.functions.source.SourceFunction;

public class CdrGenerator implements SourceFunction<CdrEvent> {
    private volatile boolean isRunning = true;

    @Override
    public void run(SourceContext<CdrEvent> ctx) throws Exception {
        long baseTime = System.currentTimeMillis();

        // 1. Normal Call
        ctx.collectWithTimestamp(new CdrEvent("905551234567", "905559876543", baseTime, 120, null, "Cell-A", "IMEI-1", 100.0, 365), baseTime);
        Thread.sleep(500);

        // 2. Scenario 3: Sequential Dialing
        // MSISDN dials sequential numbers quickly
        String seqCaller = "905554443322";
        long seqBase = 5550000;
        for (int i = 0; i < 5; i++) {
            long ts = baseTime + (i * 1000);
            ctx.collectWithTimestamp(new CdrEvent(seqCaller, "90" + (seqBase + i), ts, 5, null, "Cell-B", "IMEI-2", 50.0, 100), ts);
            Thread.sleep(100);
        }

        // 3. Scenario 7: Statical Rule (Table API/SQL)
        // SIM age <= 3 days, data < 5MB, dials 10 distinct numbers
        String staticCaller = "905553332211";
        for (int i = 0; i < 11; i++) {
            long ts = baseTime + 10000 + (i * 1000);
            ctx.collectWithTimestamp(new CdrEvent(staticCaller, "9055599900" + (i < 10 ? "0" + i : i), ts, 30, null, "Cell-C", "IMEI-3", 2.5, 2), ts);
            Thread.sleep(100);
        }

        // 4. Scenario 8: Distance Forwarding Number
        // Number A calls B, but B is redirected to different C numbers
        String callerA = "905551111111";
        String calleeB = "905552222222";
        for (int i = 0; i < 6; i++) {
            long ts = baseTime + 20000 + (i * 1000);
            String forwardedTo = "90555888880" + i;
            ctx.collectWithTimestamp(new CdrEvent(callerA, calleeB, ts, 0, forwardedTo, "Cell-D", "IMEI-4", 200.0, 500), ts);
            Thread.sleep(100);
        }

        while (isRunning) {
            Thread.sleep(5000);
        }
    }

    @Override
    public void cancel() {
        isRunning = false;
    }
}
