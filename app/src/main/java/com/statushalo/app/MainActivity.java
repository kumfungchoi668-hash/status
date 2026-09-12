package com.statushalo.app;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Space;
import android.widget.Switch;
import android.widget.TextView;

public final class MainActivity extends Activity {
    private SharedPreferences prefs;
    private LinearLayout root;
    private TextView permissionState;
    private HaloView preview;
    private TextView previewMeta;

    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Prefs.ensureDefaults(this);
        prefs = Prefs.get(this);
        buildUi();
    }

    @Override protected void onResume() {
        super.onResume();
        refreshPermissionState();
        refreshPreview();
        if (Settings.canDrawOverlays(this)) {
            startService(new Intent(this, StatusOverlayService.class));
        }
    }

    private void buildUi() {
        getWindow().setStatusBarColor(Color.rgb(246, 246, 246));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        ScrollView scroll = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(14), dp(20), dp(32));
        root.setBackgroundColor(Color.rgb(246, 246, 246));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));
        setContentView(scroll);

        root.addView(text("Status Halo", 30, true, Color.rgb(18,18,18)));
        TextView subtitle = text("三星状态栏极简网络 · 信号 · 电量指示器", 14, false, Color.rgb(96,96,96));
        subtitle.setPadding(0, dp(2), 0, dp(16));
        root.addView(subtitle);

        preview = new HaloView(this);
        LinearLayout previewCard = card();
        previewCard.setGravity(Gravity.CENTER);
        previewCard.setPadding(dp(18), dp(16), dp(18), dp(12));
        previewCard.addView(preview, new LinearLayout.LayoutParams(dp(178), dp(70)));
        previewMeta = text("预览", 12, false, Color.rgb(120,120,120));
        previewMeta.setGravity(Gravity.CENTER);
        previewMeta.setPadding(0, dp(4), 0, 0);
        previewCard.addView(previewMeta);
        root.addView(previewCard);

        addGap(14);
        LinearLayout permissionCard = card();
        permissionCard.addView(sectionTitle("显示权限"));
        permissionState = text("", 14, true, Color.rgb(30,30,30));
        permissionState.setPadding(0, dp(8), 0, dp(8));
        permissionCard.addView(permissionState);
        Button overlay = button("允许显示在其他应用上层");
        overlay.setOnClickListener(v -> {
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(i);
        });
        permissionCard.addView(overlay);
        TextView note = text("这一版不再使用无障碍服务。系统只需要“显示在其他应用上层”权限来绘制 Halo；应用不读取屏幕内容、不控制界面。", 12, false, Color.rgb(100,100,100));
        note.setPadding(0, dp(8), 0, 0);
        permissionCard.addView(note);
        root.addView(permissionCard);

        addGap(14);
        LinearLayout main = card();
        main.addView(sectionTitle("显示"));
        addSwitch(main, "显示 Status Halo", Prefs.ENABLED, true);
        addSwitch(main, "显示实时网速", Prefs.SHOW_SPEED, false);
        addSwitch(main, "显示 4G / 5G 标签", Prefs.SHOW_RADIO, false);
        addSwitch(main, "OLED 微位移保护", Prefs.OLED_SHIFT, true);
        addSwitch(main, "半透明底板（复杂背景更清楚）", Prefs.SHOW_BACKGROUND, false);
        root.addView(main);

        addGap(14);
        LinearLayout sizing = card();
        sizing.addView(sectionTitle("位置与尺寸"));
        addSeek(sizing, "组件尺寸", Prefs.SIZE, 32, 64, "dp");
        addSeek(sizing, "右侧边距", Prefs.OFFSET_X, 0, 48, "dp");
        addSeek(sizing, "顶部微调", Prefs.OFFSET_Y, -8, 20, "dp");
        addSeek(sizing, "线条粗细", Prefs.THICKNESS, 1, 6, "dp");
        addSeek(sizing, "透明度", Prefs.OPACITY, 30, 100, "%");
        root.addView(sizing);

        addGap(14);
        LinearLayout color = card();
        color.addView(sectionTitle("颜色"));
        RadioGroup group = new RadioGroup(this);
        group.setOrientation(RadioGroup.VERTICAL);
        String[] labels = {"自动（跟随系统深浅色）", "始终白色", "始终黑色"};
        for (int i = 0; i < labels.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setText(labels[i]);
            rb.setTextSize(14);
            rb.setId(100 + i);
            group.addView(rb);
        }
        group.check(100 + prefs.getInt(Prefs.COLOR_MODE, 0));
        group.setOnCheckedChangeListener((g, id) -> {
            prefs.edit().putInt(Prefs.COLOR_MODE, id - 100).apply();
            refreshPreview();
        });
        color.addView(group);
        root.addView(color);

        addGap(14);
        LinearLayout privacy = card();
        privacy.addView(sectionTitle("隐私"));
        privacy.addView(text("无 INTERNET、定位、电话状态、存储、通讯录、麦克风、摄像头、短信和通话记录权限。状态信息只在本机用于绘制。", 13, false, Color.rgb(80,80,80)));
        TextView limitation = text("注意：因为不再使用无障碍 Overlay，少数全屏应用或特殊系统界面可能覆盖 Halo；三星桌面和大多数普通应用可正常显示。", 13, false, Color.rgb(80,80,80));
        limitation.setPadding(0, dp(10), 0, 0);
        privacy.addView(limitation);
        root.addView(privacy);
    }

    private void refreshPermissionState() {
        boolean granted = Settings.canDrawOverlays(this);
        permissionState.setText(granted ? "● 悬浮显示权限已启用" : "○ 需要允许显示在其他应用上层");
        permissionState.setTextColor(granted ? Color.rgb(28, 125, 72) : Color.rgb(184, 70, 40));
    }

    private void refreshPreview() {
        if (preview == null) return;
        StatusSnapshot demo = new StatusSnapshot();
        demo.wifiConnected = true;
        demo.wifiLevel = 3;
        demo.mobileLevel = 4;
        demo.batteryPercent = 72;
        demo.charging = false;
        demo.bytesPerSecond = 1380 * 1024L;
        demo.radioLabel = "5G";
        preview.setSnapshot(demo);
        preview.applySettings(
                prefs.getInt(Prefs.COLOR_MODE, 0),
                prefs.getInt(Prefs.OPACITY, 100),
                prefs.getInt(Prefs.THICKNESS, 3),
                prefs.getBoolean(Prefs.SHOW_SPEED, false),
                prefs.getBoolean(Prefs.SHOW_RADIO, false),
                prefs.getBoolean(Prefs.SHOW_BACKGROUND, false));
        if (previewMeta != null) {
            previewMeta.setText(prefs.getInt(Prefs.SIZE, 44) + "dp · 右 " +
                    prefs.getInt(Prefs.OFFSET_X, 8) + "dp · 顶 " +
                    prefs.getInt(Prefs.OFFSET_Y, 0) + "dp");
        }
    }

    private LinearLayout card() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(16), dp(14), dp(16), dp(14));
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(20));
        bg.setStroke(dp(1), Color.rgb(232,232,232));
        l.setBackground(bg);
        l.setElevation(dp(1));
        l.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        return l;
    }

    private TextView sectionTitle(String s) { return text(s, 17, true, Color.rgb(20,20,20)); }

    private TextView text(String s, int sp, boolean bold, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        t.setLineSpacing(0, 1.08f);
        return t;
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(14);
        b.setAllCaps(false);
        return b;
    }

    private void addSwitch(LinearLayout parent, String label, String key, boolean def) {
        Switch sw = new Switch(this);
        sw.setText(label);
        sw.setTextSize(14);
        sw.setChecked(prefs.getBoolean(key, def));
        sw.setPadding(0, dp(4), 0, dp(4));
        sw.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(key, isChecked).apply();
            refreshPreview();
            if (Settings.canDrawOverlays(this) && StatusOverlayService.instance == null) {
                startService(new Intent(this, StatusOverlayService.class));
            }
        });
        parent.addView(sw, new LinearLayout.LayoutParams(-1, -2));
    }

    private void addSeek(LinearLayout parent, String label, String key, int min, int max, String suffix) {
        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setPadding(0, dp(8), 0, 0);
        TextView name = text(label, 13, false, Color.rgb(65,65,65));
        TextView value = text("", 13, true, Color.rgb(40,40,40));
        value.setGravity(Gravity.END);
        line.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        line.addView(value, new LinearLayout.LayoutParams(dp(78), -2));
        parent.addView(line);

        SeekBar seek = new SeekBar(this);
        seek.setMax(max - min);
        int current = Math.max(min, Math.min(max, prefs.getInt(key, min)));
        seek.setProgress(current - min);
        value.setText(current + suffix);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int v = min + progress;
                value.setText(v + suffix);
                if (fromUser) {
                    prefs.edit().putInt(key, v).apply();
                    refreshPreview();
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        parent.addView(seek, new LinearLayout.LayoutParams(-1, -2));
    }

    private void addGap(int valueDp) {
        Space s = new Space(this);
        root.addView(s, new LinearLayout.LayoutParams(1, dp(valueDp)));
    }
}
