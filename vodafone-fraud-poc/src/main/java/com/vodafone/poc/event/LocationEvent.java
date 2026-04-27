package com.vodafone.poc.event;

import java.io.Serializable;

public class LocationEvent implements Serializable {
    private String msisdn;
    private String location; // e.g., "Germany", "Turkiye"
    private long timestamp;
    private String runId;

    public LocationEvent() {}

    public LocationEvent(String msisdn, String location, long timestamp) {
        this.msisdn = msisdn;
        this.location = location;
        this.timestamp = timestamp;
    }

    public String getMsisdn() { return msisdn; }
    public void setMsisdn(String msisdn) { this.msisdn = msisdn; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }

    @Override
    public String toString() {
        return "LocationEvent{" +
                "msisdn='" + msisdn + '\'' +
                ", location='" + location + '\'' +
                ", timestamp=" + timestamp +
                ", runId='" + runId + '\'' +
                '}';
    }
}
