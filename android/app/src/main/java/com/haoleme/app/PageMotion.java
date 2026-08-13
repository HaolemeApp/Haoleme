package com.haoleme.app;

import android.view.View;
import android.view.ViewPropertyAnimator;
import android.view.animation.DecelerateInterpolator;

final class PageMotion {
    static final long TAB_MS = 220L;
    static final long PUSH_MS = 260L;
    static final long POP_MS = 200L;
    static final long DIALOG_IN_MS = 180L;
    static final long DIALOG_OUT_MS = 140L;
    static final float DIALOG_DIM = 0.45f;
    static final DecelerateInterpolator EASE = new DecelerateInterpolator(1.6f);

    private PageMotion() {
    }

    static void cancel(View view) {
        if (view != null) view.animate().cancel();
    }

    static void crossfade(View from, View to, Runnable after) {
        if (to == null) {
            if (after != null) after.run();
            return;
        }
        cancel(from);
        cancel(to);
        to.setVisibility(View.VISIBLE);
        float startAlpha = to.getAlpha() < 0.05f ? 0f : to.getAlpha();
        to.setAlpha(startAlpha);
        if (to.getTranslationY() == 0f) {
            to.setTranslationY(dp(to, 8));
        }
        to.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(TAB_MS)
                .setInterpolator(EASE)
                .withEndAction(null)
                .start();
        if (from == null || from == to) {
            if (after != null) after.run();
            return;
        }
        from.setVisibility(View.VISIBLE);
        from.animate()
                .alpha(0f)
                .translationY(-dp(from, 6))
                .setDuration(TAB_MS)
                .setInterpolator(EASE)
                .withEndAction(() -> {
                    from.setVisibility(View.GONE);
                    from.setAlpha(1f);
                    from.setTranslationY(0f);
                    if (after != null) after.run();
                })
                .start();
    }

    static void pushOverlay(View view) {
        if (view == null) return;
        cancel(view);
        view.setVisibility(View.VISIBLE);
        view.setAlpha(0f);
        view.setTranslationY(dp(view, 12));
        view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(PUSH_MS)
                .setInterpolator(EASE)
                .start();
    }

    static void popOverlay(View view, Runnable after) {
        if (view == null) {
            if (after != null) after.run();
            return;
        }
        cancel(view);
        ViewPropertyAnimator animator = view.animate()
                .alpha(0f)
                .translationY(dp(view, 8))
                .setDuration(POP_MS)
                .setInterpolator(EASE);
        animator.withEndAction(() -> {
            view.setVisibility(View.GONE);
            if (after != null) after.run();
        });
        animator.start();
    }

    private static float dp(View view, int value) {
        return value * view.getResources().getDisplayMetrics().density;
    }
}
