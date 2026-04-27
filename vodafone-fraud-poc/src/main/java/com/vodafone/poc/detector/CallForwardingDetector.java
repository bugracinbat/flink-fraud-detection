package com.vodafone.poc.detector;

import com.vodafone.poc.event.CdrEvent;
import com.vodafone.poc.event.FraudAlert;
import org.apache.flink.api.common.state.MapState;
import org.apache.flink.api.common.state.MapStateDescriptor;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;

public class CallForwardingDetector {

    public static DataStream<FraudAlert> detect(DataStream<CdrEvent> cdrStream) {
        // Key by the Callee (Number B)
        return cdrStream
                .filter(event -> event.getForwardedTo() != null && !event.getForwardedTo().isEmpty())
                .keyBy(CdrEvent::getCallee)
                .process(new ForwardingProcessFunction());
    }

    private static class ForwardingProcessFunction extends KeyedProcessFunction<String, CdrEvent, FraudAlert> {
        
        // MapState stores <ForwardedNumber, Timestamp>
        private transient MapState<String, Long> forwardedNumbersState;
        private static final int DISTINCT_FORWARD_THRESHOLD = 4;
        private static final long TIME_WINDOW_MS = 120000; // 2 minutes

        @Override
        public void open(Configuration parameters) {
            MapStateDescriptor<String, Long> descriptor = new MapStateDescriptor<>(
                    "forwardedNumbers", Types.STRING, Types.LONG);
            forwardedNumbersState = getRuntimeContext().getMapState(descriptor);
        }

        @Override
        public void processElement(CdrEvent event, Context ctx, Collector<FraudAlert> out) throws Exception {
            String forwardedTo = event.getForwardedTo();
            
            // Add the new forwarded number
            forwardedNumbersState.put(forwardedTo, event.getTimestamp());
            
            // Cleanup old forwarded numbers
            long currentTime = event.getTimestamp();
            int activeDistinctForwards = 0;
            
            for (String key : forwardedNumbersState.keys()) {
                long fwTime = forwardedNumbersState.get(key);
                if (currentTime - fwTime > TIME_WINDOW_MS) {
                    forwardedNumbersState.remove(key);
                } else {
                    activeDistinctForwards++;
                }
            }

            // Register timer for general cleanup
            ctx.timerService().registerEventTimeTimer(currentTime + TIME_WINDOW_MS);

            // Trigger alert if threshold is exceeded
            if (activeDistinctForwards >= DISTINCT_FORWARD_THRESHOLD) {
                out.collect(new FraudAlert(
                        event.getCallee(),
                        "DISTANCE_FORWARDING_FRAUD",
                        "Callee B (" + event.getCallee() + ") keeps redirecting calls to " + activeDistinctForwards + " different numbers.",
                        currentTime,
                        event.getRunId()
                ));
                // Clear state after alert
                forwardedNumbersState.clear();
            }
        }

        @Override
        public void onTimer(long timestamp, OnTimerContext ctx, Collector<FraudAlert> out) throws Exception {
            // General cleanup on timer
            forwardedNumbersState.clear();
        }
    }
}
