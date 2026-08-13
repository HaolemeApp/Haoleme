package com.haoleme.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/** Capsule utilization bar. Replaces stock ProgressBar chrome. */
final class MeterBarView extends View {
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF trackRect = new RectF();
    private final RectF fillRect = new RectF();
    private float fraction = 0f;
    private boolean gradient;
    private int gradientStart;
    private int gradientMiddle;
    private int gradientEnd;
    private float gradientWidth = -1f;

    MeterBarView(Context context) {
        super(context);
        trackPaint.setStyle(Paint.Style.FILL);
        fillPaint.setStyle(Paint.Style.FILL);
    }

    void setMeter(int percent, int fillColor, int trackColor) {
        fraction = percent < 0 ? 0f : Math.max(0f, Math.min(1f, percent / 100f));
        gradient = false;
        gradientWidth = -1f;
        fillPaint.setShader(null);
        fillPaint.setColor(fillColor);
        trackPaint.setColor(trackColor);
        invalidate();
    }

    void setGradientMeter(int percent, int trackColor, int startColor, int middleColor, int endColor) {
        fraction = percent < 0 ? 0f : Math.max(0f, Math.min(1f, percent / 100f));
        boolean colorsChanged = gradientStart != startColor
                || gradientMiddle != middleColor
                || gradientEnd != endColor;
        gradient = true;
        gradientStart = startColor;
        gradientMiddle = middleColor;
        gradientEnd = endColor;
        if (colorsChanged) {
            gradientWidth = -1f;
            fillPaint.setShader(null);
        }
        trackPaint.setColor(trackColor);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float h = getHeight();
        float w = getWidth();
        if (h <= 0 || w <= 0) return;
        float radius = h / 2f;
        trackRect.set(0, 0, w, h);
        canvas.drawRoundRect(trackRect, radius, radius, trackPaint);
        if (fraction <= 0f) return;
        float minFill = h;
        float fillW = Math.max(minFill, w * fraction);
        fillRect.set(0, 0, Math.min(w, fillW), h);
        if (gradient && (fillPaint.getShader() == null || gradientWidth != w)) {
            gradientWidth = w;
            fillPaint.setShader(new LinearGradient(
                    0f,
                    0f,
                    w,
                    0f,
                    new int[]{gradientStart, gradientMiddle, gradientEnd},
                    new float[]{0f, 0.68f, 1f},
                    Shader.TileMode.CLAMP));
        }
        canvas.drawRoundRect(fillRect, radius, radius, fillPaint);
    }
}
