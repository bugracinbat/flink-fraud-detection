package com.vodafone.poc.generator;

import com.vodafone.poc.event.LocationEvent;
import com.vodafone.poc.server.SimulationServer;
import org.apache.flink.streaming.api.functions.source.SourceFunction;

import java.util.Random;

public class LocationGenerator implements SourceFunction<LocationEvent> {
    private volatile boolean isRunning = true;
    private final Random random = new Random();
    private static final String[] NORMAL_USERS = {
            "905557001001", "905557001002", "905557001003", "905557001004", "905557001005"
    };
    private static final String[] LOCATIONS = {"Istanbul", "Ankara", "Izmir", "Bursa", "Antalya"};

    @Override
    public void run(SourceContext<LocationEvent> ctx) throws Exception {
        int normalCounter = 0;

        while (isRunning) {
            LocationEvent event = SimulationServer.locationQueue.poll();
            if (event != null) {
                ctx.collectWithTimestamp(event, event.getTimestamp());
            } else {
                if (normalCounter % 18 == 0) {
                    long ts = System.currentTimeMillis();
                    LocationEvent backgroundEvent = createBackgroundEvent(ts);
                    ctx.collectWithTimestamp(backgroundEvent, backgroundEvent.getTimestamp());
                }
                normalCounter++;
                Thread.sleep(100);
            }
        }
    }

    private LocationEvent createBackgroundEvent(long timestamp) {
        int userIndex = random.nextInt(NORMAL_USERS.length);
        String msisdn = NORMAL_USERS[userIndex];
        String location = LOCATIONS[userIndex];
        return new LocationEvent(msisdn, location, timestamp);
    }

    @Override
    public void cancel() {
        isRunning = false;
    }
}
