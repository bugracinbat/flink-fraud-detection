package com.vodafone.poc.generator;

import com.vodafone.poc.event.CdrEvent;
import com.vodafone.poc.server.SimulationServer;
import org.apache.flink.streaming.api.functions.source.SourceFunction;

import java.util.Random;

public class CdrGenerator implements SourceFunction<CdrEvent> {
    private volatile boolean isRunning = true;
    private final Random random = new Random();

    @Override
    public void run(SourceContext<CdrEvent> ctx) throws Exception {
        int normalCounter = 0;

        while (isRunning) {
            CdrEvent event = SimulationServer.cdrQueue.poll();
            if (event != null) {
                ctx.collectWithTimestamp(event, event.getTimestamp());
            } else {
                if (normalCounter % 6 == 0) {
                    long ts = System.currentTimeMillis();
                    CdrEvent backgroundEvent = createBackgroundEvent(ts, normalCounter);
                    ctx.collectWithTimestamp(backgroundEvent, backgroundEvent.getTimestamp());
                }
                normalCounter++;
                Thread.sleep(100);
            }
        }
    }

    private CdrEvent createBackgroundEvent(long timestamp, int counter) {
        String caller = TelecomData.normalMsisdn(random);
        String callee = TelecomData.turkishMobile(random);
        long duration = TelecomData.voiceDurationSeconds(random);
        String forwardedTo = null;
        double dataUsageMb = TelecomData.dataUsageMb(random);
        int simAgeDays = TelecomData.simAgeDays(random);

        if (counter % 35 == 0) {
            duration = 0;
        } else if (counter % 49 == 0) {
            duration = 0;
            callee = "DATA";
            dataUsageMb = TelecomData.roundOneDecimal(250.0 + random.nextDouble() * 1800.0);
        } else if (counter % 67 == 0) {
            duration = 8 + random.nextInt(18);
            forwardedTo = TelecomData.turkishMobile(random);
        } else if (counter % 23 == 0) {
            duration = 3 + random.nextInt(12);
        }

        return new CdrEvent(
                caller,
                callee,
                timestamp,
                duration,
                forwardedTo,
                TelecomData.cellSite(random),
                TelecomData.imei(random),
                dataUsageMb,
                simAgeDays
        );
    }

    @Override
    public void cancel() {
        isRunning = false;
    }
}
