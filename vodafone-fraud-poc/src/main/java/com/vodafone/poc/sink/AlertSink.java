package com.vodafone.poc.sink;

import com.vodafone.poc.event.FraudAlert;
import com.vodafone.poc.server.SimulationServer;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;

public class AlertSink implements SinkFunction<FraudAlert> {
    @Override
    public void invoke(FraudAlert value, Context context) throws Exception {
        // Broadcast the alert to connected SSE clients
        SimulationServer.broadcastAlert(value);
        // Also print to console for debugging
        System.out.println(value);
    }
}
