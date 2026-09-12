package com.statushalo.app;

import android.content.Context;
import android.content.SharedPreferences;

public final class Prefs {
    public static final String NAME = "status_halo_prefs";
    public static final String ENABLED = "enabled";
    public static final String SIZE = "size";
    public static final String OFFSET_X = "offset_x";
    public static final String OFFSET_Y = "offset_y";
    public static final String OPACITY = "opacity";
    public static final String THICKNESS = "thickness";
    public static final String COLOR_MODE = "color_mode"; // 0 auto, 1 white, 2 black
    public static final String SHOW_SPEED = "show_speed";
    public static final String SHOW_RADIO = "show_radio";
    public static final String OLED_SHIFT = "oled_shift";
    public static final String SHOW_BACKGROUND = "show_background";

    private Prefs() {}

    public static SharedPreferences get(Context c) {
        return c.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    public static void ensureDefaults(Context c) {
        SharedPreferences p = get(c);
        if (!p.contains(ENABLED)) {
            p.edit()
                    .putBoolean(ENABLED, true)
                    .putInt(SIZE, 44)
                    .putInt(OFFSET_X, 8)
                    .putInt(OFFSET_Y, 0)
                    .putInt(OPACITY, 100)
                    .putInt(THICKNESS, 3)
                    .putInt(COLOR_MODE, 0)
                    .putBoolean(SHOW_SPEED, false)
                    .putBoolean(SHOW_RADIO, false)
                    .putBoolean(OLED_SHIFT, true)
                    .putBoolean(SHOW_BACKGROUND, false)
                    .apply();
        }
    }
}
