package com.vodafone.poc.event;

import java.io.Serializable;

public class CdrEvent implements Serializable {
    private String msisdn;
    private String callee;
    private long timestamp;
    private long duration;
    private String forwardedTo;
    private String cellSite;
    private String imei;
    private double dataUsageMb;
    private int simAgeDays;

    public CdrEvent() {}

    public CdrEvent(String msisdn, String callee, long timestamp, long duration, String forwardedTo, String cellSite, String imei, double dataUsageMb, int simAgeDays) {
        this.msisdn = msisdn;
        this.callee = callee;
        this.timestamp = timestamp;
        this.duration = duration;
        this.forwardedTo = forwardedTo;
        this.cellSite = cellSite;
        this.imei = imei;
        this.dataUsageMb = dataUsageMb;
        this.simAgeDays = simAgeDays;
    }

    public String getMsisdn() { return msisdn; }
    public void setMsisdn(String msisdn) { this.msisdn = msisdn; }

    public String getCallee() { return callee; }
    public void setCallee(String callee) { this.callee = callee; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public long getDuration() { return duration; }
    public void setDuration(long duration) { this.duration = duration; }

    public String getForwardedTo() { return forwardedTo; }
    public void setForwardedTo(String forwardedTo) { this.forwardedTo = forwardedTo; }

    public String getCellSite() { return cellSite; }
    public void setCellSite(String cellSite) { this.cellSite = cellSite; }

    public String getImei() { return imei; }
    public void setImei(String imei) { this.imei = imei; }

    public double getDataUsageMb() { return dataUsageMb; }
    public void setDataUsageMb(double dataUsageMb) { this.dataUsageMb = dataUsageMb; }

    public int getSimAgeDays() { return simAgeDays; }
    public void setSimAgeDays(int simAgeDays) { this.simAgeDays = simAgeDays; }

    @Override
    public String toString() {
        return "CdrEvent{" +
                "msisdn='" + msisdn + '\'' +
                ", callee='" + callee + '\'' +
                ", timestamp=" + timestamp +
                ", forwardedTo='" + forwardedTo + '\'' +
                '}';
    }
}
