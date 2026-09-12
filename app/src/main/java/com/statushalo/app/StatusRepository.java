package com.statushalo.app;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.TrafficStats;
import android.net.wifi.WifiInfo;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyDisplayInfo;
import android.telephony.TelephonyManager;

import java.util.concurrent.CopyOnWriteArrayList;

public final class StatusRepository {
    public interface Listener { void onStatus(StatusSnapshot snapshot); }

    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private final StatusSnapshot snapshot = new StatusSnapshot();

    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;
    private TelephonyManager telephonyManager;
    private PhoneStateListener phoneStateListener;
    private BroadcastReceiver batteryReceiver;
    private long lastBytes;
    private long lastBytesAt;
    private boolean speedEnabled;

    private final Runnable speedTick = new Runnable() {
        @Override public void run() {
            if (!speedEnabled) return;
            long now = System.currentTimeMillis();
            long bytes = TrafficStats.getTotalRxBytes() + TrafficStats.getTotalTxBytes();
            if (lastBytesAt != 0 && bytes >= lastBytes) {
                long dt = Math.max(1L, now - lastBytesAt);
                snapshot.bytesPerSecond = (bytes - lastBytes) * 1000L / dt;
                publish();
            }
            lastBytes = bytes;
            lastBytesAt = now;
            main.postDelayed(this, 1000L);
        }
    };

    public StatusRepository(Context context) {
        this.context = context.getApplicationContext();
    }

    public void addListener(Listener l) {
        listeners.addIfAbsent(l);
        l.onStatus(snapshot.copy());
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    public void start(boolean showSpeed) {
        this.speedEnabled = showSpeed;
        startBattery();
        startNetwork();
        startTelephony();
        if (showSpeed) startSpeed();
    }

    public void setSpeedEnabled(boolean enabled) {
        if (speedEnabled == enabled) return;
        speedEnabled = enabled;
        main.removeCallbacks(speedTick);
        snapshot.bytesPerSecond = 0L;
        if (enabled) startSpeed();
        publish();
    }

    private void startSpeed() {
        lastBytes = TrafficStats.getTotalRxBytes() + TrafficStats.getTotalTxBytes();
        lastBytesAt = System.currentTimeMillis();
        main.postDelayed(speedTick, 1000L);
    }

    private void startBattery() {
        batteryReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                int level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                int status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                if (level >= 0 && scale > 0) snapshot.batteryPercent = Math.round(level * 100f / scale);
                snapshot.charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL;
                publish();
            }
        };
        context.registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    }

    private void startNetwork() {
        connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkRequest req = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override public void onAvailable(Network network) { updateNetwork(network); }
            @Override public void onCapabilitiesChanged(Network network, NetworkCapabilities caps) { updateCapabilities(caps); }
            @Override public void onLost(Network network) { refreshActiveNetwork(); }
        };
        try { connectivityManager.registerNetworkCallback(req, networkCallback); } catch (Exception ignored) {}
        refreshActiveNetwork();
    }

    private void refreshActiveNetwork() {
        Network n = connectivityManager.getActiveNetwork();
        if (n == null) {
            snapshot.wifiConnected = false;
            snapshot.wifiLevel = 0;
            publish();
            return;
        }
        updateNetwork(n);
    }

    private void updateNetwork(Network n) {
        NetworkCapabilities caps = connectivityManager.getNetworkCapabilities(n);
        updateCapabilities(caps);
    }

    private void updateCapabilities(NetworkCapabilities caps) {
        boolean wifi = caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
        snapshot.wifiConnected = wifi;
        int level = 0;
        if (wifi && caps != null) {
            Object ti = caps.getTransportInfo();
            if (ti instanceof WifiInfo) {
                int rssi = ((WifiInfo) ti).getRssi();
                level = rssiToLevel(rssi);
            } else if (android.os.Build.VERSION.SDK_INT >= 29) {
                int signal = caps.getSignalStrength();
                if (signal != Integer.MIN_VALUE) level = rssiToLevel(signal);
            }
        }
        snapshot.wifiLevel = level;
        publish();
    }

    private static int rssiToLevel(int rssi) {
        if (rssi >= -50) return 4;
        if (rssi >= -60) return 3;
        if (rssi >= -70) return 2;
        if (rssi >= -80) return 1;
        return 0;
    }

    @SuppressWarnings("deprecation")
    private void startTelephony() {
        telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        if (telephonyManager == null) return;
        if (context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            snapshot.mobileLevel = 0;
            snapshot.radioLabel = "";
            publish();
            return;
        }
        phoneStateListener = new PhoneStateListener() {
            @Override public void onSignalStrengthsChanged(SignalStrength signalStrength) {
                super.onSignalStrengthsChanged(signalStrength);
                try { snapshot.mobileLevel = Math.max(0, Math.min(4, signalStrength.getLevel())); }
                catch (Exception e) { snapshot.mobileLevel = 0; }
                publish();
            }

            @Override public void onDisplayInfoChanged(TelephonyDisplayInfo info) {
                super.onDisplayInfoChanged(info);
                snapshot.radioLabel = radioLabel(info);
                publish();
            }
        };
        try {
            telephonyManager.listen(phoneStateListener,
                    PhoneStateListener.LISTEN_SIGNAL_STRENGTHS | PhoneStateListener.LISTEN_DISPLAY_INFO_CHANGED);
        } catch (Exception ignored) {}
    }

    private static String radioLabel(TelephonyDisplayInfo info) {
        int override = info.getOverrideNetworkType();
        if (override == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA ||
                override == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED) return "5G";
        switch (info.getNetworkType()) {
            case TelephonyManager.NETWORK_TYPE_NR: return "5G";
            case TelephonyManager.NETWORK_TYPE_LTE: return "4G";
            case TelephonyManager.NETWORK_TYPE_HSPAP:
            case TelephonyManager.NETWORK_TYPE_HSPA:
            case TelephonyManager.NETWORK_TYPE_UMTS: return "3G";
            default: return "";
        }
    }

    private void publish() {
        StatusSnapshot copy = snapshot.copy();
        for (Listener l : listeners) l.onStatus(copy);
    }

    public void stop() {
        main.removeCallbacks(speedTick);
        if (connectivityManager != null && networkCallback != null) {
            try { connectivityManager.unregisterNetworkCallback(networkCallback); } catch (Exception ignored) {}
        }
        if (batteryReceiver != null) {
            try { context.unregisterReceiver(batteryReceiver); } catch (Exception ignored) {}
        }
        if (telephonyManager != null && phoneStateListener != null) {
            try { telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE); } catch (Exception ignored) {}
        }
    }
}
