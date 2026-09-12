package com.statushalo.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;
import android.view.animation.LinearInterpolator;

import java.util.Locale;

public final class HaloView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private StatusSnapshot s = new StatusSnapshot();
    private float chargePhase = 0f;
    private ValueAnimator chargeAnimator;

    private int colorMode = 0;
    private float opacity = 1f;
    private float strokeDp = 3f;
    private boolean showSpeed;
    private boolean showRadio;
    private boolean showBackground;

    public HaloView(Context context) {
        super(context);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        text.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    public void setSnapshot(StatusSnapshot snapshot) {
        boolean chargingChanged = s.charging != snapshot.charging;
        s = snapshot;
        if (chargingChanged) updateChargeAnimation();
        invalidate();
    }

    public void applySettings(int colorMode, int opacityPercent, int strokeDp,
                              boolean showSpeed, boolean showRadio, boolean showBackground) {
        this.colorMode = colorMode;
        this.opacity = Math.max(0.2f, Math.min(1f, opacityPercent / 100f));
        this.strokeDp = Math.max(1f, strokeDp);
        this.showSpeed = showSpeed;
        this.showRadio = showRadio;
        this.showBackground = showBackground;
        invalidate();
    }

    private void updateChargeAnimation() {
        if (chargeAnimator != null) chargeAnimator.cancel();
        if (!s.charging) {
            chargePhase = 0f;
            return;
        }
        chargeAnimator = ValueAnimator.ofFloat(0f, 1f);
        chargeAnimator.setDuration(1300);
        chargeAnimator.setRepeatCount(ValueAnimator.INFINITE);
        chargeAnimator.setInterpolator(new LinearInterpolator());
        chargeAnimator.addUpdateListener(a -> {
            chargePhase = (float) a.getAnimatedValue();
            invalidate();
        });
        chargeAnimator.start();
    }

    @Override protected void onDetachedFromWindow() {
        if (chargeAnimator != null) chargeAnimator.cancel();
        super.onDetachedFromWindow();
    }

    private int foreground() {
        boolean dark;
        if (colorMode == 1) dark = false;
        else if (colorMode == 2) dark = true;
        else dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                != Configuration.UI_MODE_NIGHT_YES;
        int base = dark ? Color.BLACK : Color.WHITE;
        return withAlpha(base, opacity);
    }

    private static int withAlpha(int color, float alpha) {
        return Color.argb(Math.round(255f * alpha), Color.red(color), Color.green(color), Color.blue(color));
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        float w = getWidth(), h = getHeight();
        if (w <= 1 || h <= 1) return;

        int fg = foreground();
        float d = getResources().getDisplayMetrics().density;
        float stroke = strokeDp * d;

        if (showBackground) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(withAlpha(Color.BLACK, 0.24f * opacity));
            p.setShadowLayer(3f * d, 0f, 1f * d, withAlpha(Color.BLACK, 0.20f));
            float r = Math.min(h * 0.42f, 18f * d);
            c.drawRoundRect(0, 0, w, h, r, r, p);
            p.clearShadowLayer();
        }

        float textReserve = (showSpeed || showRadio) ? Math.min(w * 0.34f, 46f * d) : 0f;
        float size = Math.min(h * 0.92f, w - textReserve);
        float cx = w - size * 0.52f;
        float cy = h * 0.48f;
        float radius = size * 0.36f;

        // Mobile signal: four separated arc segments. The inactive arcs remain faint,
        // which keeps the halo legible instead of making the indicator jump visually.
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(stroke);
        RectF oval = new RectF(cx - radius, cy - radius, cx + radius, cy + radius);
        float start = 145f;
        float segSweep = 42f;
        float gap = 9f;
        for (int i = 0; i < 4; i++) {
            boolean active = i < Math.max(0, Math.min(4, s.mobileLevel));
            p.setColor(active ? fg : withAlpha(fg, 0.22f));
            c.drawArc(oval, start + i * (segSweep + gap), segSweep, false, p);
        }

        // Wi-Fi glyph in the center. Offline becomes a small outlined dot rather than
        // disappearing completely, so users can still see that Halo itself is alive.
        float wcx = cx;
        float wcy = cy + size * 0.02f;
        float maxR = size * 0.19f;
        for (int i = 0; i < 3; i++) {
            float rr = maxR * (1f - i * 0.24f);
            int required = 3 - i;
            boolean active = s.wifiConnected && s.wifiLevel >= required;
            p.setColor(active ? fg : withAlpha(fg, 0.20f));
            p.setStrokeWidth(Math.max(1.2f * d, stroke * 0.72f));
            RectF a = new RectF(wcx - rr, wcy - rr * 0.58f, wcx + rr, wcy + rr * 1.42f);
            c.drawArc(a, 218f, 104f, false, p);
        }
        p.setStyle(Paint.Style.FILL);
        p.setColor(s.wifiConnected ? fg : withAlpha(fg, 0.28f));
        c.drawCircle(wcx, wcy + size * 0.13f, Math.max(1.8f * d, size * 0.035f), p);

        // Four battery dots. Charging uses a subtle traveling emphasis instead of
        // a constantly spinning animation, reducing distraction and OLED burn-in.
        int filled = Math.max(0, Math.min(4, (int) Math.ceil(s.batteryPercent / 25.0)));
        float dotsY = cy + radius * 0.87f;
        float dotR = Math.max(2.1f * d, size * 0.043f);
        float spacing = size * 0.135f;
        for (int i = 0; i < 4; i++) {
            float x = cx + (i - 1.5f) * spacing;
            float a = i < filled ? 1f : 0.20f;
            if (s.charging) {
                float pulse = 1f - Math.min(1f, Math.abs(chargePhase * 4f - i));
                a = Math.max(a, 0.32f + pulse * 0.68f);
            }
            p.setColor(withAlpha(fg, a));
            c.drawCircle(x, dotsY, dotR, p);
        }

        if (showSpeed || showRadio) {
            text.setColor(fg);
            text.setTextAlign(Paint.Align.RIGHT);
            text.setTextSize(Math.max(8f * d, h * 0.22f));
            float tx = cx - radius - 5f * d;
            float ty = h * 0.50f;
            if (showSpeed) {
                c.drawText(formatSpeed(s.bytesPerSecond), tx, ty, text);
                ty += text.getTextSize() * 1.05f;
            }
            if (showRadio && s.radioLabel != null && !s.radioLabel.isEmpty()) {
                text.setAlpha(Math.round(190 * opacity));
                c.drawText(s.radioLabel, tx, ty, text);
                text.setAlpha(255);
            }
        }
    }

    private static String formatSpeed(long bps) {
        if (bps < 1024) return bps + "B/s";
        if (bps < 1024L * 1024L) return String.format(Locale.US, "%.0fK/s", bps / 1024f);
        return String.format(Locale.US, "%.1fM/s", bps / (1024f * 1024f));
    }
}
