package com.vodafone.poc.event;

import java.io.Serializable;

public class FraudAlert implements Serializable {
    private String msisdn;
    private String fraudType;
    private String description;
    private long timestamp;

    public FraudAlert() {}

    public FraudAlert(String msisdn, String fraudType, String description, long timestamp) {
        this.msisdn = msisdn;
        this.fraudType = fraudType;
        this.description = description;
        this.timestamp = timestamp;
    }

    public String getMsisdn() { return msisdn; }
    public void setMsisdn(String msisdn) { this.msisdn = msisdn; }

    public String getFraudType() { return fraudType; }
    public void setFraudType(String fraudType) { this.fraudType = fraudType; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    @Override
    public String toString() {
        return "\u001B[31m[FRAUD ALERT] Type: " + fraudType + ", MSISDN: " + msisdn + ", Desc: " + description + "\u001B[0m";
    }
}
