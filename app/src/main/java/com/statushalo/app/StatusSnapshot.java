package com.statushalo.app;

public final class StatusSnapshot {
    public int wifiLevel = 0;      // 0..4
    public boolean wifiConnected = false;
    public int mobileLevel = 0;    // 0..4
    public String radioLabel = "";
    public int batteryPercent = 100;
    public boolean charging = false;
    public long bytesPerSecond = 0L;

    public StatusSnapshot copy() {
        StatusSnapshot s = new StatusSnapshot();
        s.wifiLevel = wifiLevel;
        s.wifiConnected = wifiConnected;
        s.mobileLevel = mobileLevel;
        s.radioLabel = radioLabel;
        s.batteryPercent = batteryPercent;
        s.charging = charging;
        s.bytesPerSecond = bytesPerSecond;
        return s;
    }
}
