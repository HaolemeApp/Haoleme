package com.haoleme.app;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Follow-finger reveal for pin / archive / delete. Replaces HorizontalScrollView swipe. */
final class SwipeActionRow extends FrameLayout {
    interface Actions {
        void onPin();
        void onArchive();
        void onDelete();
    }

    private final View card;
    private final LinearLayout leftRail;
    private final LinearLayout rightRail;
    private final int leftWidth;
    private final int rightWidth;
    private final int touchSlop;
    private float downX;
    private float downY;
    private float startTx;
    private boolean dragging;
    private boolean decided;

    SwipeActionRow(Context context, View card, String pinLabel, String archiveLabel, String deleteLabel,
                   int pinBg, int archiveBg, int deleteBg, Actions actions) {
        super(context);
        this.card = card;
        float density = context.getResources().getDisplayMetrics().density;
        leftWidth = Math.round(88 * density);
        rightWidth = Math.round(152 * density);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setClipChildren(true);
        setClipToPadding(true);

        leftRail = rail(context);
        leftRail.addView(actionButton(context, pinLabel, pinBg, v -> {
            settle(0f);
            if (actions != null) actions.onPin();
        }), new LinearLayout.LayoutParams(leftWidth - dp(8), ViewGroup.LayoutParams.MATCH_PARENT));
        addView(leftRail, new LayoutParams(leftWidth, LayoutParams.MATCH_PARENT, Gravity.START | Gravity.CENTER_VERTICAL));

        rightRail = rail(context);
        rightRail.setGravity(Gravity.END);
        LinearLayout.LayoutParams archiveParams = new LinearLayout.LayoutParams(dp(72), ViewGroup.LayoutParams.MATCH_PARENT);
        archiveParams.setMargins(0, 0, dp(8), 0);
        rightRail.addView(actionButton(context, archiveLabel, archiveBg, v -> {
            settle(0f);
            if (actions != null) actions.onArchive();
        }), archiveParams);
        rightRail.addView(actionButton(context, deleteLabel, deleteBg, v -> {
            settle(0f);
            if (actions != null) actions.onDelete();
        }), new LinearLayout.LayoutParams(dp(72), ViewGroup.LayoutParams.MATCH_PARENT));
        addView(rightRail, new LayoutParams(rightWidth, LayoutParams.MATCH_PARENT, Gravity.END | Gravity.CENTER_VERTICAL));

        FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        addView(card, cardParams);
        setLayoutParams(new MarginLayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    void bindCardBackground(android.graphics.drawable.Drawable background) {
        card.setBackground(background);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX();
                downY = event.getY();
                startTx = card.getTranslationX();
                dragging = false;
                decided = false;
                PageMotion.cancel(card);
                break;
            case MotionEvent.ACTION_MOVE:
                float dx = event.getX() - downX;
                float dy = event.getY() - downY;
                if (!decided && Math.abs(dx) > touchSlop) {
                    decided = true;
                    dragging = Math.abs(dx) > Math.abs(dy);
                    if (dragging) getParent().requestDisallowInterceptTouchEvent(true);
                }
                return dragging;
            default:
                break;
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                float dx = event.getX() - downX;
                float dy = event.getY() - downY;
                if (!decided && Math.abs(dx) > touchSlop) {
                    decided = true;
                    dragging = Math.abs(dx) > Math.abs(dy);
                    if (dragging) getParent().requestDisallowInterceptTouchEvent(true);
                }
                if (dragging) {
                    float next = clamp(startTx + dx, -rightWidth, leftWidth);
                    card.setTranslationX(next);
                    return true;
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (dragging) {
                    snap();
                    dragging = false;
                    getParent().requestDisallowInterceptTouchEvent(false);
                    return true;
                }
                settle(0f);
                break;
            default:
                break;
        }
        return dragging || super.onTouchEvent(event);
    }

    private void snap() {
        float x = card.getTranslationX();
        if (x > leftWidth * 0.45f) settle(leftWidth);
        else if (x < -rightWidth * 0.45f) settle(-rightWidth);
        else settle(0f);
    }

    private void settle(float target) {
        card.animate().translationX(target).setDuration(180L).setInterpolator(PageMotion.EASE).start();
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private LinearLayout rail(Context context) {
        LinearLayout rail = new LinearLayout(context);
        rail.setOrientation(LinearLayout.HORIZONTAL);
        rail.setGravity(Gravity.CENTER_VERTICAL);
        rail.setPadding(0, dp(4), 0, dp(15));
        return rail;
    }

    private TextView actionButton(Context context, String label, int bg, OnClickListener listener) {
        TextView button = new TextView(context);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setTextSize(12);
        button.setTypeface(null, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(bg);
        drawable.setCornerRadius(dp(16));
        button.setBackground(drawable);
        button.setOnClickListener(listener);
        return button;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
