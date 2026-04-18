package com.vodafone.poc.detector;

import com.vodafone.poc.event.FraudAlert;
import com.vodafone.poc.event.LocationEvent;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.cep.CEP;
import org.apache.flink.cep.PatternStream;
import org.apache.flink.cep.functions.PatternProcessFunction;
import org.apache.flink.cep.pattern.Pattern;
import org.apache.flink.cep.pattern.conditions.IterativeCondition;
import org.apache.flink.cep.pattern.conditions.SimpleCondition;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.KeyedStream;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.util.Collector;

import java.time.Duration;
import java.util.List;
import java.util.Map;

public class VelocityFraudDetector {

    public static DataStream<FraudAlert> detect(DataStream<LocationEvent> locationStream) {
        
        // Ensure events are timestamped correctly for CEP
        KeyedStream<LocationEvent, String> keyedStream = locationStream
                .assignTimestampsAndWatermarks(WatermarkStrategy.<LocationEvent>forBoundedOutOfOrderness(Duration.ofSeconds(5))
                        .withTimestampAssigner((event, timestamp) -> event.getTimestamp()))
                .keyBy(LocationEvent::getMsisdn);

        // Pattern: Event A followed by Event B within 15 minutes where location is different
        Pattern<LocationEvent, ?> pattern = Pattern.<LocationEvent>begin("first")
                .next("second")
                .where(new IterativeCondition<LocationEvent>() {
                    @Override
                    public boolean filter(LocationEvent second, Context<LocationEvent> ctx) throws Exception {
                        for (LocationEvent first : ctx.getEventsForPattern("first")) {
                            if (!first.getLocation().equals(second.getLocation())) {
                                return true;
                            }
                        }
                        return false;
                    }
                })
                .within(Time.minutes(15));

        PatternStream<LocationEvent> patternStream = CEP.pattern(keyedStream, pattern);

        return patternStream.process(new PatternProcessFunction<LocationEvent, FraudAlert>() {
            @Override
            public void processMatch(Map<String, List<LocationEvent>> match, Context ctx, Collector<FraudAlert> out) {
                LocationEvent first = match.get("first").get(0);
                LocationEvent second = match.get("second").get(0);

                String description = String.format("SIM clone detected. Location jump from %s to %s in %d ms.",
                        first.getLocation(), second.getLocation(), (second.getTimestamp() - first.getTimestamp()));

                out.collect(new FraudAlert(first.getMsisdn(), "VELOCITY_FRAUD_SIM_CLONE", description, second.getTimestamp()));
            }
        });
    }
}
