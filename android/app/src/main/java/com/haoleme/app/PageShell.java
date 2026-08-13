package com.haoleme.app;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import java.util.ArrayDeque;

/** Persistent chrome + overlay stack. The activity setContentViews this once. */
final class PageShell {
    static final String TAB_HOME = "home";
    static final String TAB_SETTINGS = "settings";

    final FrameLayout root;
    final LinearLayout chrome;
    final FrameLayout headerHost;
    final FrameLayout statusHost;
    final FrameLayout tabHost;
    final FrameLayout tabBarHost;
    final FrameLayout overlayHost;

    private View homeView;
    private View settingsView;
    private String currentTab = TAB_HOME;
    private boolean attached;
    private final ArrayDeque<View> overlays = new ArrayDeque<>();

    PageShell(Activity activity) {
        root = new FrameLayout(activity);
        root.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        chrome = new LinearLayout(activity);
        chrome.setOrientation(LinearLayout.VERTICAL);
        chrome.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        headerHost = wrapHost(activity);
        statusHost = wrapHost(activity);
        tabHost = new FrameLayout(activity);
        tabHost.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        tabBarHost = wrapHost(activity);

        overlayHost = new FrameLayout(activity);
        overlayHost.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        overlayHost.setClickable(false);

        chrome.addView(headerHost);
        chrome.addView(statusHost);
        chrome.addView(tabHost);
        chrome.addView(tabBarHost);
        root.addView(chrome);
        root.addView(overlayHost);
    }

    void attach(Activity activity) {
        if (attached) return;
        activity.setContentView(root);
        attached = true;
    }

    boolean isAttached() {
        return attached;
    }

    String currentTab() {
        return currentTab;
    }

    View homeView() {
        return homeView;
    }

    View settingsView() {
        return settingsView;
    }

    void clearTabContent() {
        PageMotion.cancel(tabHost);
        tabHost.removeAllViews();
        homeView = null;
        settingsView = null;
    }

    void setHeader(View header) {
        replace(headerHost, header);
    }

    void setStatus(View status) {
        replace(statusHost, status);
    }

    void setTabBar(View bar) {
        replace(tabBarHost, bar);
    }

    void setTabContent(String tab, View content, boolean animate) {
        if (content == null) return;
        boolean settings = TAB_SETTINGS.equals(tab);
        View previous = settings ? settingsView : homeView;
        if (settings) settingsView = content;
        else homeView = content;
        if (content.getParent() == null) {
            content.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            tabHost.addView(content);
        }
        boolean showing = currentTab.equals(tab) || previous == null;
        if (!showing) {
            content.setVisibility(View.GONE);
            if (previous != null && previous != content && previous.getParent() == tabHost) {
                tabHost.removeView(previous);
            }
            return;
        }
        content.setVisibility(View.VISIBLE);
        if (animate && previous != null && previous != content) {
            PageMotion.crossfade(previous, content, () -> {
                if (previous.getParent() == tabHost) tabHost.removeView(previous);
            });
        } else {
            content.setAlpha(1f);
            content.setTranslationY(0f);
            if (previous != null && previous != content && previous.getParent() == tabHost) {
                tabHost.removeView(previous);
            }
        }
    }

    void showTab(String tab, boolean animate) {
        View incoming = TAB_SETTINGS.equals(tab) ? settingsView : homeView;
        View outgoing = TAB_SETTINGS.equals(tab) ? homeView : settingsView;
        if (incoming == null) return;
        if (incoming.getParent() == null) {
            incoming.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            tabHost.addView(incoming);
        }
        boolean same = tab.equals(currentTab);
        currentTab = TAB_SETTINGS.equals(tab) ? TAB_SETTINGS : TAB_HOME;
        if (!animate || outgoing == null || outgoing == incoming || same) {
            incoming.setVisibility(View.VISIBLE);
            incoming.setAlpha(1f);
            incoming.setTranslationY(0f);
            if (outgoing != null && outgoing != incoming) {
                outgoing.setVisibility(View.GONE);
                outgoing.setAlpha(1f);
                outgoing.setTranslationY(0f);
            }
            return;
        }
        PageMotion.crossfade(outgoing, incoming, null);
    }

    void pushOverlay(View view) {
        if (view == null) return;
        view.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        overlayHost.setClickable(true);
        overlayHost.addView(view);
        overlays.addLast(view);
        PageMotion.pushOverlay(view);
    }

    boolean popOverlay() {
        if (overlays.isEmpty()) return false;
        View top = overlays.removeLast();
        PageMotion.popOverlay(top, () -> {
            if (top.getParent() == overlayHost) overlayHost.removeView(top);
            if (overlays.isEmpty()) overlayHost.setClickable(false);
        });
        return true;
    }

    boolean hasOverlay() {
        return !overlays.isEmpty();
    }

    View topOverlay() {
        return overlays.peekLast();
    }

    void clearOverlays() {
        PageMotion.cancel(overlayHost);
        for (View view : overlays) {
            PageMotion.cancel(view);
        }
        overlays.clear();
        overlayHost.removeAllViews();
        overlayHost.setClickable(false);
    }

    private static FrameLayout wrapHost(Activity activity) {
        FrameLayout host = new FrameLayout(activity);
        host.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return host;
    }

    private static void replace(FrameLayout host, View child) {
        host.removeAllViews();
        if (child != null) {
            host.addView(child, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }
}
