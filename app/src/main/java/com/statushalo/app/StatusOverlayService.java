package com.statushalo.app;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.WindowManager;

public final class StatusOverlayService extends Service implements
        SharedPreferences.OnSharedPreferenceChangeListener, StatusRepository.Listener {

    public static volatile StatusOverlayService instance;
    private WindowManager wm;
    private WindowManager.LayoutParams lp;
    private HaloView halo;
    private StatusRepository repository;
    private SharedPreferences prefs;
    private BroadcastReceiver screenReceiver;
    private boolean screenOn = true;
    private int oledNudge = 0;

    @Override public void onCreate() {
        super.onCreate();
        instance = this;
        Prefs.ensureDefaults(this);
        prefs = Prefs.get(this);
        prefs.registerOnSharedPreferenceChangeListener(this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            stopSelf();
            return;
        }

        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        createOverlay();

        repository = new StatusRepository(this);
        repository.addListener(this);
        repository.start(prefs.getBoolean(Prefs.SHOW_SPEED, false));

        screenReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) screenOn = false;
                if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) screenOn = true;
                updateVisibility();
            }
        };
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_SCREEN_ON);
        f.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(screenReceiver, f);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        updateVisibility();
        return START_STICKY;
    }

    @Override public IBinder onBind(Intent intent) {
        return null;
    }

    private void createOverlay() {
        if (halo != null || wm == null) return;
        halo = new HaloView(this);
        lp = new WindowManager.LayoutParams();
        lp.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        lp.format = PixelFormat.TRANSLUCENT;
        lp.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS |
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        lp.gravity = Gravity.TOP | Gravity.END;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        }
        applyLayout();
        applyAppearance();
        try { wm.addView(halo, lp); } catch (Exception ignored) {}
        updateVisibility();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void applyLayout() {
        if (lp == null || prefs == null) return;
        int size = prefs.getInt(Prefs.SIZE, 44);
        boolean sideText = prefs.getBoolean(Prefs.SHOW_SPEED, false) || prefs.getBoolean(Prefs.SHOW_RADIO, false);
        lp.height = dp(size);
        lp.width = dp(sideText ? Math.round(size * 1.85f) : Math.round(size * 1.14f));
        int x = prefs.getInt(Prefs.OFFSET_X, 8);
        int y = prefs.getInt(Prefs.OFFSET_Y, 0);
        if (prefs.getBoolean(Prefs.OLED_SHIFT, true)) {
            oledNudge = (int) (System.currentTimeMillis() / 60000L) % 3 - 1;
        } else {
            oledNudge = 0;
        }
        lp.x = dp(Math.max(0, x + oledNudge));
        lp.y = dp(y);
        if (halo != null && halo.isAttachedToWindow()) {
            try { wm.updateViewLayout(halo, lp); } catch (Exception ignored) {}
        }
    }

    private void applyAppearance() {
        if (halo == null || prefs == null) return;
        halo.applySettings(
                prefs.getInt(Prefs.COLOR_MODE, 0),
                prefs.getInt(Prefs.OPACITY, 100),
                prefs.getInt(Prefs.THICKNESS, 3),
                prefs.getBoolean(Prefs.SHOW_SPEED, false),
                prefs.getBoolean(Prefs.SHOW_RADIO, false),
                prefs.getBoolean(Prefs.SHOW_BACKGROUND, false));
    }

    private void updateVisibility() {
        if (halo == null || prefs == null) return;
        boolean visible = prefs.getBoolean(Prefs.ENABLED, true) && screenOn;
        halo.setVisibility(visible ? android.view.View.VISIBLE : android.view.View.GONE);
    }

    public void setOverlayEnabled(boolean enabled) {
        if (prefs != null) prefs.edit().putBoolean(Prefs.ENABLED, enabled).apply();
    }

    public boolean isOverlayEnabled() {
        return prefs != null && prefs.getBoolean(Prefs.ENABLED, true);
    }

    @Override public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (Prefs.SHOW_SPEED.equals(key) && repository != null) {
            repository.setSpeedEnabled(sharedPreferences.getBoolean(Prefs.SHOW_SPEED, false));
        }
        applyLayout();
        applyAppearance();
        updateVisibility();
    }

    @Override public void onStatus(StatusSnapshot snapshot) {
        if (halo != null) halo.setSnapshot(snapshot);
    }

    @Override public void onDestroy() {
        instance = null;
        if (prefs != null) prefs.unregisterOnSharedPreferenceChangeListener(this);
        if (repository != null) repository.stop();
        if (screenReceiver != null) {
            try { unregisterReceiver(screenReceiver); } catch (Exception ignored) {}
        }
        if (wm != null && halo != null) {
            try { wm.removeView(halo); } catch (Exception ignored) {}
        }
        halo = null;
        super.onDestroy();
    }
}
