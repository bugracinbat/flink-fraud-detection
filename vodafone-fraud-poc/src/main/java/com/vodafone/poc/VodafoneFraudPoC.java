package com.vodafone.poc;

import com.vodafone.poc.detector.CallForwardingDetector;
import com.vodafone.poc.detector.SequentialDialingDetector;
import com.vodafone.poc.detector.StaticalRuleSqlDetector;
import com.vodafone.poc.detector.VelocityFraudDetector;
import com.vodafone.poc.event.CdrEvent;
import com.vodafone.poc.event.FraudAlert;
import com.vodafone.poc.event.LocationEvent;
import com.vodafone.poc.generator.CdrGenerator;
import com.vodafone.poc.generator.LocationGenerator;
import com.vodafone.poc.server.SimulationServer;
import com.vodafone.poc.sink.AlertSink;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;

import java.time.Duration;

public class VodafoneFraudPoC {

    public static void main(String[] args) throws Exception {
        
        // 1. Set up the execution environment
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        StreamTableEnvironment tEnv = StreamTableEnvironment.create(env);
        
        // Use event time for accurate complex event processing
        env.setParallelism(1);

        // 2. Add Sources (Generators)
        DataStream<LocationEvent> locationStream = env.addSource(new LocationGenerator())
                .assignTimestampsAndWatermarks(WatermarkStrategy.<LocationEvent>forBoundedOutOfOrderness(Duration.ofSeconds(5))
                        .withTimestampAssigner((event, timestamp) -> event.getTimestamp()))
                .name("Location-Updates");

        DataStream<CdrEvent> cdrStream = env.addSource(new CdrGenerator())
                .assignTimestampsAndWatermarks(WatermarkStrategy.<CdrEvent>forBoundedOutOfOrderness(Duration.ofSeconds(5))
                        .withTimestampAssigner((event, timestamp) -> event.getTimestamp()))
                .name("CDR-Events");

        // 3. Connect to Detectors
        
        // Scenario 1: Velocity / SIM Clone
        DataStream<FraudAlert> velocityAlerts = VelocityFraudDetector.detect(locationStream);
        
        // Scenario 3: Sequential Dialing
        DataStream<FraudAlert> sequentialDialingAlerts = SequentialDialingDetector.detect(cdrStream);
        
        // Scenario 7: Statical Rule using SQL
        DataStream<FraudAlert> staticalRuleAlerts = StaticalRuleSqlDetector.detect(tEnv, cdrStream);
        
        // Scenario 8: Call Forwarding Distance
        DataStream<FraudAlert> callForwardingAlerts = CallForwardingDetector.detect(cdrStream);

        // 4. Sink: Send alerts to SSE Simulation Server
        velocityAlerts.addSink(new AlertSink()).name("VelocityAlert-Sink");
        sequentialDialingAlerts.addSink(new AlertSink()).name("SequentialAlert-Sink");
        staticalRuleAlerts.addSink(new AlertSink()).name("StaticalRuleAlert-Sink");
        callForwardingAlerts.addSink(new AlertSink()).name("CallForwardingAlert-Sink");

        // 5. Execute Job
        System.out.println("Starting Vodafone Fraud Detection PoC with Apache Flink...");
        SimulationServer.startServer();
        env.execute("Vodafone Fraud Detection PoC");
    }
}
