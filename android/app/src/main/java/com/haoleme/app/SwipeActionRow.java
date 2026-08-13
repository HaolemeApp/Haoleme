package com.haoleme.app;

import android.content.Context;
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

/** Follow-finger reveal for pin / archive / delete. Rails stay behind the card. */
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
                   int pinBg, int archiveBg, int deleteBg,
                   int pinFg, int archiveFg, int deleteFg, Actions actions) {
        super(context);
        this.card = card;
        float density = context.getResources().getDisplayMetrics().density;
        leftWidth = Math.round(88 * density);
        rightWidth = Math.round(152 * density);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setClipChildren(true);
        setClipToPadding(true);

        leftRail = rail(context);
        leftRail.addView(actionButton(context, pinLabel, pinBg, pinFg, v -> {
            if (actions != null) actions.onPin();
        }), new LinearLayout.LayoutParams(leftWidth - dp(8), ViewGroup.LayoutParams.MATCH_PARENT));
        addView(leftRail, new LayoutParams(leftWidth, LayoutParams.MATCH_PARENT, Gravity.START | Gravity.CENTER_VERTICAL));

        rightRail = rail(context);
        rightRail.setGravity(Gravity.END);
        LinearLayout.LayoutParams archiveParams = new LinearLayout.LayoutParams(dp(72), ViewGroup.LayoutParams.MATCH_PARENT);
        archiveParams.setMargins(0, 0, dp(8), 0);
        rightRail.addView(actionButton(context, archiveLabel, archiveBg, archiveFg, v -> {
            if (actions != null) actions.onArchive();
        }), archiveParams);
        rightRail.addView(actionButton(context, deleteLabel, deleteBg, deleteFg, v -> {
            if (actions != null) actions.onDelete();
        }), new LinearLayout.LayoutParams(dp(72), ViewGroup.LayoutParams.MATCH_PARENT));
        addView(rightRail, new LayoutParams(rightWidth, LayoutParams.MATCH_PARENT, Gravity.END | Gravity.CENTER_VERTICAL));

        FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        addView(card, cardParams);
        card.bringToFront();
        setActionsEnabled(false);
        setLayoutParams(new MarginLayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (!dragging && actionsFullyOpen()) {
            float tx = card.getTranslationX();
            float x = ev.getX();
            if (tx > touchSlop && x < tx) {
                MotionEvent copy = offsetTo(ev, leftRail);
                try {
                    return leftRail.dispatchTouchEvent(copy);
                } finally {
                    copy.recycle();
                }
            }
            if (tx < -touchSlop && x > getWidth() + tx) {
                MotionEvent copy = offsetTo(ev, rightRail);
                try {
                    return rightRail.dispatchTouchEvent(copy);
                } finally {
                    copy.recycle();
                }
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    private MotionEvent offsetTo(MotionEvent ev, View target) {
        MotionEvent copy = MotionEvent.obtain(ev);
        copy.offsetLocation(-target.getLeft(), -target.getTop());
        return copy;
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
                    if (dragging) {
                        setActionsEnabled(false);
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
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
                    if (dragging) {
                        setActionsEnabled(false);
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                }
                if (dragging) {
                    card.setTranslationX(clampTranslation(startTx + dx));
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
                if (!actionsFullyOpen()) settle(0f);
                break;
            default:
                break;
        }
        return dragging || super.onTouchEvent(event);
    }

    private float clampTranslation(float value) {
        if (startTx > 1f) {
            return clamp(value, 0f, leftWidth);
        }
        if (startTx < -1f) {
            return clamp(value, -rightWidth, 0f);
        }
        return clamp(value, -rightWidth, leftWidth);
    }

    private void snap() {
        float x = card.getTranslationX();
        if (startTx > 1f) {
            settle(x > leftWidth * 0.75f ? leftWidth : 0f);
            return;
        }
        if (startTx < -1f) {
            settle(x < -rightWidth * 0.75f ? -rightWidth : 0f);
            return;
        }
        if (x > leftWidth * 0.55f) settle(leftWidth);
        else if (x < -rightWidth * 0.55f) settle(-rightWidth);
        else settle(0f);
    }

    private void settle(float target) {
        card.bringToFront();
        setActionsEnabled(false);
        card.animate()
                .translationX(target)
                .setDuration(180L)
                .setInterpolator(PageMotion.EASE)
                .withEndAction(() -> setActionsEnabled(Math.abs(target) > 1f))
                .start();
    }

    private boolean actionsFullyOpen() {
        float x = card.getTranslationX();
        return x > leftWidth * 0.92f || x < -rightWidth * 0.92f;
    }

    private void setActionsEnabled(boolean enabled) {
        setRailEnabled(leftRail, enabled);
        setRailEnabled(rightRail, enabled);
    }

    private void setRailEnabled(ViewGroup rail, boolean enabled) {
        rail.setEnabled(enabled);
        for (int i = 0; i < rail.getChildCount(); i++) {
            View child = rail.getChildAt(i);
            child.setEnabled(enabled);
            child.setClickable(enabled);
        }
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

    private TextView actionButton(Context context, String label, int bg, int fg, OnClickListener listener) {
        TextView button = new TextView(context);
        button.setText(label);
        button.setTextColor(fg);
        button.setTextSize(13);
        button.setTypeface(null, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(bg);
        drawable.setCornerRadius(dp(16));
        button.setBackground(drawable);
        button.setClickable(true);
        button.setMinHeight(dp(48));
        button.setOnClickListener(listener);
        return button;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
