package com.vodafone.poc.generator;

import com.vodafone.poc.event.CdrEvent;
import com.vodafone.poc.server.SimulationServer;
import org.apache.flink.streaming.api.functions.source.SourceFunction;

import java.util.Random;

public class CdrGenerator implements SourceFunction<CdrEvent> {
    private volatile boolean isRunning = true;
    private final Random random = new Random();
    private static final String[] NORMAL_CALLERS = {
            "905551234567", "905552468135", "905553579246", "905554681357", "905555792468", "905556813579"
    };
    private static final String[] CELL_SITES = {"Cell-A", "Cell-E", "Cell-F", "Cell-G", "Cell-H"};

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
        String caller = NORMAL_CALLERS[random.nextInt(NORMAL_CALLERS.length)];
        String callee = "90555" + (1000000 + random.nextInt(8000000));
        long duration = 20 + random.nextInt(260);
        String forwardedTo = null;
        double dataUsageMb = 15.0 + random.nextInt(900);
        int simAgeDays = 30 + random.nextInt(900);

        if (counter % 35 == 0) {
            duration = 0;
        } else if (counter % 49 == 0) {
            duration = 0;
            callee = "DATA_SESSION_" + random.nextInt(1000);
            dataUsageMb = 100.0 + random.nextInt(1400);
        } else if (counter % 67 == 0) {
            duration = 8 + random.nextInt(18);
            forwardedTo = "905557" + (100000 + random.nextInt(800000));
        } else if (counter % 23 == 0) {
            duration = 3 + random.nextInt(12);
        }

        return new CdrEvent(
                caller,
                callee,
                timestamp,
                duration,
                forwardedTo,
                CELL_SITES[random.nextInt(CELL_SITES.length)],
                "IMEI-N" + random.nextInt(500),
                dataUsageMb,
                simAgeDays
        );
    }

    @Override
    public void cancel() {
        isRunning = false;
    }
}
