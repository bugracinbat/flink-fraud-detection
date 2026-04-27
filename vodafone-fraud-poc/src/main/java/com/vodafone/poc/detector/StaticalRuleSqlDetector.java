package com.vodafone.poc.detector;

import com.vodafone.poc.event.CdrEvent;
import com.vodafone.poc.event.FraudAlert;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.table.api.Schema;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;
import org.apache.flink.types.Row;

public class StaticalRuleSqlDetector {

    public static DataStream<FraudAlert> detect(StreamTableEnvironment tEnv, DataStream<CdrEvent> cdrStream) {
        
        // Define Schema with explicit event time attribute for Flink SQL windowing
        Schema schema = Schema.newBuilder()
                .column("msisdn", "STRING")
                .column("callee", "STRING")
                .column("timestamp", "BIGINT")
                .column("dataUsageMb", "DOUBLE")
                .column("simAgeDays", "INT")
                .column("runId", "STRING")
                .columnByExpression("ts", "TO_TIMESTAMP_LTZ(`timestamp`, 3)")
                .watermark("ts", "ts - INTERVAL '5' SECOND")
                .build();

        // Create temporary view
        tEnv.createTemporaryView("cdrs", cdrStream, schema);

        // Scenario 7: SIM card purchased recently (<=3 days), used < 5 MB internet, 
        // and made calls to 10 or more different numbers.
        // We use a TUMBLE window to group calls by day.
        String sql = 
                "SELECT " +
                "  msisdn, " +
                "  runId, " +
                "  window_end, " +
                "  COUNT(DISTINCT callee) as distinct_callees " +
                "FROM TABLE(TUMBLE(TABLE cdrs, DESCRIPTOR(ts), INTERVAL '1' DAY)) " +
                "WHERE simAgeDays <= 3 AND dataUsageMb < 5.0 " +
                "GROUP BY msisdn, runId, window_end, window_start " +
                "HAVING COUNT(DISTINCT callee) >= 10";

        Table resultTable = tEnv.sqlQuery(sql);

        // Convert the result Table back to a DataStream of FraudAlert
        DataStream<Row> resultStream = tEnv.toDataStream(resultTable);

        return resultStream.map(row -> {
            String msisdn = (String) row.getField("msisdn");
            String runId = (String) row.getField("runId");
            long distinctCallees = (long) row.getField("distinct_callees");
            String desc = String.format("Static rule violation: SIM <= 3 days old, low data usage, but called %d distinct numbers.", distinctCallees);
            return new FraudAlert(msisdn, "STATICAL_RULE_FRAUD", desc, System.currentTimeMillis(), runId);
        });
    }
}
