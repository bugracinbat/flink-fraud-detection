package com.vodafone.poc.detector;

import com.vodafone.poc.event.CdrEvent;
import com.vodafone.poc.event.FraudAlert;
import org.apache.flink.api.common.state.ListState;
import org.apache.flink.api.common.state.ListStateDescriptor;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SequentialDialingDetector {

    public static DataStream<FraudAlert> detect(DataStream<CdrEvent> cdrStream) {
        return cdrStream
                .keyBy(CdrEvent::getMsisdn)
                .process(new SequentialDialingProcessFunction());
    }

    private static class SequentialDialingProcessFunction extends KeyedProcessFunction<String, CdrEvent, FraudAlert> {
        // Keeps track of dialed numbers for a caller within a window
        private transient ListState<Long> dialedNumbersState;
        private static final int SEQUENTIAL_THRESHOLD = 4; // Alert if 4 consecutive numbers dialed
        private static final long TIME_WINDOW_MS = 60000; // 1 minute window

        @Override
        public void open(Configuration parameters) {
            ListStateDescriptor<Long> descriptor = new ListStateDescriptor<>(
                    "dialedNumbers", Types.LONG);
            dialedNumbersState = getRuntimeContext().getListState(descriptor);
        }

        @Override
        public void processElement(CdrEvent event, Context ctx, Collector<FraudAlert> out) throws Exception {
            String callee = event.getCallee();
            
            // Attempt to parse callee as a number
            try {
                long dialedNum = Long.parseLong(callee);
                dialedNumbersState.add(dialedNum);

                // Register timer to clear state after TIME_WINDOW_MS
                ctx.timerService().registerEventTimeTimer(event.getTimestamp() + TIME_WINDOW_MS);

                // Check for sequence
                Iterable<Long> dialed = dialedNumbersState.get();
                List<Long> dialedList = new ArrayList<>();
                for (Long num : dialed) {
                    dialedList.add(num);
                }

                if (dialedList.size() >= SEQUENTIAL_THRESHOLD) {
                    Collections.sort(dialedList);
                    int maxSeqLength = 1;
                    int currentSeqLength = 1;

                    for (int i = 1; i < dialedList.size(); i++) {
                        if (dialedList.get(i) - dialedList.get(i - 1) == 1) {
                            currentSeqLength++;
                            maxSeqLength = Math.max(maxSeqLength, currentSeqLength);
                        } else if (dialedList.get(i) - dialedList.get(i - 1) != 0) {
                            currentSeqLength = 1;
                        }
                    }

                    if (maxSeqLength >= SEQUENTIAL_THRESHOLD) {
                        out.collect(new FraudAlert(
                                event.getMsisdn(),
                                "SEQUENTIAL_DIALING",
                                "Detected " + maxSeqLength + " sequential numbers dialed within short period.",
                                event.getTimestamp()
                        ));
                        // Clear state after alert
                        dialedNumbersState.clear();
                    }
                }

            } catch (NumberFormatException e) {
                // Ignore non-numeric callees
            }
        }

        @Override
        public void onTimer(long timestamp, OnTimerContext ctx, Collector<FraudAlert> out) throws Exception {
            // Clear state after window expires to avoid memory leaks
            dialedNumbersState.clear();
        }
    }
}
