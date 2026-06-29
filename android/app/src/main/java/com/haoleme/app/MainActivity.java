package com.haoleme.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.Image;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.util.Base64;
import android.util.Log;
import android.util.Size;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.CheckedTextView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import android.content.DialogInterface;

import android.content.res.ColorStateList;

import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.annotation.OptIn;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.SecretKeySpec;

@OptIn(markerClass = ExperimentalGetImage.class)
public class MainActivity extends Activity implements LifecycleOwner {
    private static final String TAG = "Haoleme";
    private static final String PREFS = "haoleme";
    private static final String CHANNEL_ID = "runs";
    private static final int CAMERA_REQUEST = 4108;
    private static final long POLL_MS = 5000L;
    private static final long LIST_ACTIVE_POLL_MS = 2500L;
    private static final long CONSOLE_RUNNING_POLL_MS = 1000L;
    private static final int HTTP_CONNECT_TIMEOUT_MS = 8000;
    private static final int HTTP_READ_TIMEOUT_MS = 12000;
    private static final int HTTP_LIST_READ_TIMEOUT_MS = 10000;
    private static final String CACHE_RUNS = "cached_runs_json";
    private static final String CACHE_RUNS_AT = "cached_runs_at";
    private static final String CACHE_RUNS_PREFIX = "cached_runs_json_";
    private static final String CACHE_RUNS_AT_PREFIX = "cached_runs_at_";
    private static final String CACHE_DEVICES = "cached_devices_json";
    private static final String CACHE_RUN_PREFIX = "cached_run_";
    private static final String PREF_STATUS_FILTER = "status_filter";
    private static final String PREF_ARCHIVED_RUNS = "archived_run_ids";
    private static final String PREF_PROJECT_FILTER = "project_filter";
    private static final String PREF_THEME_MODE = "theme_mode";
    private static final String PREF_NOTIFY_SUCCESS = "notify_success";
    private static final String PREF_NOTIFY_FAILURE = "notify_failure";
    private static final String PREF_NOTIFY_MIN_SECONDS = "notify_min_seconds";
    private static final String PREF_NOTIFY_QUIET_HOURS = "notify_quiet_hours";
    private static final String PREF_UPDATE_AUTO_CHECK = "update_auto_check";
    private static final String PREF_UPDATE_WIFI_ONLY = "update_wifi_only";
    private static final String PREF_MASK_SENSITIVE = "mask_sensitive";
    private static final String PREF_CONSOLE_HISTORY_CHARS = "console_history_chars";
    private static final String PREF_SHOW_OFFLINE_DEVICES = "show_offline_devices";
    private static final String PREF_LANGUAGE_MODE = "language_mode";
    private static final String PREF_APP_CLIENT_ID = "app_client_id";
    private static final int CONSOLE_RENDER_INITIAL_CHARS = 60000;
    private static final int CONSOLE_RENDER_STEP_CHARS = 60000;
    private static final String THEME_LIGHT = "light";
    private static final String THEME_DARK = "dark";
    private static final String LANG_ZH = "zh";
    private static final String LANG_EN = "en";
    private static final String DEFAULT_SERVER_URL = BuildConfig.HAOLEME_DEFAULT_SERVER_URL;
    private static final String CANONICAL_SERVER_URL = "https://api.haoleme.cloud";
    private static final String DEFAULT_UPDATE_URLS = BuildConfig.HAOLEME_UPDATE_URLS;
    private static final String[] LEGACY_SERVER_URLS = new String[]{
            "http://106.14.246.204",
            "https://106.14.246.204",
            "http://api.haoleme.cloud"
    };

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newFixedThreadPool(3);
    private final ExecutorService updateExecutor = Executors.newSingleThreadExecutor();
    private final Map<String, String> knownStatuses = new HashMap<>();
    private final Map<String, String> deviceNames = new HashMap<>();
    private final Map<String, String> deviceLastSeen = new HashMap<>();
    private final Map<String, String> deviceTokenLastUsed = new HashMap<>();
    private final Map<String, Boolean> deviceOnline = new HashMap<>();

    private SharedPreferences prefs;
    private EditText pairInput;
    private TextView pairButton;
    private TextView updateBadgeButton;
    private TextView connectionSubtitleText;
    private TextView statusText;
    private TextView deviceSummaryText;
    private LinearLayout deviceGpuContainer;
    private int gpuMetricIndex = 0;
    private boolean gpuExpanded = false;
    private static final int GPU_METRIC_COUNT = 3;
    private android.view.GestureDetector gpuGestureDetector;
    private HorizontalScrollView devicesScrollView;
    private LinearLayout devicesContainer;
    private LinearLayout runsContainer;
    private Button renameDeviceButton;
    private Button revokeDeviceButton;
    private Button clearDeviceRunsButton;
    private TextView detailCommand;
    private TextView detailMeta;
    private TextView detailConsole;
    private JSONObject currentRunDetail;
    private TextView consoleAutoScrollButton;
    private TextView consoleInterruptButton;
    private TextView consoleTopMoreButton;
    private EditText consoleSearchInput;
    private ScrollView consoleVerticalScroll;
    private boolean consoleSearchVisible = false;
    private boolean consoleAutoScroll = true;
    private int consoleRenderLimit = CONSOLE_RENDER_INITIAL_CHARS;
    private final long notificationSessionStartedAt = System.currentTimeMillis();
    private boolean firstLoad = true;
    private boolean scannerVisible = false;
    private boolean decodingFrame = false;
    private String selectedRunId = null;
    private String selectedRunStatus = "";
    private String selectedDeviceId = "all";
    private String selectedProjectFilter = "all";
    private String latestDownloadUrl = "";
    private String latestVersionName = "";
    private String latestApkSha256 = "";
    private final List<String> latestDownloadUrls = new ArrayList<>();
    private long updateDownloadId = -1L;
    private boolean updateDownloading = false;
    private String lastUpdateDownloadError = "";
    private LifecycleRegistry lifecycleRegistry;
    private ProcessCameraProvider cameraProvider;
    private PreviewView scannerPreviewView;
    private TextView scannerStatus;
    private BarcodeScanner barcodeScanner;
    private Runnable pairAutoRunnable;
    private boolean pairingInProgress = false;
    private String selectedStatusFilter = "all";
    private String currentConsoleOutput = "";
    private int consoleOutputSyncedLength = 0;
    private int outputChunkSyncedCount = 0;
    private boolean consoleIncrementalUsesChunks = false;
    private String currentTab = "runs";
    private String settingsSection = null;
    private String lastRunsSig = "";
    private String lastDevicesSig = "";
    private boolean hasActiveRunVisible = false;

    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                if (selectedRunId == null) {
                    refreshDevices();
                    refreshRuns();
                } else {
                    refreshRunDetail(selectedRunId, false);
                }
            } catch (Throwable throwable) {
                // A transient refresh error must never kill the auto-refresh loop
                // or replace the UI with the startup-error screen. Keep polling.
            }
            handler.postDelayed(this, pollDelayMs());
        }
    };

    private long pollDelayMs() {
        if (selectedRunId != null) {
            return ("running".equals(selectedRunStatus) || "created".equals(selectedRunStatus))
                    ? CONSOLE_RUNNING_POLL_MS : POLL_MS;
        }
        // On the list: poll faster while something is actively running so progress
        // shows sooner; stay calm (slower) when everything is idle.
        return hasActiveRunVisible ? LIST_ACTIVE_POLL_MS : POLL_MS;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        lifecycleRegistry = new LifecycleRegistry(this);
        super.onCreate(savedInstanceState);
        try {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE);
            prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
            updateLauncherAlias();
            createNotificationChannel();
            requestNotificationPermission();
            buildUi();
            handlePairIntent(getIntent());
            loadCachedRuns();
            if (statusText != null) {
                statusText.setText(isEnglish() ? "Refreshing..." : "正在刷新...");
            }
            refreshDevices();
            refreshRuns();
            if (autoCheckUpdatesEnabled()) {
                checkForUpdates(false);
            } else {
                restoreUpdateBadgeFromPrefs();
            }
            handler.postDelayed(pollRunnable, pollDelayMs());
        } catch (Throwable throwable) {
            showStartupError(throwable);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START);
    }

    @Override
    protected void onResume() {
        super.onResume();
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME);
    }

    @Override
    protected void onPause() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE);
        super.onPause();
    }

    @Override
    protected void onStop() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopScannerCamera();
        handler.removeCallbacksAndMessages(null);
        executor.shutdownNow();
        updateExecutor.shutdownNow();
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY);
    }

    @Override
    public Lifecycle getLifecycle() {
        return lifecycleRegistry;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handlePairIntent(intent);
    }

    @Override
    public void onBackPressed() {
        if (scannerVisible) {
            closeScanner();
            return;
        }
        if (selectedRunId != null) {
            returnToList();
            return;
        }
        if ("settings".equals(currentTab) && settingsSection != null) {
            settingsSection = null;
            buildUi();
            return;
        }
        super.onBackPressed();
    }

    private void buildUi() {
        selectedRunId = null;
        selectedRunStatus = "";
        selectedStatusFilter = prefs.getString(PREF_STATUS_FILTER, "all");
        if (!"running".equals(selectedStatusFilter) && !"failed".equals(selectedStatusFilter)
                && !"succeeded".equals(selectedStatusFilter) && !"archived".equals(selectedStatusFilter)) {
            selectedStatusFilter = "all";
        }
        selectedDeviceId = prefs.getString("selected_device_id", "all");
        if (selectedDeviceId == null || selectedDeviceId.trim().isEmpty()) {
            selectedDeviceId = "all";
        }
        selectedProjectFilter = prefs.getString(PREF_PROJECT_FILTER, "all");
        if (selectedProjectFilter == null || selectedProjectFilter.trim().isEmpty()) {
            selectedProjectFilter = "all";
        }
        pairInput = null;
        pairButton = null;
        devicesScrollView = null;
        devicesContainer = null;
        runsContainer = null;
        renameDeviceButton = null;
        revokeDeviceButton = null;
        clearDeviceRunsButton = null;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), statusBarHeight() + dp(8), dp(18), navigationBarHeight() + dp(2));
        // Let the bottom bar draw edge-to-edge into the root padding (negative margins).
        root.setClipToPadding(false);
        root.setBackgroundColor(appBg());
        getWindow().setStatusBarColor(appBg());
        getWindow().setNavigationBarColor(appBg());

        FrameLayout header = new FrameLayout(this);
        header.setMinimumHeight(dp(40));

        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(dp(38), dp(38), Gravity.START | Gravity.CENTER_VERTICAL);
        header.addView(appIconView(), iconParams);

        LinearLayout headerText = new LinearLayout(this);
        headerText.setOrientation(LinearLayout.VERTICAL);
        headerText.setGravity(Gravity.CENTER);
        TextView title = new TextView(this);
        title.setText(screenTitle());
        title.setTextSize(18);
        title.setTextColor(textPrimary());
        title.setGravity(Gravity.CENTER);
        title.setTypeface(null, Typeface.BOLD);
        headerText.addView(title, matchWrap());

        connectionSubtitleText = new TextView(this);
        connectionSubtitleText.setTextSize(11);
        connectionSubtitleText.setGravity(Gravity.CENTER);
        updateConnectionSubtitle();
        headerText.addView(connectionSubtitleText, matchWrap());
        FrameLayout.LayoutParams headerTextParams = new FrameLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
        );
        header.addView(headerText, headerTextParams);

        updateBadgeButton = new TextView(this);
        updateBadgeButton.setTextSize(12);
        updateBadgeButton.setGravity(Gravity.CENTER);
        updateBadgeButton.setTextColor(updateAccent());
        updateBadgeButton.setTypeface(null, Typeface.BOLD);
        updateBadgeButton.setPadding(dp(8), 0, dp(8), 0);
        updateBadgeButton.setVisibility(View.GONE);
        updateBadgeButton.setOnClickListener(v -> confirmUpdateDownload());
        restoreUpdateBadgeFromPrefs();
        FrameLayout.LayoutParams updateParams = new FrameLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(42),
                Gravity.END | Gravity.CENTER_VERTICAL
        );
        header.addView(updateBadgeButton, updateParams);

        root.addView(header, matchWrap());

        statusText = new TextView(this);
        statusText.setText(t("connecting"));
        statusText.setTextSize(11);
        statusText.setTextColor(textSecondary());
        statusText.setGravity(Gravity.CENTER);
        statusText.setPadding(0, dp(4), 0, dp(7));
        root.addView(statusText, matchWrap());

        String rawSavedServerUrl = prefs.getString("server_url", "").trim();
        String savedServerUrl = normalizeServerUrl(rawSavedServerUrl);
        if (shouldReplaceSavedServerUrl(rawSavedServerUrl, savedServerUrl)) {
            boolean authServerChanged = shouldClearAuthForServerReplacement(rawSavedServerUrl, savedServerUrl);
            if (authServerChanged) {
                clearAllPairingAndCache();
            }
            prefs.edit()
                    .putString("server_url", savedServerUrl)
                    .putBoolean("inputs_locked", true)
                    .apply();
            if (authServerChanged) {
                statusText.setText(isEnglish() ? "Server changed. Pair again to continue." : "服务器已切换，请重新配对后继续使用。");
            }
        }
        accountToken();

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        root.addView(content, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        if ("settings".equals(currentTab)) {
            buildSettingsTab(content);
        } else {
            currentTab = "home";
            buildHomeTab(content);
        }
        root.addView(bottomTabs());

        setContentView(root);
    }

    private void showStartupError(Throwable throwable) {
        if (prefs == null) {
            prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        }
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), statusBarHeight() + dp(18), dp(18), dp(18));
        root.setBackgroundColor(appBg());

        TextView title = new TextView(this);
        title.setText(appDisplayName() + (isEnglish() ? " startup failed" : " 启动失败"));
        title.setTextSize(22);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(color("#B42318"));
        root.addView(title, matchWrap());

        TextView message = new TextView(this);
        message.setText(crashText(throwable));
        message.setTextSize(12);
        message.setTextColor(textPrimary());
        message.setTypeface(android.graphics.Typeface.MONOSPACE);
        message.setPadding(0, dp(12), 0, dp(12));
        root.addView(message, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        Button reset = new Button(this);
        reset.setText(isEnglish() ? "Reset local cache and restart" : "重置本地缓存并重启");
        reset.setAllCaps(false);
        styleActionButton(reset);
        reset.setOnClickListener(v -> {
            prefs.edit()
                    .remove(CACHE_RUNS)
                    .remove(CACHE_RUNS_AT)
                    .remove(CACHE_DEVICES)
                    .apply();
            Map<String, ?> values = prefs.getAll();
            SharedPreferences.Editor editor = prefs.edit();
            for (String key : values.keySet()) {
                if (key.startsWith(CACHE_RUNS_PREFIX)
                        || key.startsWith(CACHE_RUNS_AT_PREFIX)
                        || key.startsWith(CACHE_RUN_PREFIX)
                        || key.startsWith("notified_terminal_")) {
                    editor.remove(key);
                }
            }
            editor.apply();
            buildUi();
            refreshDevices();
            refreshRuns();
        });
        root.addView(reset, matchWrap());

        setContentView(root);
    }

    private String crashText(Throwable throwable) {
        StringWriter writer = new StringWriter();
        throwable.printStackTrace(new PrintWriter(writer));
        String text = writer.toString();
        return text.length() > 5000 ? text.substring(0, 5000) : text;
    }

    @ExperimentalGetImage
    private void buildPairOnboarding(LinearLayout content) {
        content.addView(emptyState(t("pair_this_phone"), t("pair_onboarding_subtitle"), "▣"), matchWrap());

        TextView scan = actionButton(t("scan_pair_qr"));
        scan.setTextSize(16);
        scan.setOnClickListener(v -> startQrScan());
        LinearLayout.LayoutParams scanParams = matchWrap();
        scanParams.setMargins(0, 0, 0, dp(10));
        content.addView(scan, scanParams);

        TextView code = actionButton(t("enter_code"));
        code.setTextSize(16);
        code.setOnClickListener(v -> {
            currentTab = "settings";
            buildUi();
            if (pairInput != null) {
                pairInput.requestFocus();
            }
        });
        content.addView(code, matchWrap());
    }

    private boolean hasPairedDevice() {
        String paired = prefs.getString("paired_device_id", "");
        return paired != null && !paired.trim().isEmpty();
    }

    private void buildHomeTab(LinearLayout content) {
        if (!hasPairedDevice()) {
            buildPairOnboarding(content);
            return;
        }
        devicesScrollView = new HorizontalScrollView(this);
        devicesScrollView.setHorizontalScrollBarEnabled(false);
        devicesContainer = new LinearLayout(this);
        devicesContainer.setOrientation(LinearLayout.HORIZONTAL);
        devicesScrollView.addView(devicesContainer, new HorizontalScrollView.LayoutParams(
                HorizontalScrollView.LayoutParams.WRAP_CONTENT,
                HorizontalScrollView.LayoutParams.WRAP_CONTENT
        ));
        content.addView(devicesScrollView, matchWrap());

        LinearLayout deviceHeader = new LinearLayout(this);
        deviceHeader.setOrientation(LinearLayout.VERTICAL);

        LinearLayout infoRow = new LinearLayout(this);
        infoRow.setOrientation(LinearLayout.HORIZONTAL);
        infoRow.setGravity(Gravity.CENTER_VERTICAL);

        deviceSummaryText = new TextView(this);
        deviceSummaryText.setText("");
        deviceSummaryText.setTextSize(11);
        deviceSummaryText.setTextColor(textSecondary());
        deviceSummaryText.setPadding(0, dp(6), dp(8), dp(6));
        deviceSummaryText.setOnClickListener(v -> {
            gpuExpanded = !gpuExpanded;
            updateDeviceSummary();
        });
        infoRow.addView(deviceSummaryText, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView refreshDeviceButton = actionButton(actionLabel("↻", "", 1.34f));
        refreshDeviceButton.setContentDescription(t("refresh"));
        refreshDeviceButton.setOnClickListener(v -> {
            refreshDevices(true);
            refreshRuns(true);
        });
        infoRow.addView(refreshDeviceButton, new LinearLayout.LayoutParams(dp(42), dp(42)));

        TextView deviceMenuButton = actionButton("⋯");
        deviceMenuButton.setTextSize(22);
        deviceMenuButton.setOnClickListener(v -> showDeviceActionsDialog());
        LinearLayout.LayoutParams menuParams = new LinearLayout.LayoutParams(dp(42), dp(42));
        menuParams.setMargins(dp(8), 0, 0, 0);
        infoRow.addView(deviceMenuButton, menuParams);

        deviceHeader.addView(infoRow, matchWrap());

        deviceGpuContainer = new LinearLayout(this);
        deviceGpuContainer.setOrientation(LinearLayout.VERTICAL);
        deviceGpuContainer.setVisibility(View.GONE);
        attachGpuSwipe(deviceGpuContainer);
        LinearLayout.LayoutParams gpuParams = matchWrap();
        gpuParams.setMargins(0, 0, 0, dp(4));
        deviceHeader.addView(deviceGpuContainer, gpuParams);

        content.addView(deviceHeader, matchWrap());

        // Status + project as two independent controls (device is chosen via the
        // strip above). Separate buttons let you set both without reopening a menu.
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER_VERTICAL);

        TextView statusFilterButton = actionButton(t("status") + ": " + statusFilterLabel(selectedStatusFilter));
        statusFilterButton.setOnClickListener(v -> showStatusFilterDialog());
        controls.addView(statusFilterButton, new LinearLayout.LayoutParams(0, dp(42), 1));

        TextView projectFilterButton = actionButton(t("project") + ": " + projectFilterLabel(selectedProjectFilter));
        projectFilterButton.setOnClickListener(v -> showProjectFilterDialog());
        LinearLayout.LayoutParams pParams = new LinearLayout.LayoutParams(0, dp(42), 1);
        pParams.setMargins(dp(8), 0, 0, 0);
        controls.addView(projectFilterButton, pParams);

        LinearLayout.LayoutParams controlsParams = matchWrap();
        controlsParams.setMargins(0, dp(2), 0, dp(2));
        content.addView(controls, controlsParams);

        // The single run list (filtered by selected device + project + status).
        ScrollView scrollView = new ScrollView(this);
        runsContainer = new LinearLayout(this);
        runsContainer.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(runsContainer);
        content.addView(scrollView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        mergeDevicesFromCachedRuns();
        loadCachedDevices();
        loadCachedRuns();
        updateDeviceSummary();
        updateDeviceActionButtons();
    }

    @ExperimentalGetImage
    private void buildSettingsTab(LinearLayout content) {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(false);
        scrollView.setVerticalScrollBarEnabled(false);
        LinearLayout settingsContent = new LinearLayout(this);
        settingsContent.setOrientation(LinearLayout.VERTICAL);
        settingsContent.setPadding(0, 0, 0, dp(8));
        scrollView.addView(settingsContent, matchWrap());
        content.addView(scrollView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        if (settingsSection == null) {
            buildSettingsHome(settingsContent);
            return;
        }

        // Second level: a clean back link (the top header already shows the
        // section title), then the section rows.
        TextView back = new TextView(this);
        back.setText("‹  " + t("settings"));
        back.setTextSize(15);
        back.setTextColor(color("#2563EB"));
        back.setGravity(Gravity.CENTER_VERTICAL);
        back.setPadding(dp(2), dp(6), dp(12), dp(10));
        back.setClickable(true);
        back.setOnClickListener(v -> {
            settingsSection = null;
            buildUi();
        });
        LinearLayout.LayoutParams backParams = matchWrap();
        settingsContent.addView(back, backParams);

        switch (settingsSection) {
            case "pair":
                buildPairSection(settingsContent);
                break;
            case "notifications":
                buildNotificationsSection(settingsContent);
                break;
            case "storage":
                buildStorageSection(settingsContent);
                break;
            default:
                settingsSection = null;
                buildSettingsHome(settingsContent);
                break;
        }
    }

    private String settingsSectionTitle(String key) {
        if (key == null) {
            return t("settings");
        }
        switch (key) {
            case "pair": return isEnglish() ? "Pairing" : "配对设备";
            case "appearance": return t("appearance");
            case "notifications": return t("notifications");
            case "security": return t("security");
            case "storage": return t("storage");
            case "about": return isEnglish() ? "About" : "关于";
            default: return t("settings");
        }
    }

    private void openSettingsSection(String key) {
        settingsSection = key;
        buildUi();
    }

    @ExperimentalGetImage
    private void buildSettingsHome(LinearLayout c) {
        // Pairing stays on the top level — it's important, and the real scan/code
        // controls give the page substance instead of a bare list of links.
        TextView pairTitle = sectionTitle(settingsSectionTitle("pair"));
        c.addView(pairTitle, matchWrap());
        buildPairSection(c);

        // Appearance / security / about live directly on the settings home.
        c.addView(sectionTitle(t("appearance")), matchWrap());
        buildAppearanceSection(c);

        c.addView(sectionTitle(t("security")), matchWrap());
        buildSecuritySection(c);

        TextView more = sectionTitle(isEnglish() ? "Preferences" : "通用设置");
        c.addView(more, matchWrap());
        c.addView(settingsGroup(
                settingsRow("✓", color("#16A34A"),
                        settingsSectionTitle("notifications"),
                        isEnglish() ? "Run alerts, quiet hours" : "运行提醒、免打扰",
                        "", true, v -> openSettingsSection("notifications")),
                settingsRow("▤", color("#0EA5E9"),
                        settingsSectionTitle("storage"),
                        isEnglish() ? "Cache, clear, export" : "缓存、清理、导出",
                        "", true, v -> openSettingsSection("storage"))
        ));

        c.addView(sectionTitle(settingsSectionTitle("about")), matchWrap());
        buildAboutSection(c);

        c.addView(buildSettingsFooter());
    }

    private View buildSettingsFooter() {
        LinearLayout f = new LinearLayout(this);
        f.setOrientation(LinearLayout.VERTICAL);
        f.setGravity(Gravity.CENTER);
        f.setPadding(0, dp(20), 0, dp(12));

        TextView name = new TextView(this);
        name.setText(appDisplayName());
        name.setTextSize(13);
        name.setTextColor(textSecondary());
        name.setGravity(Gravity.CENTER);
        f.addView(name, matchWrap());

        String ver = currentVersionName();
        String cli = prefs.getString("latest_cli_version", "");
        String sub = "v" + ver + (cli.isEmpty() ? "" : "  ·  CLI " + cli);
        TextView v = new TextView(this);
        v.setText(sub);
        v.setTextSize(11);
        v.setTextColor(textSecondary());
        v.setGravity(Gravity.CENTER);
        v.setPadding(0, dp(2), 0, 0);
        f.addView(v, matchWrap());
        return f;
    }

    @ExperimentalGetImage
    private void buildPairSection(LinearLayout settingsContent) {
        LinearLayout.LayoutParams tightGroup = matchWrap();
        tightGroup.setMargins(0, 0, 0, dp(3));
        settingsContent.addView(settingsGroup(settingsRow(
                "qr",
                color("#2563EB"),
                t("scan_qr_auth"),
                t("scan_qr_subtitle"),
                "",
                true,
                v -> startQrScan()
        )), tightGroup);

        LinearLayout pairControls = new LinearLayout(this);
        pairControls.setOrientation(LinearLayout.HORIZONTAL);
        pairControls.setGravity(Gravity.CENTER_VERTICAL);

        pairInput = new EditText(this);
        pairInput.setSingleLine(true);
        pairInput.setTextSize(14);
        pairInput.setHint(t("six_digit_code"));
        pairInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        pairInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(6)});
        styleInput(pairInput);
        pairInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                schedulePairAutoConfirm();
            }
        });
        pairControls.addView(pairInput, new LinearLayout.LayoutParams(0, dp(46), 1));

        pairButton = actionButton(t("pair"));
        pairButton.setOnClickListener(v -> confirmPairing());
        LinearLayout.LayoutParams pairButtonParams = new LinearLayout.LayoutParams(dp(78), dp(46));
        pairButtonParams.setMargins(dp(8), 0, 0, 0);
        pairControls.addView(pairButton, pairButtonParams);
        LinearLayout.LayoutParams pairParams = matchWrap();
        pairParams.setMargins(0, 0, 0, dp(3));
        settingsContent.addView(pairCodeCard(pairControls), pairParams);
        settingsContent.addView(settingsGroup(settingsRow(
                "⇄",
                color("#14B8A6"),
                t("sync_space"),
                t("sync_space_subtitle"),
                syncSpaceLabel(),
                true,
                v -> showSyncSpaceDialog()
        )));
    }

    private void buildAppearanceSection(LinearLayout settingsContent) {
        settingsContent.addView(settingsGroup(
                settingsRow(
                        "theme_icon",
                        color("#8B5CF6"),
                        t("theme"),
                        t("theme_subtitle"),
                        themeLabel(),
                        true,
                        v -> showThemeDialog()
                ),
                settingsRow(
                        "language_icon",
                        color("#0EA5E9"),
                        t("language"),
                        t("language_subtitle"),
                        languageLabel(),
                        true,
                        v -> showLanguageDialog()
                )
        ));
    }

    private void buildNotificationsSection(LinearLayout settingsContent) {
        settingsContent.addView(settingsGroup(
                settingsRow(
                        "✓",
                        color("#16A34A"),
                        t("succeeded_runs"),
                        t("succeeded_runs_subtitle"),
                        onOff(notifySuccessEnabled()),
                        false,
                        v -> togglePreference(PREF_NOTIFY_SUCCESS, true, v)
                ),
                settingsRow(
                        "!",
                        color("#DC2626"),
                        t("failed_runs"),
                        t("failed_runs_subtitle"),
                        onOff(notifyFailureEnabled()),
                        false,
                        v -> togglePreference(PREF_NOTIFY_FAILURE, true, v)
                ),
                settingsRow(
                        "⏱",
                        color("#2563EB"),
                        t("minimum_run_time"),
                        t("minimum_run_time_subtitle"),
                        notifyMinDurationLabel(),
                        true,
                        v -> showNotifyMinDurationDialog(v)
                ),
                settingsRow(
                        "quiet_icon",
                        color("#64748B"),
                        t("quiet_hours"),
                        t("quiet_hours_subtitle"),
                        onOff(quietHoursEnabled()),
                        false,
                        v -> togglePreference(PREF_NOTIFY_QUIET_HOURS, false, v)
                )
        ));
    }

    private void buildSecuritySection(LinearLayout settingsContent) {
        settingsContent.addView(settingsGroup(
                settingsRow(
                        "mask_icon",
                        color("#7C3AED"),
                        t("mask_sensitive"),
                        t("mask_sensitive_subtitle"),
                        onOff(maskSensitiveEnabled()),
                        false,
                        v -> togglePreference(PREF_MASK_SENSITIVE, true, v)
                ),
                settingsRow(
                        "▣",
                        color("#2563EB"),
                        t("device_security"),
                        t("device_security_subtitle"),
                        deviceSecurityLabel(),
                        true,
                        v -> showDeviceSecurityDialog()
                )
        ));
    }

    private void buildStorageSection(LinearLayout settingsContent) {
        settingsContent.addView(settingsGroup(
                settingsRow(
                        "▤",
                        color("#0EA5E9"),
                        t("saved_data"),
                        t("saved_data_subtitle"),
                        localCacheSizeLabel(),
                        false,
                        null
                ),
                settingsRow(
                        "⌫",
                        color("#EF4444"),
                        t("clear_local_cache"),
                        t("clear_local_cache_subtitle"),
                        "",
                        true,
                        v -> confirmClearLocalCache()
                ),
                settingsRow(
                        "✓",
                        color("#16A34A"),
                        t("clear_completed_runs"),
                        t("clear_completed_runs_subtitle"),
                        "",
                        true,
                        v -> confirmClearCompletedLocalRuns()
                ),
                settingsRow(
                        "⌘",
                        color("#64748B"),
                        t("console_history"),
                        t("console_history_subtitle"),
                        consoleHistoryLabel(),
                        true,
                        v -> showConsoleHistoryDialog(v)
                ),
                settingsRow(
                        "⇪",
                        color("#2563EB"),
                        t("export_runs"),
                        t("export_runs_subtitle"),
                        "",
                        true,
                        v -> exportRuns()
                ),
                settingsRow(
                        "☁",
                        color("#EF4444"),
                        t("clear_cloud_runs"),
                        t("clear_cloud_runs_subtitle"),
                        "",
                        true,
                        v -> confirmClearCloudRuns()
                ),
                settingsRow(
                        "⊘",
                        color("#DC2626"),
                        t("delete_sync_space"),
                        t("delete_sync_space_subtitle"),
                        "",
                        true,
                        v -> confirmDeleteSyncSpace()
                )
        ));
    }

    private void buildAboutSection(LinearLayout settingsContent) {
        settingsContent.addView(settingsGroup(settingsRow(
                "♥",
                color("#EF4444"),
                t("donation"),
                t("donation_subtitle"),
                "",
                true,
                v -> showDonationSheet()
        )));

        String ver = currentVersionName();
        String cli = prefs.getString("latest_cli_version", "");
        if (!cli.isEmpty()) ver += " / CLI " + cli;
        settingsContent.addView(settingsGroup(
                settingsRow(
                        "↻",
                        color("#2563EB"),
                        t("auto_check_updates"),
                        t("auto_check_updates_subtitle"),
                        onOff(autoCheckUpdatesEnabled()),
                        false,
                        v -> togglePreference(PREF_UPDATE_AUTO_CHECK, true, v)
                ),
                settingsRow(
                        "⌁",
                        color("#0EA5E9"),
                        t("wifi_only_downloads"),
                        t("wifi_only_downloads_subtitle"),
                        onOff(wifiOnlyUpdatesEnabled()),
                        false,
                        v -> togglePreference(PREF_UPDATE_WIFI_ONLY, false, v)
                ),
                settingsRow(
                        "↓",
                        color("#2563EB"),
                        t("update"),
                        updateRowSubtitle(),
                        updateRowValue(),
                        true,
                        v -> {
                            if (hasAvailableUpdate()) {
                                confirmUpdateDownload();
                            } else {
                                checkForUpdates(true);
                            }
                        }
                ),
                settingsRow(
                        "✦",
                        color("#F59E0B"),
                        t("whats_new"),
                        t("whats_new_subtitle"),
                        "",
                        true,
                        v -> showWhatsNewDialog()
                ),
                settingsRow(
                        "i",
                        textSecondary(),
                        t("version"),
                        appDisplayName() + " app",
                        ver,
                        false,
                        null
                ),
                settingsRow(
                        "diagnostics_icon",
                        color("#64748B"),
                        t("diagnostics"),
                        t("diagnostics_subtitle"),
                        "",
                        true,
                        v -> {
                            String diag = diagnosticsText();
                            copyText(appDisplayName() + " diagnostics", diag);
                            statusText.setText(t("diagnostics_copied"));
                            openExternalUrl("https://github.com/HaolemeApp/Haoleme/issues/new");
                        }
                ),
                settingsRow(
                        "github",
                        textPrimary(),
                        t("github"),
                        t("github_subtitle"),
                        "",
                        true,
                        v -> openExternalUrl("https://github.com/HaolemeApp/Haoleme")
                )
        ));
    }

    private TextView sectionTitle(String title) {
        // iOS-style grouped section header: small, muted, slightly tracked.
        TextView view = new TextView(this);
        view.setText(title);
        view.setTextSize(12);
        view.setTypeface(null, Typeface.NORMAL);
        view.setAllCaps(true);
        view.setLetterSpacing(0.04f);
        view.setTextColor(textSecondary());
        view.setPadding(dp(4), dp(12), 0, dp(6));
        return view;
    }

    private LinearLayout pairCodeCard(LinearLayout pairControls) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(12));
        card.setBackground(roundedBg(cardBg(), 16, cardStroke()));
        card.setElevation(0);

        TextView title = new TextView(this);
        title.setText(t("or_enter_code"));
        title.setTextSize(13);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(textPrimary());
        card.addView(title, matchWrap());

        TextView subtitle = new TextView(this);
        subtitle.setText(t("code_instead_qr"));
        subtitle.setTextSize(11);
        subtitle.setTextColor(textSecondary());
        subtitle.setPadding(0, dp(2), 0, dp(9));
        card.addView(subtitle, matchWrap());

        card.addView(pairControls, matchWrap());
        return card;
    }

    private View emptyState(String title, String subtitle, String icon) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(18), dp(34), dp(18), dp(34));
        card.setBackground(roundedBg(cardBg(), 16, cardStroke()));
        card.setElevation(0);

        TextView iconView = new TextView(this);
        iconView.setText(icon);
        iconView.setTextSize(34);
        iconView.setTypeface(null, Typeface.BOLD);
        iconView.setTextColor(textSecondary());
        iconView.setGravity(Gravity.CENTER);
        card.addView(iconView, matchWrap());

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextSize(18);
        titleView.setTypeface(null, Typeface.BOLD);
        titleView.setTextColor(textPrimary());
        titleView.setGravity(Gravity.CENTER);
        titleView.setPadding(0, dp(10), 0, 0);
        card.addView(titleView, matchWrap());

        TextView subtitleView = new TextView(this);
        subtitleView.setText(subtitle);
        subtitleView.setTextSize(13);
        subtitleView.setTextColor(textSecondary());
        subtitleView.setGravity(Gravity.CENTER);
        subtitleView.setPadding(0, dp(4), 0, 0);
        card.addView(subtitleView, matchWrap());
        return card;
    }

    private View settingsIconView(String icon, int iconColor) {
        FrameLayout chip = new FrameLayout(this);
        chip.setBackgroundColor(Color.TRANSPARENT);

        if ("github".equals(icon)) {
            ImageView image = new ImageView(this);
            image.setImageResource(R.drawable.github_mark);
            image.setColorFilter(iconColor);
            image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            FrameLayout.LayoutParams imageParams = new FrameLayout.LayoutParams(dp(28), dp(28), Gravity.CENTER);
            chip.addView(image, imageParams);
            return chip;
        }
        if ("qr".equals(icon)) {
            QrIconView qr = new QrIconView(this, iconColor);
            chip.addView(qr, new FrameLayout.LayoutParams(dp(30), dp(30), Gravity.CENTER));
            return chip;
        }
        if ("theme_icon".equals(icon)) {
            ThemeIconView theme = new ThemeIconView(this, iconColor);
            chip.addView(theme, new FrameLayout.LayoutParams(dp(28), dp(28), Gravity.CENTER));
            return chip;
        }
        if ("quiet_icon".equals(icon)) {
            QuietHoursIconView quiet = new QuietHoursIconView(this, iconColor);
            chip.addView(quiet, new FrameLayout.LayoutParams(dp(28), dp(28), Gravity.CENTER));
            return chip;
        }
        if ("mask_icon".equals(icon)) {
            MaskIconView mask = new MaskIconView(this, iconColor);
            chip.addView(mask, new FrameLayout.LayoutParams(dp(28), dp(28), Gravity.CENTER));
            return chip;
        }
        if ("language_icon".equals(icon)) {
            LanguageIconView language = new LanguageIconView(this, iconColor);
            chip.addView(language, new FrameLayout.LayoutParams(dp(28), dp(28), Gravity.CENTER));
            return chip;
        }
        if ("diagnostics_icon".equals(icon)) {
            DiagnosticsIconView diagnostics = new DiagnosticsIconView(this, iconColor);
            chip.addView(diagnostics, new FrameLayout.LayoutParams(dp(28), dp(28), Gravity.CENTER));
            return chip;
        }

        TextView iconView = new TextView(this);
        iconView.setText(icon);
        float textSize = icon.length() > 1 ? 20f : 27f;
        int box = dp(30);
        if ("♥".equals(icon)) {
            textSize = 21f;
            box = dp(26);
        }
        iconView.setTextSize(textSize);
        iconView.setTypeface(null, Typeface.BOLD);
        iconView.setGravity(Gravity.CENTER);
        iconView.setIncludeFontPadding(false);
        iconView.setTextColor(iconColor);
        chip.addView(iconView, new FrameLayout.LayoutParams(box, box, Gravity.CENTER));
        return chip;
    }

    private LinearLayout settingsRow(
            String icon,
            int iconColor,
            String title,
            String subtitle,
            String value,
            boolean chevron,
            View.OnClickListener listener
    ) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(52));
        row.setPadding(dp(12), dp(8), dp(11), dp(8));
        row.setBackground(rowPressBg());
        row.setElevation(0);
        row.setClickable(listener != null);
        if (listener != null) {
            row.setOnClickListener(listener);
        }

        row.addView(settingsIconView(icon, iconColor), new LinearLayout.LayoutParams(dp(32), dp(32)));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextSize(14);
        titleView.setTextColor(textPrimary());
        titleView.setSingleLine(false);
        labels.addView(titleView, matchWrap());
        if (subtitle != null && !subtitle.trim().isEmpty()) {
            TextView subtitleView = new TextView(this);
            subtitleView.setText(subtitle.trim());
            subtitleView.setTextSize(11);
            subtitleView.setTextColor(textSecondary());
            subtitleView.setPadding(0, dp(2), 0, 0);
            labels.addView(subtitleView, matchWrap());
        }
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        labelParams.setMargins(dp(9), 0, dp(8), 0);
        row.addView(labels, labelParams);

        if (value != null && !value.trim().isEmpty()) {
            TextView valueView = new TextView(this);
            valueView.setTag("settings_value");
            valueView.setText(value.trim());
            valueView.setTextSize(13);
            valueView.setTextColor(textSecondary());
            valueView.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
            row.addView(valueView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        }
        if (chevron) {
            TextView chevronView = new TextView(this);
            chevronView.setText("›");
            chevronView.setTextSize(24);
            chevronView.setTextColor(chevronColor());
            chevronView.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams chevronParams = new LinearLayout.LayoutParams(dp(22), LinearLayout.LayoutParams.WRAP_CONTENT);
            chevronParams.setMargins(dp(4), 0, 0, 0);
            row.addView(chevronView, chevronParams);
        }

        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(0, 0, 0, 0);
        row.setLayoutParams(params);
        return row;
    }

    private LinearLayout settingsGroup(View... rows) {
        LinearLayout group = new LinearLayout(this);
        group.setOrientation(LinearLayout.VERTICAL);
        group.setBackground(roundedBg(cardBg(), 12, cardStroke()));
        group.setClipToOutline(true);
        boolean first = true;
        for (View row : rows) {
            if (row == null) continue;
            if (!first) group.addView(settingsDividerView());
            group.addView(row);
            first = false;
        }
        LinearLayout.LayoutParams lp = matchWrap();
        lp.setMargins(0, 0, 0, dp(10));
        group.setLayoutParams(lp);
        return group;
    }

    private View settingsDividerView() {
        View d = new View(this);
        d.setBackgroundColor(settingsDivider());
        int h = Math.max(1, Math.round(getResources().getDisplayMetrics().density * 0.7f));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, h);
        lp.setMargins(dp(50), 0, 0, 0);
        d.setLayoutParams(lp);
        return d;
    }

    private int settingsDivider() {
        return isDarkTheme() ? color("#2C2C2E") : color("#E5E5EA");
    }

    private int pressHighlight() {
        return isDarkTheme() ? color("#2E2E36") : color("#E9EDF3");
    }

    private android.graphics.drawable.Drawable rowPressBg() {
        android.content.res.ColorStateList ripple = android.content.res.ColorStateList.valueOf(pressHighlight());
        android.graphics.drawable.StateListDrawable content = new android.graphics.drawable.StateListDrawable();
        content.addState(new int[]{android.R.attr.state_pressed}, new android.graphics.drawable.ColorDrawable(pressHighlight()));
        content.addState(new int[0], new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        return new android.graphics.drawable.RippleDrawable(ripple, content, new android.graphics.drawable.ColorDrawable(Color.WHITE));
    }

    private void showThemeDialog() {
        String[] labels = new String[]{t("light"), t("dark")};
        String[] values = new String[]{THEME_LIGHT, THEME_DARK};
        String current = themeMode();
        int selected = THEME_DARK.equals(current) ? 1 : 0;
        AlertDialog d = dialogBuilder()
                .setTitle(t("theme"))
                .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                    prefs.edit().putString(PREF_THEME_MODE, values[which]).apply();
                    dialog.dismiss();
                    buildUi();
                })
                .setNegativeButton(t("cancel"), null)
                .create();
        applyDialogStyle(d);
        d.show();
    }

    private void showLanguageDialog() {
        String[] labels = new String[]{"中文", "English"};
        String[] values = new String[]{LANG_ZH, LANG_EN};
        String current = languageMode();
        int selected = LANG_EN.equals(current) ? 1 : 0;
        AlertDialog d = dialogBuilder()
                .setTitle(t("language"))
                .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                    prefs.edit().putString(PREF_LANGUAGE_MODE, values[which]).apply();
                    dialog.dismiss();
                    updateLauncherAlias();
                    buildUi();
                    if (statusText != null) {
                        statusText.setText(t("language_updated"));
                    }
                })
                .setNegativeButton(t("cancel"), null)
                .create();
        applyDialogStyle(d);
        d.show();
    }

    private void showConsoleHistoryDialog(View row) {
        int[] values = new int[]{30000, 100000, 300000, 1000000};
        String[] labels = new String[]{"30k chars", "100k chars", "300k chars", "1M chars"};
        int current = consoleHistoryLimit();
        int selected = 2;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == current) {
                selected = i;
                break;
            }
        }
        AlertDialog d = dialogBuilder()
                .setTitle(t("console_history"))
                .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                    prefs.edit().putInt(PREF_CONSOLE_HISTORY_CHARS, values[which]).apply();
                    dialog.dismiss();
                    updateSettingsRowValue(row, labels[which]);
                    if (selectedRunId != null) {
                        renderConsoleText();
                    }
                    statusText.setText(isEnglish() ? "Console history window set to " + labels[which] + "." : "控制台历史窗口已设置为 " + labels[which] + "。");
                })
                .setNegativeButton(t("cancel"), null)
                .create();
        applyDialogStyle(d);
        d.show();
    }

    @ExperimentalGetImage
    private void showSyncSpaceDialog() {
        String[] labels = new String[]{t("share_sync_space"), t("join_sync_space"), t("scan_pair_qr")};
        AlertDialog d = dialogBuilder()
                .setTitle(t("sync_space"))
                .setItems(labels, (dialog, which) -> {
                    if (which == 0) {
                        showShareDeviceChoice();
                    } else if (which == 1) {
                        showJoinSyncSpaceDialog();
                    } else {
                        startQrScan();
                    }
                })
                .setNegativeButton(t("cancel"), null)
                .create();
        applyDialogStyle(d);
        d.show();
    }

    private void showShareDeviceChoice() {
        if (deviceNames.isEmpty()) {
            loadCachedDevices();
        }
        List<String> ids = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        ids.add("all");
        labels.add(isEnglish() ? "All devices" : "全部设备");
        for (Map.Entry<String, String> e : deviceNames.entrySet()) {
            ids.add(e.getKey());
            boolean online = deviceOnline.getOrDefault(e.getKey(), false);
            String label = e.getValue() + (online ? (isEnglish() ? " (online)" : " (在线)") : "");
            labels.add(label);
        }
        if (ids.size() <= 1) {
            // no devices, share all
            createSyncSpaceShare(null);
            return;
        }
        String[] items = labels.toArray(new String[0]);
        AlertDialog d = dialogBuilder()
                .setTitle(isEnglish() ? "Share run records for which devices?" : "分享哪些设备的运行记录？")
                .setItems(items, (dialog, which) -> {
                    String chosenId = ids.get(which);
                    String dev = "all".equals(chosenId) ? null : chosenId;
                    createSyncSpaceShare(dev);
                })
                .setNegativeButton(t("cancel"), null)
                .create();
        applyDialogStyle(d);
        d.show();
    }

    private void createSyncSpaceShare(String shareDeviceId) {
        statusText.setText(isEnglish() ? "Creating shared space code..." : "正在生成共享空间码...");
        updateExecutor.submit(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("clientName", appDisplayName() + " Android");
                payload.put("encryptionKey", base64UrlEncode(accountEncryptionKeyBytes()));
                String responseText = httpPostJson(normalizedServerUrl() + "/api/space/share", payload.toString());
                JSONObject response = responseText.isEmpty() ? new JSONObject() : new JSONObject(responseText);
                String code = response.optString("code", "").trim();
                String shareToken = response.optString("shareToken", "").trim();
                String expiresAt = response.optString("expiresAt", "").trim();
                String spaceId = response.optString("spaceId", "").trim();
                String spaceUrl = buildSyncSpaceUrl(normalizedServerUrl(), code, shareToken, shareDeviceId);
                handler.post(() -> {
                    if (!spaceId.isEmpty()) {
                        prefs.edit().putString("space_id", spaceId).apply();
                    }
                    buildUi();
                    if (shareDeviceId != null) {
                        String devName = deviceNames.getOrDefault(shareDeviceId, shareDeviceId);
                        statusText.setText((isEnglish() ? "Shared space code for device " : "已为设备 ") + devName + (isEnglish() ? " created." : " 生成共享空间码。"));
                    } else {
                        statusText.setText(isEnglish() ? "Shared space code created." : "共享空间码已生成。");
                    }
                    showSyncSpaceShareDialog(code, shareToken, expiresAt, spaceUrl);
                });
            } catch (Exception e) {
                handler.post(() -> statusText.setText(syncSpaceFailureMessage(e)));
            }
        });
    }

    private void showSyncSpaceShareDialog(String code, String shareToken, String expiresAt, String spaceUrl) {
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(8), dp(8), dp(8), 0);

        TextView codeView = new TextView(this);
        codeView.setText(code);
        codeView.setTextSize(34);
        codeView.setTypeface(null, Typeface.BOLD);
        codeView.setGravity(Gravity.CENTER);
        codeView.setTextColor(textPrimary());
        body.addView(codeView, matchWrap());

        TextView hint = new TextView(this);
        String expiry = expiresAt == null || expiresAt.isEmpty() ? "" : ("\n" + (isEnglish() ? "Expires: " : "过期时间：") + expiresAt);
        hint.setText((isEnglish()
                ? "Use this code within 5 minutes, or scan the QR with another Haoleme app. The other app will see run records for the shared devices (all or selected)."
                : "请在 5 分钟内使用这个码，或用另一台好了么扫码加入。对方将看到共享设备（全部或选中）的运行记录。") + expiry);
        hint.setTextSize(13);
        hint.setTextColor(textSecondary());
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, dp(6), 0, dp(12));
        body.addView(hint, matchWrap());

        try {
            ImageView qr = new ImageView(this);
            qr.setImageBitmap(qrBitmap(spaceUrl, dp(220)));
            qr.setAdjustViewBounds(true);
            LinearLayout.LayoutParams qrParams = new LinearLayout.LayoutParams(dp(232), dp(232));
            qrParams.gravity = Gravity.CENTER_HORIZONTAL;
            body.addView(qr, qrParams);
        } catch (Exception ignored) {
            TextView fallback = new TextView(this);
            fallback.setText(spaceUrl);
            fallback.setTextSize(11);
            fallback.setTextColor(textSecondary());
            fallback.setPadding(0, dp(8), 0, 0);
            body.addView(fallback, matchWrap());
        }

        AlertDialog d = dialogBuilder()
                .setTitle(t("sync_space"))
                .setView(body)
                .setNegativeButton(t("close"), null)
                .setPositiveButton(t("copy"), (dialog, which) -> copyText(t("sync_space_code"), code))
                .create();
        applyDialogStyle(d);
        d.show();
    }

    private void showJoinSyncSpaceDialog() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setTextSize(18);
        input.setHint(t("sync_space_code"));
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(6)});
        styleInput(input);
        int pad = dp(18);
        FrameLayout wrapper = new FrameLayout(this);
        wrapper.setPadding(pad, dp(8), pad, 0);
        wrapper.addView(input, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(50)));
        AlertDialog d = dialogBuilder()
                .setTitle(t("join_sync_space"))
                .setMessage(isEnglish() ? "Enter the 6-digit code shown on another Haoleme app to share its run records." : "输入另一台好了么显示的 6 位共享空间码，以共享其运行记录。")
                .setView(wrapper)
                .setNegativeButton(t("cancel"), null)
                .setPositiveButton(t("join_sync_space"), (dialog, which) -> joinSyncSpaceCode(input.getText().toString(), "", normalizedServerUrl()))
                .create();
        applyDialogStyle(d);
        d.show();
    }

    private void joinSyncSpaceCode(String rawCode, String shareToken, String serverUrl) {
        joinSyncSpaceCode(rawCode, shareToken, serverUrl, null);
    }

    private void joinSyncSpaceCode(String rawCode, String shareToken, String serverUrl, String deviceId) {
        String code = rawCode == null ? "" : rawCode.replaceAll("\\D", "");
        if (code.length() != 6) {
            statusText.setText(isEnglish() ? "Enter the 6-digit shared space code." : "请输入 6 位共享空间码。");
            return;
        }
        String targetServer = normalizeServerUrl(serverUrl);
        statusText.setText(isEnglish() ? "Joining shared space..." : "正在加入共享空间...");
        updateExecutor.submit(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("code", code);
                if (shareToken != null && !shareToken.trim().isEmpty()) {
                    payload.put("shareToken", shareToken.trim());
                }
                payload.put("clientName", appDisplayName() + " Android");
                payload.put("appVersionCode", currentVersionCode());
                payload.put("appVersionName", currentVersionName());
                payload.put("platform", "android");
                String responseText = httpRequest(targetServer + "/api/space/join", "POST", false, payload.toString());
                JSONObject response = responseText.isEmpty() ? new JSONObject() : new JSONObject(responseText);
                String token = response.optString("token", "").trim();
                String spaceId = response.optString("spaceId", "").trim();
                String joinedAt = response.optString("joinedAt", "").trim();
                String encryptionKey = response.optString("encryptionKey", "").trim();
                if (token.isEmpty()) {
                    throw new IOException("cloud returned empty sync token");
                }
                handler.post(() -> {
                    // Do not clear local run records so original user's run history is preserved when joining shared space
                    // clearLocalCache();  // removed per requirement
                    SharedPreferences.Editor editor = prefs.edit()
                            .putString("server_url", targetServer)
                            .putString("token", token)
                            .putString("paired_account", "sync-space")
                            .putString("space_id", spaceId)
                            .putString("space_joined_at", joinedAt)
                            .putBoolean("inputs_locked", true)
                            .remove("paired_device_id")
                            .remove("paired_device_name")
                            .remove("selected_device_id");
                    if (!encryptionKey.isEmpty()) {
                        editor.putString("encryption_key_b64", encryptionKey);
                    }
                    selectedDeviceId = (deviceId != null && !deviceId.trim().isEmpty() && !"all".equals(deviceId)) ? deviceId : "all";
                    editor.putString("selected_device_id", selectedDeviceId);
                    editor.apply();
                    selectedRunId = null;
                    currentTab = "runs";
                    buildUi();
                    statusText.setText(isEnglish() ? "Joined shared space. Refreshing..." : "已加入共享空间，正在刷新...");
                    refreshDevices();
                    refreshRuns();
                });
            } catch (Exception e) {
                handler.post(() -> statusText.setText(syncSpaceFailureMessage(e)));
            }
        });
    }

    private String buildSyncSpaceUrl(String server, String code, String shareToken) {
        return buildSyncSpaceUrl(server, code, shareToken, null);
    }

    private String buildSyncSpaceUrl(String server, String code, String shareToken, String deviceId) {
        Uri.Builder builder = new Uri.Builder()
                .scheme("haoleme")
                .authority("space")
                .appendQueryParameter("server", normalizeServerUrl(server))
                .appendQueryParameter("code", code == null ? "" : code);
        if (shareToken != null && !shareToken.trim().isEmpty()) {
            builder.appendQueryParameter("share", shareToken.trim());
        }
        if (deviceId != null && !deviceId.trim().isEmpty() && !"all".equals(deviceId)) {
            builder.appendQueryParameter("deviceId", deviceId.trim());
        }
        return builder.build().toString();
    }

    private Bitmap qrBitmap(String text, int size) throws Exception {
        BitMatrix matrix = new MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, size, size);
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        int dark = Color.BLACK;
        int light = Color.WHITE;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                bitmap.setPixel(x, y, matrix.get(x, y) ? dark : light);
            }
        }
        return bitmap;
    }

    private void showWhatsNewDialog() {
        String latestName = prefs.getString("latest_version_name", "");
        String notes = prefs.getString("latest_update_notes", "");
        String version = latestName == null || latestName.trim().isEmpty() ? currentVersionName() : latestName.trim();
        if (notes == null || notes.trim().isEmpty()) {
            String cli = prefs.getString("latest_cli_version", "");
            String cliPart = cli.isEmpty() ? "" : " (CLI " + cli + ")";
            notes = appDisplayName() + " " + currentVersionName() + cliPart + (isEnglish()
                    ? "\n\n- Improved command monitoring UI.\n- Better device and update experience."
                    : "\n\n- 改进命令监控界面。\n- 优化设备和更新体验。");
        }
        AlertDialog d = dialogBuilder()
                .setTitle(t("whats_new"))
                .setMessage((isEnglish() ? "Version " : "版本 ") + version + "\n\n" + notes.trim())
                .setPositiveButton(t("ok"), null)
                .create();
        applyDialogStyle(d);
        d.show();
    }

    private void showDonationSheet() {
        AlertDialog d = dialogBuilder()
                .setTitle(t("donation"))
                .setMessage(t("donation_public_hint"))
                .setPositiveButton(t("ok"), null)
                .create();
        applyDialogStyle(d);
        d.show();
    }

    private void openExternalUrl(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            statusText.setText(isEnglish() ? "No app found for this link." : "没有找到可打开此链接的应用。");
        } catch (Exception e) {
            statusText.setText((isEnglish() ? "Cannot open link: " : "无法打开链接：") + e.getMessage());
        }
    }

    private ImageView appIconView() {
        ImageView icon = new ImageView(this);
        icon.setImageResource(isDarkTheme() ? R.drawable.haoleme_icon_dark : R.drawable.haoleme_icon_light);
        icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        icon.setAdjustViewBounds(false);
        icon.setBackground(roundedBg(cardBg(), 14, cardStroke()));
        icon.setElevation(0);
        return icon;
    }

    private String screenTitle() {
        if ("settings".equals(currentTab)) {
            return settingsSection == null ? t("settings") : settingsSectionTitle(settingsSection);
        }
        return isEnglish() ? "Home" : "主页";
    }

    private String appDisplayName() {
        return isEnglish() ? "Haoleme" : "好了么";
    }

    private String languageMode() {
        if (prefs == null) {
            return LANG_ZH;
        }
        String mode = prefs.getString(PREF_LANGUAGE_MODE, LANG_ZH);
        return LANG_EN.equals(mode) ? LANG_EN : LANG_ZH;
    }

    private boolean isEnglish() {
        return LANG_EN.equals(languageMode());
    }

    private String languageLabel() {
        return isEnglish() ? "English" : "中文";
    }

    private int consoleHistoryLimit() {
        int saved = prefs == null ? 300000 : prefs.getInt(PREF_CONSOLE_HISTORY_CHARS, 300000);
        if (saved <= 30000) {
            return 30000;
        }
        if (saved <= 100000) {
            return 100000;
        }
        if (saved <= 300000) {
            return 300000;
        }
        return 1000000;
    }

    private String consoleHistoryLabel() {
        int limit = consoleHistoryLimit();
        if (limit >= 1000000) {
            return "1M chars";
        }
        return (limit / 1000) + "k chars";
    }

    private String syncSpaceLabel() {
        String spaceId = prefs == null ? "" : prefs.getString("space_id", "");
        if (spaceId == null || spaceId.trim().isEmpty()) {
            return isEnglish() ? "Local" : "本机";
        }
        return spaceId.trim();
    }

    private void updateLauncherAlias() {
        if (prefs == null) {
            return;
        }
        PackageManager manager = getPackageManager();
        boolean english = isEnglish();
        setAliasEnabled(manager, "com.haoleme.app.MainActivityZh", !english);
        setAliasEnabled(manager, "com.haoleme.app.MainActivityEn", english);
    }

    private void setAliasEnabled(PackageManager manager, String className, boolean enabled) {
        manager.setComponentEnabledSetting(
                new ComponentName(this, className),
                enabled ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
        );
    }

    private String themeMode() {
        if (prefs == null) {
            return THEME_LIGHT;
        }
        String mode = prefs.getString(PREF_THEME_MODE, THEME_LIGHT);
        return THEME_DARK.equals(mode) ? THEME_DARK : THEME_LIGHT;
    }

    private boolean isDarkTheme() {
        return THEME_DARK.equals(themeMode());
    }

    private String themeLabel() {
        return isDarkTheme() ? t("dark") : t("light");
    }

    private String onOff(boolean enabled) {
        return enabled ? t("on") : t("off");
    }

    private String t(String key) {
        boolean en = isEnglish();
        switch (key) {
            case "runs": return en ? "Runs" : "运行";
            case "devices": return en ? "Devices" : "设备";
            case "settings": return en ? "Settings" : "设置";
            case "connecting": return en ? "Connecting..." : "正在连接...";
            case "refresh": return en ? "Refresh" : "刷新";
            case "project": return en ? "Project" : "项目";
            case "status": return en ? "Status" : "状态";
            case "device": return en ? "Device" : "设备";
            case "all": return en ? "All" : "全部";
            case "no_project": return en ? "No Project" : "无项目";
            case "running": return en ? "Running" : "运行中";
            case "failed": return en ? "Failed" : "失败";
            case "succeeded": return en ? "Succeeded" : "成功";
            case "cancelled": return en ? "Cancelled" : "已取消";
            case "unknown": return en ? "Unknown" : "未知";
            case "pair_this_phone": return en ? "Pair this phone" : "配对这台手机";
            case "pair_onboarding_subtitle": return en ? "Install: pip install -U haoleme\nThen run: hao login" : "安装：pip install -U haoleme\n然后运行：hao login";
            case "scan_pair_qr": return en ? "Scan Pair QR" : "扫码配对";
            case "enter_code": return en ? "Enter 6-digit Code" : "输入 6 位配对码";
            case "pair_device": return en ? "Pair Device" : "配对设备";
            case "scan_qr_auth": return en ? "Scan QR code to authenticate" : "扫描二维码认证";
            case "scan_qr_subtitle": return en ? "Scan the QR code from hao login" : "扫描 hao login 生成的二维码";
            case "six_digit_code": return en ? "6-digit code" : "6 位配对码";
            case "pair": return en ? "Pair" : "配对";
            case "or_enter_code": return en ? "Or enter 6-digit code" : "也可以输入 6 位配对码";
            case "code_instead_qr": return en ? "Use this instead of scanning QR." : "这是扫码之外的另一种配对方式。";
            case "sync_space": return en ? "Shared Space" : "共享空间";
            case "sync_space_subtitle": return en ? "Share command run status across multiple apps" : "多个 App 共享同一个空间的命令运行状态";
            case "share_sync_space": return en ? "Share This Space" : "分享此空间";
            case "join_sync_space": return en ? "Join Shared Space" : "加入共享空间";
            case "sync_space_code": return en ? "Shared space code" : "共享空间码";
            case "appearance": return en ? "Appearance" : "外观";
            case "theme": return en ? "Theme" : "主题";
            case "theme_subtitle": return en ? "Choose light or dark mode" : "选择浅色或深色模式";
            case "language": return en ? "Language" : "语言";
            case "language_subtitle": return en ? "Switch app language and launcher name" : "切换应用语言和桌面名称";
            case "language_updated": return en ? "Language updated." : "语言已更新。";
            case "notifications": return en ? "Notifications" : "通知";
            case "succeeded_runs": return en ? "Succeeded Runs" : "成功运行";
            case "succeeded_runs_subtitle": return en ? "Notify when a command exits 0" : "命令以 0 退出时通知";
            case "failed_runs": return en ? "Failed Runs" : "失败运行";
            case "failed_runs_subtitle": return en ? "Notify when a command fails or is cancelled" : "命令失败或取消时通知";
            case "minimum_run_time": return en ? "Minimum Run Time" : "最短运行时间";
            case "minimum_run_time_subtitle": return en ? "Skip notifications for short commands" : "短命令不发送通知";
            case "quiet_hours": return en ? "Quiet Hours" : "勿扰时段";
            case "quiet_hours_subtitle": return en ? "Silence notifications from 22:00 to 08:00" : "22:00 到 08:00 静默通知";
            case "security": return en ? "Security" : "安全";
            case "mask_sensitive": return en ? "Mask Sensitive Text" : "隐藏敏感文本";
            case "mask_sensitive_subtitle": return en ? "Hide tokens, passwords and API keys in UI" : "在界面中隐藏 token、密码和 API key";
            case "device_security": return en ? "Device Security" : "设备安全";
            case "device_security_subtitle": return en ? "Review paired devices and revoke old access" : "检查配对设备并撤销旧访问";
            case "storage": return en ? "Storage" : "存储";
            case "saved_data": return en ? "Saved Data" : "已保存数据";
            case "saved_data_subtitle": return en ? "Local runs, consoles, devices and update state" : "本地运行、控制台、设备和更新状态";
            case "clear_local_cache": return en ? "Clear Local Cache" : "清理本地缓存";
            case "clear_local_cache_subtitle": return en ? "Remove saved runs and consoles on this phone" : "删除手机上的运行和控制台缓存";
            case "clear_completed_runs": return en ? "Clear Completed Runs" : "清理已完成运行";
            case "clear_completed_runs_subtitle": return en ? "Remove succeeded, failed and cancelled runs locally" : "本地删除成功、失败和取消的记录";
            case "console_history": return en ? "Console History" : "控制台历史";
            case "console_history_subtitle": return en ? "Each run keeps the latest console output" : "每条运行保留最新控制台输出";
            case "export_runs": return en ? "Export Runs" : "导出运行记录";
            case "export_runs_subtitle": return en ? "Share saved runs as JSON" : "将已保存运行记录导出为 JSON";
            case "clear_cloud_runs": return en ? "Clear Cloud Runs" : "清空云端运行";
            case "clear_cloud_runs_subtitle": return en ? "Delete all cloud run history in this space" : "删除当前空间的全部云端运行历史";
            case "delete_sync_space": return en ? "Delete Shared Space" : "删除共享空间";
            case "delete_sync_space_subtitle": return en ? "Remove cloud data and pairing for this space" : "删除云端数据并移除当前配对";
            case "support": return en ? "Support" : "支持";
            case "donation": return en ? "Donation" : "打赏";
            case "donation_subtitle": return en ? "Support project development" : "支持项目继续开发";
            case "donation_public_hint": return en ? "Donation QR codes are not bundled in the public source build." : "公开源码版不内置个人收款码。";
            case "app": return en ? "App" : "应用";
            case "auto_check_updates": return en ? "Auto Check Updates" : "自动检查更新";
            case "auto_check_updates_subtitle": return en ? "Check quietly when the app opens" : "打开应用时后台检查";
            case "wifi_only_downloads": return en ? "Wi-Fi Only Downloads" : "仅 Wi-Fi 下载";
            case "wifi_only_downloads_subtitle": return en ? "Avoid downloading APK updates on mobile data" : "避免用移动数据下载 APK 更新";
            case "update": return en ? "Update" : "更新";
            case "whats_new": return en ? "What's New" : "更新内容";
            case "whats_new_subtitle": return en ? "See the latest improvements" : "查看最新改进";
            case "version": return en ? "Version" : "版本";
            case "diagnostics": return en ? "Diagnostics & Feedback" : "诊断与反馈";
            case "diagnostics_subtitle": return en ? "Open GitHub issues (diagnostics copied)" : "打开 GitHub Issues（已复制诊断信息）";
            case "feedback": return en ? "Feedback" : "意见反馈";
            case "feedback_hint": return en ? "Describe the problem or suggestion..." : "写下你遇到的问题或建议...";
            case "send_feedback": return en ? "Send Feedback" : "发送反馈";
            case "diagnostics_copied": return en ? "Diagnostics copied." : "诊断信息已复制。";
            case "github": return "GitHub";
            case "github_subtitle": return "HaolemeApp/Haoleme";
            case "light": return en ? "Light" : "浅色";
            case "dark": return en ? "Dark" : "深色";
            case "on": return en ? "On" : "开";
            case "off": return en ? "Off" : "关";
            case "cancel": return en ? "Cancel" : "取消";
            case "ok": return en ? "OK" : "确定";
            case "close": return en ? "Close" : "关闭";
            case "clear": return en ? "Clear" : "清理";
            case "delete": return en ? "Delete" : "删除";
            case "console": return en ? "Console" : "控制台";
            case "back": return en ? "Back" : "返回";
            case "search": return en ? "Search" : "搜索";
            case "copy": return en ? "Copy" : "复制";
            case "more": return en ? "More" : "更多";
            case "auto_on": return en ? "Auto On" : "自动滚动开";
            case "auto_off": return en ? "Auto Off" : "自动滚动关";
            case "interrupt": return en ? "Interrupt" : "中断";
            case "interrupt_confirm": return en ? "Stop this running command on the linked computer?" : "确定要在电脑上停止这条正在运行的命令吗？";
            default: return key;
        }
    }

    private boolean notifySuccessEnabled() {
        return prefs == null || prefs.getBoolean(PREF_NOTIFY_SUCCESS, true);
    }

    private boolean notifyFailureEnabled() {
        return prefs == null || prefs.getBoolean(PREF_NOTIFY_FAILURE, true);
    }

    private boolean quietHoursEnabled() {
        return prefs != null && prefs.getBoolean(PREF_NOTIFY_QUIET_HOURS, false);
    }

    private boolean autoCheckUpdatesEnabled() {
        return prefs == null || prefs.getBoolean(PREF_UPDATE_AUTO_CHECK, true);
    }

    private boolean wifiOnlyUpdatesEnabled() {
        return prefs != null && prefs.getBoolean(PREF_UPDATE_WIFI_ONLY, false);
    }

    private boolean maskSensitiveEnabled() {
        return prefs == null || prefs.getBoolean(PREF_MASK_SENSITIVE, true);
    }

    private void togglePreference(String key, boolean defaultValue, View row) {
        boolean next = !prefs.getBoolean(key, defaultValue);
        prefs.edit().putBoolean(key, next).apply();
        updateSettingsRowValue(row, onOff(next));
        statusText.setText(next ? "Enabled." : "Disabled.");
    }

    private void updateSettingsRowValue(View row, String value) {
        if (!(row instanceof LinearLayout)) {
            return;
        }
        TextView valueView = findSettingsValueView((LinearLayout) row);
        if (valueView != null) {
            valueView.setText(value == null ? "" : value);
        }
    }

    private TextView findSettingsValueView(LinearLayout row) {
        for (int i = 0; i < row.getChildCount(); i++) {
            View child = row.getChildAt(i);
            if (child instanceof TextView && "settings_value".equals(child.getTag())) {
                return (TextView) child;
            }
        }
        return null;
    }

    private int notifyMinSeconds() {
        return prefs == null ? 0 : prefs.getInt(PREF_NOTIFY_MIN_SECONDS, 0);
    }

    private String notifyMinDurationLabel() {
        int seconds = notifyMinSeconds();
        if (seconds <= 0) {
            return isEnglish() ? "Any" : "任意";
        }
        if (seconds < 60) {
            return seconds + "s";
        }
        int minutes = seconds / 60;
        if (minutes < 60) {
            return minutes + " min";
        }
        return (minutes / 60) + (isEnglish() ? " hour" : " 小时");
    }

    private void showNotifyMinDurationDialog(View row) {
        String[] labels = new String[]{isEnglish() ? "Any" : "任意", isEnglish() ? "1 min" : "1 分钟", isEnglish() ? "5 min" : "5 分钟", isEnglish() ? "15 min" : "15 分钟", isEnglish() ? "1 hour" : "1 小时"};
        int[] values = new int[]{0, 60, 300, 900, 3600};
        int current = notifyMinSeconds();
        int selected = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == current) {
                selected = i;
                break;
            }
        }
        AlertDialog d = dialogBuilder()
                .setTitle(t("minimum_run_time"))
                .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                    prefs.edit().putInt(PREF_NOTIFY_MIN_SECONDS, values[which]).apply();
                    dialog.dismiss();
                    updateSettingsRowValue(row, labels[which]);
                    statusText.setText(isEnglish() ? "Notification filter updated." : "通知过滤已更新。");
                })
                .setNegativeButton(t("cancel"), null)
                .create();
        applyDialogStyle(d);
        d.show();
    }

    private String localCacheSizeLabel() {
        long bytes = localCacheBytes();
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format(Locale.US, "%.1f KB", kb);
        }
        return String.format(Locale.US, "%.1f MB", kb / 1024.0);
    }

    private long localCacheBytes() {
        if (prefs == null) {
            return 0L;
        }
        long total = 0L;
        Map<String, ?> values = prefs.getAll();
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            String key = entry.getKey();
            if (!isLocalCacheKey(key)) {
                continue;
            }
            total += key.getBytes(StandardCharsets.UTF_8).length;
            Object value = entry.getValue();
            if (value instanceof String) {
                total += ((String) value).getBytes(StandardCharsets.UTF_8).length;
            } else {
                total += 8;
            }
        }
        return total;
    }

    private boolean isLocalCacheKey(String key) {
        return CACHE_RUNS.equals(key)
                || CACHE_RUNS_AT.equals(key)
                || CACHE_DEVICES.equals(key)
                || key.startsWith(CACHE_RUNS_PREFIX)
                || key.startsWith(CACHE_RUNS_AT_PREFIX)
                || key.startsWith(CACHE_RUN_PREFIX)
                || key.startsWith("notified_terminal_")
                || key.startsWith("latest_");
    }

    private void confirmClearLocalCache() {
        AlertDialog d = dialogBuilder()
                .setTitle(isEnglish() ? "Clear local cache" : "清理本地缓存")
                .setMessage(isEnglish() ? "Remove saved runs, consoles, device cache and update state from this phone? Cloud history stays unchanged." : "删除这台手机上保存的运行、控制台、设备缓存和更新状态？云端历史不会受影响。")
                .setNegativeButton(t("cancel"), null)
                .setPositiveButton(t("clear"), (dialog, which) -> {
                    int removed = clearLocalCache();
                    buildUi();
                    statusText.setText(isEnglish() ? "Cleared " + removed + " local cache item(s)." : "已清理 " + removed + " 项本地缓存。");
                    refreshDevices();
                    refreshRuns();
                })
                .create();
        applyDialogStyle(d);
        d.show();
    }

    private int clearLocalCache() {
        int removed = 0;
        SharedPreferences.Editor editor = prefs.edit();
        Map<String, ?> values = prefs.getAll();
        for (String key : values.keySet()) {
            if (isLocalCacheKey(key)) {
                editor.remove(key);
                removed++;
            }
        }
        editor.apply();
        knownStatuses.clear();
        return removed;
    }

    private void confirmClearCompletedLocalRuns() {
        AlertDialog d = dialogBuilder()
                .setTitle(isEnglish() ? "Clear completed runs" : "清理已完成运行")
                .setMessage(isEnglish() ? "Remove completed runs from this phone's saved history? Running runs and cloud history stay unchanged." : "从这台手机的保存历史中删除已完成运行？运行中的记录和云端历史不会受影响。")
                .setNegativeButton(t("cancel"), null)
                .setPositiveButton(t("clear"), (dialog, which) -> {
                    int removed = clearCompletedLocalRuns();
                    buildUi();
                    statusText.setText(isEnglish() ? "Removed " + removed + " completed local run(s)." : "已删除 " + removed + " 条本地已完成运行。");
                    refreshRuns();
                })
                .create();
        applyDialogStyle(d);
        d.show();
    }

    private int clearCompletedLocalRuns() {
        SharedPreferences.Editor editor = prefs.edit();
        Map<String, ?> values = prefs.getAll();
        Set<String> removedIds = new HashSet<>();
        long now = System.currentTimeMillis();
        for (String key : values.keySet()) {
            if (!CACHE_RUNS.equals(key) && !key.startsWith(CACHE_RUNS_PREFIX)) {
                continue;
            }
            Object rawValue = values.get(key);
            if (!(rawValue instanceof String)) {
                continue;
            }
            JSONArray updated = removeCompletedRunsFromJsonArray((String) rawValue, removedIds);
            if (updated == null) {
                continue;
            }
            editor.putString(key, updated.toString());
            String atKey = cacheAtKeyForRunsKey(key);
            if (!atKey.isEmpty()) {
                editor.putLong(atKey, now);
            }
        }
        for (String id : removedIds) {
            editor.remove(CACHE_RUN_PREFIX + id).remove("notified_terminal_" + id);
            knownStatuses.remove(id);
        }
        editor.apply();
        return removedIds.size();
    }

    private JSONArray removeCompletedRunsFromJsonArray(String raw, Set<String> removedIds) {
        try {
            JSONArray original = new JSONArray(raw);
            JSONArray kept = new JSONArray();
            boolean removed = false;
            for (int i = 0; i < original.length(); i++) {
                JSONObject run = original.optJSONObject(i);
                if (run != null && isCompletedStatus(run.optString("status", ""))) {
                    removed = true;
                    String id = run.optString("id", "");
                    if (!id.isEmpty()) {
                        removedIds.add(id);
                    }
                    continue;
                }
                kept.put(original.get(i));
            }
            return removed ? kept : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean isCompletedStatus(String status) {
        return "succeeded".equals(status) || "failed".equals(status) || "cancelled".equals(status);
    }

    private JSONArray bestCachedRunsForExport() {
        String raw = prefs.getString(runsCacheKey(), "");
        if (raw == null || raw.trim().isEmpty()) {
            raw = prefs.getString(CACHE_RUNS, "");
        }
        if (raw == null || raw.trim().isEmpty()) {
            return new JSONArray();
        }
        try {
            return new JSONArray(raw);
        } catch (Exception ignored) {
            return new JSONArray();
        }
    }

    private void exportRuns() {
        try {
            JSONArray runs = bestCachedRunsForExport();
            JSONObject export = new JSONObject();
            export.put("app", "Haoleme");
            export.put("exportedAt", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(new Date()));
            export.put("server", normalizedServerUrl());
            export.put("runs", runs);
            String json = export.toString(2);

            File exportsDir = new File(getCacheDir(), "exports");
            if (!exportsDir.exists()) {
                exportsDir.mkdirs();
            }
            String ts = new SimpleDateFormat("yyyy-MM-dd'T'HH-mm-ss'Z'", Locale.US).format(new Date());
            File exportFile = new File(exportsDir, "haoleme-runs-" + ts + ".json");
            try (FileOutputStream fos = new FileOutputStream(exportFile)) {
                fos.write(json.getBytes(StandardCharsets.UTF_8));
            }

            Uri uri = FileProvider.getUriForFile(
                    this,
                    BuildConfig.APPLICATION_ID + ".fileprovider",
                    exportFile
            );

            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("application/json");
            intent.putExtra(Intent.EXTRA_SUBJECT, "Haoleme runs export");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, isEnglish() ? "Export runs" : "导出运行记录"));
            statusText.setText(isEnglish() ? "Choose an app to receive the export." : "请选择应用接收导出文件。");
        } catch (Exception e) {
            statusText.setText((isEnglish() ? "Export failed: " : "导出失败：") + e.getMessage());
        }
    }

    private void confirmClearCloudRuns() {
        AlertDialog d = dialogBuilder()
                .setTitle(t("clear_cloud_runs"))
                .setMessage(isEnglish()
                        ? "Delete all run history from the cloud for this shared space? Paired devices stay connected."
                        : "删除当前共享空间里的全部云端运行历史？已配对设备会继续保留。")
                .setNegativeButton(t("cancel"), null)
                .setPositiveButton(t("delete"), (dialog, which) -> clearCloudRuns())
                .create();
        applyDialogStyle(d);
        d.show();
    }

    private void clearCloudRuns() {
        statusText.setText(isEnglish() ? "Deleting cloud runs..." : "正在删除云端运行记录...");
        updateExecutor.submit(() -> {
            try {
                httpRequest(normalizedServerUrl() + "/api/runs", "DELETE");
                clearRunCachesOnly();
                handler.post(() -> {
                    selectedRunId = null;
                    buildUi();
                    statusText.setText(isEnglish() ? "Cloud run history deleted." : "云端运行历史已删除。");
                    refreshRuns();
                    refreshDevices();
                });
            } catch (Exception e) {
                handler.post(() -> statusText.setText((isEnglish() ? "Delete cloud runs failed: " : "删除云端运行失败：") + e.getMessage()));
            }
        });
    }

    private void clearRunCachesOnly() {
        SharedPreferences.Editor editor = prefs.edit();
        Map<String, ?> values = prefs.getAll();
        for (String key : values.keySet()) {
            if (CACHE_RUNS.equals(key)
                    || CACHE_RUNS_AT.equals(key)
                    || key.startsWith(CACHE_RUNS_PREFIX)
                    || key.startsWith(CACHE_RUNS_AT_PREFIX)
                    || key.startsWith(CACHE_RUN_PREFIX)
                    || key.startsWith("notified_terminal_")) {
                editor.remove(key);
            }
        }
        editor.apply();
        knownStatuses.clear();
    }

    private void confirmDeleteSyncSpace() {
        AlertDialog d = dialogBuilder()
                .setTitle(t("delete_sync_space"))
                .setMessage(isEnglish()
                        ? "Delete this shared space from the cloud and remove pairing from this phone? Other devices will need to pair again."
                        : "从云端删除当前共享空间，并移除这台手机上的配对？其他设备需要重新配对。")
                .setNegativeButton(t("cancel"), null)
                .setPositiveButton(t("delete"), (dialog, which) -> deleteSyncSpace())
                .create();
        applyDialogStyle(d);
        d.show();
    }

    private void deleteSyncSpace() {
        statusText.setText(isEnglish() ? "Deleting shared space..." : "正在删除共享空间...");
        executor.submit(() -> {
            try {
                httpRequest(normalizedServerUrl() + "/api/account", "DELETE");
                handler.post(() -> {
                    clearAllPairingAndCache();
                    buildUi();
                    statusText.setText(isEnglish() ? "Shared space deleted. Pair again to continue." : "共享空间已删除。请重新配对后继续使用。");
                });
            } catch (Exception e) {
                handler.post(() -> statusText.setText((isEnglish() ? "Delete shared space failed: " : "删除共享空间失败：") + e.getMessage()));
            }
        });
    }

    private void clearAllPairingAndCache() {
        SharedPreferences.Editor editor = prefs.edit();
        Map<String, ?> values = prefs.getAll();
        for (String key : values.keySet()) {
            if (isLocalCacheKey(key)
                    || "token".equals(key)
                    || "encryption_key_b64".equals(key)
                    || "paired_device_id".equals(key)
                    || "paired_device_name".equals(key)
                    || "paired_account".equals(key)
                    || "paired_at".equals(key)
                    || "paired_server_url".equals(key)
                    || "space_id".equals(key)
                    || "space_joined_at".equals(key)
                    || "selected_device_id".equals(key)) {
                editor.remove(key);
            }
        }
        editor.apply();
        knownStatuses.clear();
        selectedDeviceId = "all";
        selectedRunId = null;
    }

    private String deviceSecurityLabel() {
        JSONArray devices = cachedDevicesArray();
        int count = devices.length();
        int online = 0;
        for (int i = 0; i < devices.length(); i++) {
            JSONObject device = devices.optJSONObject(i);
            if (device != null && device.optBoolean("online", false)) {
                online++;
            }
        }
        if (count == 0) {
            return isEnglish() ? "No devices" : "无设备";
        }
        return isEnglish() ? online + " online / " + count + " total" : online + " 在线 / 共 " + count;
    }

    private JSONArray cachedDevicesArray() {
        String cached = prefs == null ? "" : prefs.getString(CACHE_DEVICES, "");
        if (cached == null || cached.trim().isEmpty()) {
            return new JSONArray();
        }
        try {
            return new JSONArray(cached);
        } catch (Exception ignored) {
            return new JSONArray();
        }
    }

    private void showDeviceSecurityDialog() {
        List<JSONObject> devices = cachedDeviceList();
        if (devices.isEmpty()) {
            AlertDialog d = dialogBuilder()
                    .setTitle(t("device_security"))
                    .setMessage(isEnglish() ? "No saved devices yet. Refresh Devices first." : "还没有保存的设备。请先刷新设备。")
                    .setNegativeButton(t("close"), null)
                    .setPositiveButton(t("refresh"), (dialog, which) -> refreshDevices())
                    .create();
            applyDialogStyle(d);
            d.show();
            return;
        }

        String current = prefs.getString("paired_device_id", "");
        String[] labels = new String[devices.size()];
        boolean[] checked = new boolean[devices.size()];
        for (int i = 0; i < devices.size(); i++) {
            JSONObject device = devices.get(i);
            String id = device.optString("id", "").trim();
            String name = device.optString("name", "Device");
            String online = device.optBoolean("online", false) ? (isEnglish() ? "online" : "在线") : (isEnglish() ? "offline" : "离线");
            String lastSeen = device.optString("lastSeenAt", "");
            String suffix = id.equals(current) ? (isEnglish() ? " · current" : " · 当前") : "";
            String seenLabel = formatDeviceTimestamp(lastSeen);
            labels[i] = name + " · " + online + suffix + (seenLabel.isEmpty() ? "" : "\n" + (isEnglish() ? "Last seen: " : "最后在线：") + seenLabel);
        }

        AlertDialog d = dialogBuilder()
                .setTitle(t("device_security"))
                .setMultiChoiceItems(labels, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
                .setNegativeButton(t("close"), null)
                .setNeutralButton(t("refresh"), (dialog, which) -> refreshDevices())
                .setPositiveButton(isEnglish() ? "Disconnect Selected" : "断联选中设备", (dialog, which) -> {
                    List<JSONObject> selected = new ArrayList<>();
                    for (int i = 0; i < checked.length; i++) {
                        if (checked[i]) {
                            selected.add(devices.get(i));
                        }
                    }
                    confirmRevokeDevices(selected);
                })
                .create();
        applyDialogStyle(d);
        d.show();
    }

    private List<JSONObject> cachedDeviceList() {
        JSONArray devices = cachedDevicesArray();
        List<JSONObject> list = new ArrayList<>();
        for (int i = 0; i < devices.length(); i++) {
            JSONObject device = devices.optJSONObject(i);
            if (device == null) {
                continue;
            }
            String id = device.optString("id", "").trim();
            if (!id.isEmpty()) {
                list.add(device);
            }
        }
        return list;
    }

    private void confirmRevokeDevices(List<JSONObject> targets) {
        if (targets.isEmpty()) {
            statusText.setText(isEnglish() ? "Choose at least one device." : "请至少选择一台设备。");
            return;
        }
        String names = deviceNamesSummary(targets);
        AlertDialog d = dialogBuilder()
                .setTitle(isEnglish() ? "Disconnect device" : "断联设备")
                .setMessage(isEnglish()
                        ? "Stop these device(s) from uploading new runs?\n\n" + names
                        : "阻止这些设备继续上传新的运行记录？\n\n" + names)
                .setNegativeButton(t("cancel"), null)
                .setPositiveButton(isEnglish() ? "Disconnect" : "断联", (dialog, which) -> revokeDevices(targets))
                .create();
        applyDialogStyle(d);
        d.show();
    }

    private String deviceNamesSummary(List<JSONObject> devices) {
        StringBuilder builder = new StringBuilder();
        for (JSONObject device : devices) {
            String name = device.optString("name", "").trim();
            if (name.isEmpty()) {
                name = device.optString("id", "Device");
            }
            if (builder.length() > 0) {
                builder.append("\n");
            }
            builder.append("• ").append(name);
        }
        return builder.toString();
    }

    private void revokeDevices(List<JSONObject> devices) {
        statusText.setText(isEnglish() ? "Disconnecting device(s)..." : "正在断联设备...");
        executor.submit(() -> {
            int revoked = 0;
            Set<String> revokedIds = new HashSet<>();
            for (JSONObject device : devices) {
                String id = device.optString("id", "").trim();
                if (id.isEmpty()) {
                    continue;
                }
                try {
                    httpRequest(normalizedServerUrl() + "/api/devices/" + Uri.encode(id), "DELETE");
                    revoked++;
                    revokedIds.add(id);
                } catch (Exception ignored) {
                }
            }
            int finalRevoked = revoked;
            handler.post(() -> {
                if (revokedIds.contains(selectedDeviceId)) {
                    selectedDeviceId = "all";
                    prefs.edit().putString("selected_device_id", selectedDeviceId).apply();
                }
                statusText.setText(isEnglish() ? "Disconnected " + finalRevoked + " device(s)." : "已断联 " + finalRevoked + " 台设备。");
                refreshDevices();
                refreshRuns();
            });
        });
    }

    private void copyText(String label, String text) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText(label, text == null ? "" : text));
        }
    }

    private String diagnosticsText() {
        return diagnosticsText("");
    }

    private String diagnosticsText(String feedback) {
        StringBuilder text = new StringBuilder();
        String cleanFeedback = feedback == null ? "" : feedback.trim();
        if (!cleanFeedback.isEmpty()) {
            text.append("Feedback:\n").append(cleanFeedback).append("\n\n");
        }
        text.append(appDisplayName()).append(" diagnostics\n");
        text.append("Version: ").append(currentVersionName()).append(" (").append(currentVersionCode()).append(")\n");
        String latestCli = prefs.getString("latest_cli_version", "");
        if (!latestCli.isEmpty()) {
            text.append("CLI: ").append(latestCli).append("\n");
        }
        text.append("Server: ").append(normalizedServerUrl()).append("\n");
        text.append("Account: ").append(prefs.getString("paired_account", "")).append("\n");
        text.append("Device: ").append(prefs.getString("paired_device_name", "")).append("\n");
        text.append("Selected device: ").append(selectedDeviceId).append("\n");
        text.append("Project filter: ").append(selectedProjectFilter).append("\n");
        text.append("Status filter: ").append(selectedStatusFilter).append("\n");
        text.append("Devices: ").append(deviceSecurityLabel()).append("\n");
        text.append("Local cache: ").append(localCacheSizeLabel()).append("\n");
        text.append("Theme: ").append(themeLabel()).append("\n");
        text.append("Language: ").append(languageLabel()).append("\n");
        text.append("Notifications: success=").append(notifySuccessEnabled())
                .append(", failure=").append(notifyFailureEnabled())
                .append(", min=").append(notifyMinDurationLabel())
                .append(", quiet=").append(quietHoursEnabled()).append("\n");
        text.append("Updates: auto=").append(autoCheckUpdatesEnabled())
                .append(", wifiOnly=").append(wifiOnlyUpdatesEnabled())
                .append(", latest=").append(prefs.getString("latest_version_name", "")).append("\n");
        return text.toString();
    }

    private String displayText(String raw) {
        String value = raw == null ? "" : raw;
        return maskSensitiveEnabled() ? maskSensitive(value) : value;
    }

    private String maskSensitive(String raw) {
        String masked = raw == null ? "" : raw;
        masked = masked.replaceAll("(?i)(password|passwd|pwd|token|api[_-]?key|secret|access[_-]?key|authorization)(\\s*[:=]\\s*)([^\\s'\"&]+)", "$1$2••••");
        masked = masked.replaceAll("(?i)(--(?:password|passwd|pwd|token|api-key|api_key|secret|access-key|access_key)\\s+)(\\S+)", "$1••••");
        masked = masked.replaceAll("(?i)(Bearer\\s+)[A-Za-z0-9._~+/=-]+", "$1••••");
        return masked;
    }

    private String notificationSummary(JSONObject run, String command, String status) {
        if (!"failed".equals(status) && !"cancelled".equals(status)) {
            return command;
        }
        String latest = latestOutputLine(run);
        if (latest.isEmpty()) {
            return command;
        }
        return trim(command + " · " + displayText(latest));
    }

    private CharSequence actionLabel(String icon, String label, float iconScale) {
        String cleanLabel = label == null ? "" : label.trim();
        String text = cleanLabel.isEmpty() ? icon : icon + " " + cleanLabel;
        SpannableString span = new SpannableString(text);
        span.setSpan(new RelativeSizeSpan(iconScale), 0, icon.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        span.setSpan(new StyleSpan(Typeface.BOLD), 0, icon.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return span;
    }

    private TextView actionButton(CharSequence label) {
        TextView button = new TextView(this);
        button.setText(label);
        button.setTextSize(14);
        button.setTypeface(null, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setTextColor(textPrimary());
        button.setMinHeight(dp(42));
        button.setPadding(dp(10), 0, dp(10), 0);
        button.setBackground(roundedBg(buttonBg(), 10, surfaceStroke()));
        button.setClickable(true);
        button.setElevation(0);
        return button;
    }

    private boolean hasAvailableUpdate() {
        return prefs.getInt("latest_version_code", 0) > currentVersionCode()
                && !prefs.getString("latest_download_url", "").trim().isEmpty();
    }

    private String updateRowSubtitle() {
        if (updateDownloading) {
            return isEnglish() ? "Downloading update" : "正在下载更新";
        }
        if (hasAvailableUpdate()) {
            return isEnglish() ? "New version is ready to download" : "新版本可以下载";
        }
        return isEnglish() ? "Check for a newer Haoleme APK" : "检查新版好了么 APK";
    }

    private String updateRowValue() {
        if (hasAvailableUpdate()) {
            String latest = prefs.getString("latest_version_name", "");
            return latest == null || latest.trim().isEmpty() ? t("update") : latest.trim();
        }
        return isEnglish() ? "Check" : "检查";
    }

    private LinearLayout bottomTabs() {
        // Flat full-width bar with a top hairline (mirrors happy-app's TabBar):
        // surface background, no floating card, active tab shown by color + weight.
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.VERTICAL);
        bar.setBackgroundColor(cardBg());
        bar.setPadding(0, 0, 0, navigationBarHeight() + dp(2));

        View topLine = new View(this);
        topLine.setBackgroundColor(cardStroke());
        int hairline = Math.max(1, Math.round(getResources().getDisplayMetrics().density * 0.7f));
        bar.addView(topLine, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, hairline));

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setPadding(dp(12), 0, dp(12), 0);
        tabs.addView(tabButton("home", "⌂", isEnglish() ? "Home" : "主页"),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        tabs.addView(tabButton("settings", "⚙", t("settings")),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        bar.addView(tabs, matchWrap());

        // Break out of the root's side/bottom padding so the bar spans edge to edge.
        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(-dp(18), dp(6), -dp(18), -(navigationBarHeight() + dp(2)));
        bar.setLayoutParams(params);
        return bar;
    }

    private LinearLayout tabButton(String tab, String icon, String label) {
        LinearLayout button = new LinearLayout(this);
        button.setOrientation(LinearLayout.VERTICAL);
        button.setGravity(Gravity.CENTER);
        button.setPadding(0, dp(8), 0, dp(5));
        // No press ripple/highlight on the bar — switching only changes the
        // icon + label color, nothing gray flashes.
        button.setClickable(true);
        boolean selected = tab.equals(currentTab);

        TextView iconView = new TextView(this);
        iconView.setText(icon);
        iconView.setTextSize(20);
        iconView.setGravity(Gravity.CENTER);
        iconView.setTypeface(null, Typeface.BOLD);
        iconView.setTextColor(selected ? textPrimary() : textSecondary());
        button.addView(iconView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextSize(10);
        labelView.setGravity(Gravity.CENTER);
        labelView.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
        labelView.setTextColor(selected ? textPrimary() : textSecondary());
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        labelParams.setMargins(0, dp(3), 0, 0);
        button.addView(labelView, labelParams);

        button.setOnClickListener(v -> {
            if (!tab.equals(currentTab)) {
                currentTab = tab;
                settingsSection = null;
                buildUi();
                if ("home".equals(currentTab)) {
                    refreshDevices();
                    refreshRuns();
                }
            }
        });
        return button;
    }

    private void refreshRuns() {
        refreshRuns(false);
    }

    private void refreshRuns(boolean manual) {
        if (manual || !hasCachedRuns()) {
            statusText.setText(isEnglish() ? "Refreshing..." : "正在刷新...");
        }
        String url = normalizedServerUrl() + "/api/runs?limit=50";
        if (selectedDeviceId != null && !selectedDeviceId.isEmpty() && !"all".equals(selectedDeviceId)) {
            url += "&deviceId=" + Uri.encode(selectedDeviceId);
        }
        if (selectedStatusFilter != null && !"all".equals(selectedStatusFilter)) {
            url += "&status=" + Uri.encode(selectedStatusFilter);
        }
        if (selectedProjectFilter != null && !"all".equals(selectedProjectFilter)) {
            url += "&project=" + Uri.encode(selectedProjectFilter);
        }
        final String requestUrl = url;
        // Capture the device selection at the time this refresh was requested.
        // Later completions for old selections will be ignored to prevent
        // showing stale device data when user switches quickly.
        final String targetDevice = (selectedDeviceId == null || "all".equals(selectedDeviceId)) ? "all" : selectedDeviceId;
        executor.submit(() -> {
            try {
                String body = httpGet(requestUrl, HTTP_LIST_READ_TIMEOUT_MS);
                final JSONArray runs = decryptRuns(new JSONObject(body).getJSONArray("runs"));
                handler.post(() -> {
                    String current = (selectedDeviceId == null || "all".equals(selectedDeviceId)) ? "all" : selectedDeviceId;
                    if (!targetDevice.equals(current)) {
                        // stale refresh for a previous device selection, ignore
                        return;
                    }
                    renderRuns(runs, false);
                });
                executor.submit(() -> saveRunsCache(runs));
            } catch (Exception e) {
                Log.w(TAG, "refreshRuns failed for " + safeRequestLabel(requestUrl), e);
                handler.post(() -> {
                    if (hasCachedRuns()) {
                        if ("home".equals(currentTab)) {
                            mergeDevicesFromCachedRuns();
                            loadCachedDevices();
                        }
                        loadCachedRuns();
                        statusText.setText(cloudFailureMessage(e) + (isEnglish() ? " Showing local cache." : " 正在显示本地缓存。"));
                    } else {
                        statusText.setText(cloudFailureMessage(e));
                    }
                });
            }
        });
    }

    private void refreshDevices() {
        refreshDevices(false);
    }

    private void refreshDevices(boolean manual) {
        final String requestUrl = normalizedServerUrl() + "/api/devices";
        executor.submit(() -> {
            try {
                String body = httpGet(requestUrl, HTTP_LIST_READ_TIMEOUT_MS);
                JSONArray devices = new JSONObject(body).getJSONArray("devices");
                prefs.edit().putString(CACHE_DEVICES, devices.toString()).apply();
                handler.post(() -> renderDevices(devices));
            } catch (Exception ignored) {
                Log.w(TAG, "refreshDevices failed for " + safeRequestLabel(requestUrl), ignored);
                handler.post(() -> {
                    mergeDevicesFromCachedRuns();
                    if ("home".equals(currentTab)) {
                        loadCachedDevices();
                        if (manual || !hasCachedRuns()) {
                            statusText.setText(cloudFailureMessage(ignored) + (isEnglish() ? " Showing saved devices." : " 正在显示已保存设备。"));
                        }
                    }
                });
            }
        });
    }

    private void loadCachedDevices() {
        String cached = prefs.getString(CACHE_DEVICES, "");
        if (cached == null || cached.isEmpty()) {
            renderDevices(new JSONArray());
            return;
        }
        try {
            renderDevices(new JSONArray(cached));
        } catch (Exception ignored) {
            renderDevices(new JSONArray());
        }
    }

    private void cachePairedDevice(String deviceId, String deviceName, String pairedAt) {
        if (deviceId == null || deviceId.trim().isEmpty()) {
            return;
        }
        String id = deviceId.trim();
        String name = deviceName == null || deviceName.trim().isEmpty() ? appDisplayName() + " device" : deviceName.trim();
        String seenAt = pairedAt == null ? "" : pairedAt.trim();
        try {
            JSONArray devices;
            String cached = prefs.getString(CACHE_DEVICES, "");
            if (cached == null || cached.isEmpty()) {
                devices = new JSONArray();
            } else {
                devices = new JSONArray(cached);
            }
            JSONArray merged = new JSONArray();
            JSONObject paired = new JSONObject();
            paired.put("id", id);
            paired.put("name", name);
            paired.put("createdAt", seenAt);
            paired.put("lastSeenAt", seenAt);
            paired.put("tokenLastUsedAt", seenAt);
            paired.put("revokedAt", "");
            paired.put("online", true);
            paired.put("onlineWindowSeconds", 90);
            merged.put(paired);
            for (int i = 0; i < devices.length(); i++) {
                JSONObject device = devices.optJSONObject(i);
                if (device == null || id.equals(device.optString("id", ""))) {
                    continue;
                }
                merged.put(device);
            }
            prefs.edit().putString(CACHE_DEVICES, merged.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    private void renderDevices(JSONArray devices) {
        int scrollX = devicesScrollView == null ? 0 : devicesScrollView.getScrollX();
        deviceNames.clear();
        deviceLastSeen.clear();
        deviceTokenLastUsed.clear();
        deviceOnline.clear();
        boolean showOffline = showOfflineDevicesEnabled();
        boolean hasSelected = "all".equals(selectedDeviceId);
        for (int i = 0; i < devices.length(); i++) {
            JSONObject device = devices.optJSONObject(i);
            if (device == null) {
                continue;
            }
            String id = device.optString("id", "");
            if (id.isEmpty()) {
                continue;
            }
            String name = device.optString("name", id);
            boolean online = device.optBoolean("online", false);
            deviceNames.put(id, name);
            deviceLastSeen.put(id, device.optString("lastSeenAt", ""));
            deviceTokenLastUsed.put(id, device.optString("tokenLastUsedAt", ""));
            deviceOnline.put(id, online);
            if (id.equals(selectedDeviceId)) {
                hasSelected = true;
            }
        }
        // Collect devices to show (before any view work) and sort by name for
        // stable ordering so buttons don't jump positions on every refresh.
        List<JSONObject> toShow = new ArrayList<>();
        for (int i = 0; i < devices.length(); i++) {
            JSONObject device = devices.optJSONObject(i);
            if (device == null) {
                continue;
            }
            String id = device.optString("id", "");
            if (id.isEmpty()) {
                continue;
            }
            boolean online = device.optBoolean("online", false);
            if (showOffline || online || id.equals(selectedDeviceId)) {
                toShow.add(device);
            }
        }
        Collections.sort(toShow, (a, b) -> {
            String na = a.optString("name", a.optString("id", "")).toLowerCase(Locale.US);
            String nb = b.optString("name", b.optString("id", "")).toLowerCase(Locale.US);
            return na.compareTo(nb);
        });
        if (!hasSelected && devices.length() > 0) {
            selectedDeviceId = "all";
            prefs.edit().putString("selected_device_id", selectedDeviceId).apply();
            scrollX = 0;
        }

        if (devicesContainer == null) {
            return;
        }

        // The chip strip depends only on id/name/online + selection + the offline
        // toggle. GPU/heartbeat values change every poll but don't affect the
        // chips, so when the strip is unchanged we skip the rebuild (no 5s flicker
        // or wasted work) and only refresh the live summary/GPU panel.
        StringBuilder sigB = new StringBuilder();
        for (JSONObject device : toShow) {
            sigB.append(device.optString("id", "")).append(':')
                .append(device.optString("name", "")).append(':')
                .append(device.optBoolean("online", false) ? '1' : '0').append('|');
        }
        sigB.append("sel=").append(selectedDeviceId).append(";off=").append(showOffline);
        String sig = sigB.toString();
        if (devicesContainer.getChildCount() > 0 && sig.equals(lastDevicesSig)) {
            updateDeviceSummary();
            updateDeviceActionButtons();
            updateConnectionSubtitle();
            return;
        }
        lastDevicesSig = sig;

        devicesContainer.removeAllViews();
        devicesContainer.addView(deviceButton("all", t("all"), false));
        for (JSONObject device : toShow) {
            String id = device.optString("id", "");
            boolean online = device.optBoolean("online", false);
            devicesContainer.addView(deviceButton(id, device.optString("name", id), online));
        }
        updateDeviceSummary();
        updateDeviceActionButtons();
        updateConnectionSubtitle();
        final int finalScrollX = scrollX;
        if (devicesScrollView != null) {
            devicesScrollView.post(() -> devicesScrollView.scrollTo(finalScrollX, 0));
        }
    }

    private void updateConnectionSubtitle() {
        if (connectionSubtitleText == null || prefs == null) {
            return;
        }
        int online = onlineDeviceCount();
        if (online > 0) {
            connectionSubtitleText.setText(isEnglish()
                    ? "● " + online + (online == 1 ? " device online" : " devices online")
                    : "● " + online + " 台设备在线");
            connectionSubtitleText.setTextColor(color("#16A34A"));
            return;
        }
        String pairedDevice = prefs.getString("paired_device_name", "");
        if (pairedDevice == null || pairedDevice.trim().isEmpty()) {
            connectionSubtitleText.setText(isEnglish() ? "● disconnected" : "● 未连接");
        } else {
            connectionSubtitleText.setText(isEnglish() ? "● 0 devices online" : "● 0 台设备在线");
        }
        connectionSubtitleText.setTextColor(textSecondary());
    }

    private int onlineDeviceCount() {
        int count = 0;
        if (!deviceOnline.isEmpty()) {
            for (Boolean online : deviceOnline.values()) {
                if (Boolean.TRUE.equals(online)) {
                    count++;
                }
            }
            return count;
        }
        try {
            String cached = prefs.getString(CACHE_DEVICES, "");
            if (cached == null || cached.isEmpty()) {
                return 0;
            }
            JSONArray devices = new JSONArray(cached);
            for (int i = 0; i < devices.length(); i++) {
                JSONObject device = devices.optJSONObject(i);
                if (device != null && device.optBoolean("online", false)) {
                    count++;
                }
            }
        } catch (Exception ignored) {
        }
        return count;
    }

    private View deviceButton(String id, String label, boolean online) {
        boolean selected = id.equals(selectedDeviceId);
        LinearLayout button = new LinearLayout(this);
        button.setOrientation(LinearLayout.HORIZONTAL);
        button.setGravity(Gravity.CENTER_VERTICAL);
        button.setMinimumHeight(dp(34));
        button.setPadding(dp(10), 0, dp(11), 0);
        button.setBackground(roundedBg(selected ? tabSelectedBg() : cardBg(), 14, selected ? tabSelectedBg() : cardStroke()));
        button.setClickable(true);
        button.setElevation(0);

        if (!"all".equals(id)) {
            View dot = statusDot(online ? color("#16A34A") : (selected ? tabSelectedText() : textSecondary()));
            LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(8), dp(8));
            dotParams.setMargins(0, 0, dp(10), 0);
            button.addView(dot, dotParams);
        }

        ComputerIconView icon = new ComputerIconView(this, selected ? tabSelectedText() : textPrimary());
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(20), dp(18));
        iconParams.setMargins(0, 0, dp(7), 0);
        button.addView(icon, iconParams);

        TextView name = new TextView(this);
        name.setText(label == null || label.trim().isEmpty() ? ("all".equals(id) ? "All" : "Device") : label.trim());
        name.setSingleLine(true);
        name.setTextSize(13);
        name.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
        name.setTextColor(selected ? tabSelectedText() : textPrimary());
        name.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        button.addView(name, nameParams);

        button.setOnClickListener(v -> {
            selectedDeviceId = id;
            prefs.edit().putString("selected_device_id", selectedDeviceId).apply();
            loadCachedDevices();
            updateDeviceSummary();
            updateDeviceActionButtons();
            // Show this device's cached runs immediately, then refresh in the
            // background — switching devices no longer waits on the network.
            loadCachedRuns();
            refreshRuns();
        });
        if (!"all".equals(id)) {
            button.setOnLongClickListener(v -> {
                showRenameDeviceDialog(id, label);
                return true;
            });
        }
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(42)
        );
        params.setMargins(0, dp(8), dp(8), 0);
        button.setLayoutParams(params);
        return button;
    }

    private void updateDeviceActionButtons() {
        boolean deviceSelected = selectedDeviceId != null && !"all".equals(selectedDeviceId);
        if (renameDeviceButton != null) {
            renameDeviceButton.setEnabled(deviceSelected);
        }
        if (revokeDeviceButton != null) {
            revokeDeviceButton.setEnabled(deviceSelected);
        }
        if (clearDeviceRunsButton != null) {
            clearDeviceRunsButton.setEnabled(deviceSelected);
        }
    }

    private void showDeviceActionsDialog() {
        boolean deviceSelected = selectedDeviceId != null && !"all".equals(selectedDeviceId);
        List<String> labels = new ArrayList<>();
        List<Integer> actions = new ArrayList<>();
        if (deviceSelected) {
            labels.add(isEnglish() ? "Rename" : "重命名");
            actions.add(0);
            labels.add(isEnglish() ? "Clear History" : "清空历史");
            actions.add(1);
            labels.add(isEnglish() ? "Revoke" : "撤销");
            actions.add(2);
        }
        labels.add(showOfflineDevicesEnabled()
                ? (isEnglish() ? "Hide Offline Devices" : "隐藏离线设备")
                : (isEnglish() ? "Show Offline Devices" : "显示离线设备"));
        actions.add(3);
        String label = selectedDeviceName();
        AlertDialog d = dialogBuilder()
                .setTitle(deviceSelected ? label : t("devices"))
                .setItems(labels.toArray(new String[0]), (dialog, which) -> {
                    int action = actions.get(which);
                    if (action == 0) {
                        showRenameDeviceDialog(selectedDeviceId, label);
                    } else if (action == 1) {
                        showClearDeviceRunsDialog(selectedDeviceId, label);
                    } else if (action == 2) {
                        showRevokeDeviceDialog(selectedDeviceId, label);
                    } else if (action == 3) {
                        toggleOfflineDevicesVisible();
                    }
                })
                .create();
        applyDialogStyle(d);
        d.show();
    }

    private boolean showOfflineDevicesEnabled() {
        return prefs == null || prefs.getBoolean(PREF_SHOW_OFFLINE_DEVICES, true);
    }

    private void toggleOfflineDevicesVisible() {
        boolean next = !showOfflineDevicesEnabled();
        SharedPreferences.Editor editor = prefs.edit().putBoolean(PREF_SHOW_OFFLINE_DEVICES, next);
        if (!next && selectedDeviceId != null && !"all".equals(selectedDeviceId)
                && !Boolean.TRUE.equals(deviceOnline.get(selectedDeviceId))) {
            selectedDeviceId = "all";
            editor.putString("selected_device_id", selectedDeviceId);
        }
        editor.apply();
        loadCachedDevices();
        updateDeviceSummary();
        updateDeviceActionButtons();
        refreshRuns();
        if (statusText != null) {
            statusText.setText(next
                    ? (isEnglish() ? "Offline devices are visible." : "已显示离线设备。")
                    : (isEnglish() ? "Offline devices are hidden." : "已隐藏离线设备。"));
        }
    }

    private String selectedDeviceName() {
        String name = deviceNames.get(selectedDeviceId);
        if (name != null && !name.trim().isEmpty()) {
            return name.trim();
        }
        String pairedId = prefs.getString("paired_device_id", "");
        if (selectedDeviceId != null && selectedDeviceId.equals(pairedId)) {
            String pairedName = prefs.getString("paired_device_name", "");
            if (pairedName != null && !pairedName.trim().isEmpty()) {
                return pairedName.trim();
            }
        }
        return t("device");
    }

    private void updateDeviceSummary() {
        if (deviceSummaryText == null) {
            return;
        }
        if (selectedDeviceId == null || "all".equals(selectedDeviceId)) {
            deviceSummaryText.setText(isEnglish() ? "All active devices" : "全部活跃设备");
            if (deviceGpuContainer != null) {
                deviceGpuContainer.setVisibility(View.GONE);
            }
            return;
        }
        String lastSeen = formatDeviceTimestamp(deviceLastSeen.get(selectedDeviceId));
        StringBuilder text = new StringBuilder(selectedDeviceName());
        boolean online = Boolean.TRUE.equals(deviceOnline.get(selectedDeviceId));
        text.append(" · ").append(online ? (isEnglish() ? "Online" : "在线") : (isEnglish() ? "Offline" : "离线"));
        if (!lastSeen.isEmpty()) {
            text.append(" · ").append(isEnglish() ? "seen " : "心跳 ").append(lastSeen);
        }
        if (selectedDeviceGpuCount() > 0) {
            text.append(gpuExpanded ? (isEnglish() ? " · GPU ▴" : " · GPU ▴") : (isEnglish() ? " · GPU ▾" : " · GPU ▾"));
        }
        deviceSummaryText.setText(text.toString());
        updateDeviceGpu();
    }

    private int selectedDeviceGpuCount() {
        if (selectedDeviceId == null || "all".equals(selectedDeviceId)) {
            return 0;
        }
        JSONArray devices = cachedDevicesArray();
        for (int i = 0; i < devices.length(); i++) {
            JSONObject d = devices.optJSONObject(i);
            if (d != null && selectedDeviceId.equals(d.optString("id", ""))) {
                JSONArray g = d.optJSONArray("gpus");
                return g == null ? 0 : g.length();
            }
        }
        return 0;
    }

    private void updateDeviceGpu() {
        if (deviceGpuContainer == null) {
            return;
        }
        deviceGpuContainer.removeAllViews();
        boolean all = selectedDeviceId == null || "all".equals(selectedDeviceId);
        if (all) {
            deviceGpuContainer.setVisibility(View.GONE);
            return;
        }
        JSONArray devices = cachedDevicesArray();
        List<JSONObject> deviceGpus = new ArrayList<>();
        for (int i = 0; i < devices.length(); i++) {
            JSONObject device = devices.optJSONObject(i);
            if (device == null) continue;
            String id = device.optString("id", "");
            if (id.equals(selectedDeviceId)) {
                JSONArray gpus = device.optJSONArray("gpus");
                if (gpus != null) {
                    for (int g = 0; g < gpus.length(); g++) {
                        JSONObject gpu = gpus.optJSONObject(g);
                        if (gpu != null) deviceGpus.add(gpu);
                    }
                }
                break;
            }
        }
        if (deviceGpus.isEmpty()) {
            deviceGpuContainer.setVisibility(View.GONE);
            return;
        }
        if (!gpuExpanded) {
            // Folded by default to keep the home page tidy; tap the device
            // status line to expand the swipeable GPU panel.
            deviceGpuContainer.setVisibility(View.GONE);
            return;
        }
        if (gpuMetricIndex < 0 || gpuMetricIndex >= GPU_METRIC_COUNT) {
            gpuMetricIndex = 0;
        }

        deviceGpuContainer.addView(buildGpuMetricHeader());

        int gpusPerRow = 4;
        int total = deviceGpus.size();
        int rows = (total + gpusPerRow - 1) / gpusPerRow;

        for (int r = 0; r < rows; r++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(1), 0, dp(1));

            for (int c = 0; c < gpusPerRow; c++) {
                int gidx = r * gpusPerRow + c;
                if (gidx >= total) {
                    // filler
                    View filler = new View(this);
                    filler.setLayoutParams(new LinearLayout.LayoutParams(0, dp(20), 1));
                    row.addView(filler);
                    continue;
                }

                JSONObject gpu = deviceGpus.get(gidx);
                int idx = gpu.optInt("index", gidx);
                int util = Math.max(0, Math.min(100, gpu.optInt("utilization", 0)));
                int memUsed = Math.max(0, gpu.optInt("memoryUsed", 0));
                int memTotal = Math.max(0, gpu.optInt("memoryTotal", 0));
                int temp = Math.max(0, gpu.optInt("temperature", 0));
                int memPct = memTotal > 0 ? Math.max(0, Math.min(100, Math.round(memUsed * 100f / memTotal))) : 0;

                int progress;
                String valueText;
                int barColor;
                if (gpuMetricIndex == 1) {            // VRAM usage rate
                    progress = memPct;
                    valueText = memPct + "%";
                    barColor = gpuBarColor(memPct, false);
                } else if (gpuMetricIndex == 2) {     // temperature
                    progress = Math.min(100, temp);
                    valueText = temp + "°";
                    barColor = gpuBarColor(temp, true);
                } else {                               // utilization
                    progress = util;
                    valueText = util + "%";
                    barColor = gpuBarColor(util, false);
                }

                LinearLayout item = new LinearLayout(this);
                item.setOrientation(LinearLayout.VERTICAL);
                item.setGravity(Gravity.CENTER_HORIZONTAL);
                item.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

                TextView label = new TextView(this);
                label.setText("G" + idx);
                label.setTextSize(8f);
                label.setTextColor(textSecondary());
                label.setGravity(Gravity.CENTER);
                label.setPadding(0, 0, 0, 0);
                item.addView(label, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

                ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
                bar.setMax(100);
                bar.setProgress(progress);
                LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(dp(38), dp(6));
                barLp.setMargins(dp(1), dp(1), dp(1), dp(1));
                bar.setLayoutParams(barLp);
                bar.setProgressTintList(ColorStateList.valueOf(barColor));
                bar.setProgressBackgroundTintList(ColorStateList.valueOf(gpuTrackColor()));

                item.addView(bar);

                TextView pct = new TextView(this);
                pct.setText(valueText);
                pct.setTextSize(7f);
                pct.setTextColor(textSecondary());
                pct.setGravity(Gravity.CENTER);
                item.addView(pct, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

                row.addView(item);
            }
            deviceGpuContainer.addView(row, matchWrap());
        }
        deviceGpuContainer.setVisibility(View.VISIBLE);
    }

    private int gpuBarColor(int value, boolean isTemp) {
        if (isTemp) {
            if (value >= 80) return color("#EF4444");
            if (value >= 65) return color("#EAB308");
            return color("#22C55E");
        }
        if (value >= 95) return color("#EF4444");
        if (value >= 75) return color("#EAB308");
        return color("#22C55E");
    }

    private String gpuMetricName() {
        if (gpuMetricIndex == 1) {
            return isEnglish() ? "VRAM usage" : "显存占用率";
        }
        if (gpuMetricIndex == 2) {
            return isEnglish() ? "Temperature" : "GPU 温度";
        }
        return isEnglish() ? "GPU usage" : "GPU 利用率";
    }

    private View buildGpuMetricHeader() {
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(0, 0, 0, dp(2));

        TextView title = new TextView(this);
        title.setText(gpuMetricName());
        title.setTextSize(10f);
        title.setTextColor(textPrimary());
        title.setTypeface(null, Typeface.BOLD);
        head.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        StringBuilder dots = new StringBuilder();
        for (int i = 0; i < GPU_METRIC_COUNT; i++) {
            dots.append(i == gpuMetricIndex ? "●" : "○");
            if (i < GPU_METRIC_COUNT - 1) {
                dots.append(' ');
            }
        }
        TextView dotView = new TextView(this);
        dotView.setText(dots + (isEnglish() ? "  swipe ›" : "  右滑切换 ›"));
        dotView.setTextSize(9f);
        dotView.setTextColor(textSecondary());
        head.addView(dotView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return head;
    }

    private void attachGpuSwipe(View target) {
        gpuGestureDetector = new android.view.GestureDetector(this, new android.view.GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(android.view.MotionEvent e1, android.view.MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null) {
                    return false;
                }
                float dx = e2.getX() - e1.getX();
                float dy = e2.getY() - e1.getY();
                if (Math.abs(dx) > Math.abs(dy) && Math.abs(dx) > dp(36)) {
                    if (dx < 0) {
                        gpuMetricIndex = (gpuMetricIndex + 1) % GPU_METRIC_COUNT;
                    } else {
                        gpuMetricIndex = (gpuMetricIndex - 1 + GPU_METRIC_COUNT) % GPU_METRIC_COUNT;
                    }
                    updateDeviceGpu();
                    return true;
                }
                return false;
            }

            @Override
            public boolean onSingleTapUp(android.view.MotionEvent e) {
                gpuMetricIndex = (gpuMetricIndex + 1) % GPU_METRIC_COUNT;
                updateDeviceGpu();
                return true;
            }
        });
        target.setClickable(true);
        target.setOnTouchListener((v, ev) -> {
            boolean handled = gpuGestureDetector.onTouchEvent(ev);
            if (ev.getAction() == android.view.MotionEvent.ACTION_UP) {
                v.performClick();
            }
            return handled;
        });
    }

    private void showClearDeviceRunsDialog(String deviceId, String currentName) {
        if (deviceId == null || deviceId.trim().isEmpty() || "all".equals(deviceId)) {
            return;
        }
        String label = currentName == null || currentName.trim().isEmpty() ? (isEnglish() ? "this device" : "这台设备") : currentName.trim();
        AlertDialog d = dialogBuilder()
                .setTitle(isEnglish() ? "Clear history" : "清空历史")
                .setMessage(isEnglish() ? "Delete all run history for " + label + "? The device stays paired." : "删除 " + label + " 的全部运行历史？设备会保持配对。")
                .setNegativeButton(t("cancel"), null)
                .setPositiveButton(t("clear"), (dialog, which) -> clearDeviceRuns(deviceId, label))
                .create();
        applyDialogStyle(d);
        d.show();
    }

    private void clearDeviceRuns(String deviceId, String label) {
        if (deviceId == null || deviceId.trim().isEmpty()) {
            return;
        }
        statusText.setText(isEnglish() ? "Clearing history..." : "正在清空历史...");
        executor.submit(() -> {
            try {
                httpRequest(normalizedServerUrl() + "/api/devices/" + Uri.encode(deviceId) + "/runs", "DELETE");
                removeDeviceRunsFromCaches(deviceId);
                handler.post(() -> {
                    statusText.setText(isEnglish() ? "Cleared history for " + label + "." : "已清空 " + label + " 的历史。");
                    loadCachedRuns();
                    refreshRuns();
                });
            } catch (Exception e) {
                handler.post(() -> statusText.setText((isEnglish() ? "Clear history failed: " : "清空历史失败：") + e.getMessage()));
            }
        });
    }

    private void showStatusFilterDialog() {
        String[] labels = new String[]{t("all"), t("running"), t("failed"), t("succeeded"), isEnglish() ? "Archived" : "已归档"};
        String[] values = new String[]{"all", "running", "failed", "succeeded", "archived"};
        int selected = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(selectedStatusFilter)) {
                selected = i;
                break;
            }
        }
        AlertDialog d = dialogBuilder()
                .setTitle(t("status"))
                .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                    selectedStatusFilter = values[which];
                    prefs.edit().putString(PREF_STATUS_FILTER, selectedStatusFilter).apply();
                    dialog.dismiss();
                    buildUi();
                    refreshRuns();
                })
                .setNegativeButton(t("cancel"), null)
                .create();
        applyDialogStyle(d);
        d.show();
    }

    private void showProjectFilterDialog() {
        List<String> values = availableProjectFilters();
        String[] labels = new String[values.size()];
        int selected = 0;
        for (int i = 0; i < values.size(); i++) {
            String value = values.get(i);
            labels[i] = projectFilterLabel(value);
            if (value.equals(selectedProjectFilter)) {
                selected = i;
            }
        }
        AlertDialog d = dialogBuilder()
                .setTitle(t("project"))
                .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                    selectedProjectFilter = values.get(which);
                    prefs.edit().putString(PREF_PROJECT_FILTER, selectedProjectFilter).apply();
                    dialog.dismiss();
                    buildUi();
                    refreshRuns();
                })
                .setNegativeButton(t("cancel"), null)
                .create();
        applyDialogStyle(d);
        d.show();
    }

    private List<String> availableProjectFilters() {
        Set<String> projects = new HashSet<>();
        collectProjectsFromCachedRuns(projects, prefs.getString(CACHE_RUNS, ""));
        collectProjectsFromCachedRuns(projects, prefs.getString(runsCacheKey(), ""));
        if (selectedProjectFilter != null && !"all".equals(selectedProjectFilter) && !"__none__".equals(selectedProjectFilter)) {
            projects.add(selectedProjectFilter);
        }
        List<String> sorted = new ArrayList<>(projects);
        Collections.sort(sorted);
        List<String> values = new ArrayList<>();
        values.add("all");
        values.add("__none__");
        values.addAll(sorted);
        return values;
    }

    private void collectProjectsFromCachedRuns(Set<String> projects, String cached) {
        if (cached == null || cached.isEmpty()) {
            return;
        }
        try {
            JSONArray runs = new JSONArray(cached);
            for (int i = 0; i < runs.length(); i++) {
                JSONObject run = runs.optJSONObject(i);
                if (run == null) {
                    continue;
                }
                String project = run.optString("project", "").trim();
                if (!project.isEmpty()) {
                    projects.add(project);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private String statusFilterLabel(String value) {
        if ("running".equals(value)) {
            return t("running");
        }
        if ("failed".equals(value)) {
            return t("failed");
        }
        if ("succeeded".equals(value)) {
            return t("succeeded");
        }
        if ("archived".equals(value)) {
            return isEnglish() ? "Archived" : "已归档";
        }
        return t("all");
    }

    private String projectFilterLabel(String value) {
        if ("__none__".equals(value)) {
            return t("no_project");
        }
        if (value == null || value.trim().isEmpty() || "all".equals(value)) {
            return t("all");
        }
        return value.trim();
    }

    private void showRenameDeviceDialog(String deviceId, String currentName) {
        if (deviceId == null || deviceId.trim().isEmpty() || "all".equals(deviceId)) {
            return;
        }
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(currentName == null ? "" : currentName.trim());
        input.setSelectAllOnFocus(true);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(80)});
        styleInput(input);
        input.setMinHeight(dp(46));
        input.setMinimumWidth(dp(280));

        LinearLayout renameWrap = new LinearLayout(this);
        renameWrap.setPadding(dp(20), dp(10), dp(20), dp(6));
        renameWrap.addView(input, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        AlertDialog d = dialogBuilder()
                .setTitle(isEnglish() ? "Rename device" : "重命名设备")
                .setView(renameWrap)
                .setNegativeButton(t("cancel"), null)
                .setPositiveButton(isEnglish() ? "Save" : "保存", (dialog, which) -> renameDevice(deviceId, input.getText().toString()))
                .create();
        applyDialogStyle(d);
        d.show();
    }

    private void renameDevice(String deviceId, String newName) {
        String name = newName == null ? "" : newName.trim();
        if (name.isEmpty()) {
            statusText.setText(isEnglish() ? "Device name cannot be empty." : "设备名称不能为空。");
            return;
        }
        statusText.setText(isEnglish() ? "Renaming device..." : "正在重命名设备...");
        executor.submit(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("name", name);
                String body = httpPostJson(normalizedServerUrl() + "/api/devices/" + Uri.encode(deviceId) + "/rename", payload.toString());
                JSONObject response = body.isEmpty() ? new JSONObject() : new JSONObject(body);
                JSONObject device = response.optJSONObject("device");
                String savedName = device == null ? name : device.optString("name", name).trim();
                if (savedName.isEmpty()) {
                    savedName = name;
                }
                String finalSavedName = savedName;
                handler.post(() -> {
                    String pairedId = prefs.getString("paired_device_id", "");
                    if (deviceId.equals(pairedId)) {
                        prefs.edit().putString("paired_device_name", finalSavedName).apply();
                    }
                    buildUi();
                    statusText.setText(isEnglish() ? "Renamed to " + finalSavedName + "." : "已重命名为 " + finalSavedName + "。");
                    refreshDevices();
                    refreshRuns();
                });
            } catch (Exception e) {
                handler.post(() -> statusText.setText((isEnglish() ? "Rename failed: " : "重命名失败：") + e.getMessage()));
            }
        });
    }

    private void showRevokeDeviceDialog(String deviceId, String currentName) {
        if (deviceId == null || deviceId.trim().isEmpty() || "all".equals(deviceId)) {
            return;
        }
        String label = currentName == null || currentName.trim().isEmpty() ? (isEnglish() ? "this device" : "这台设备") : currentName.trim();
        AlertDialog d = dialogBuilder()
                .setTitle(isEnglish() ? "Revoke device" : "撤销设备")
                .setMessage(isEnglish() ? "Stop " + label + " from uploading new runs?" : "阻止 " + label + " 继续上传新运行？")
                .setNegativeButton(t("cancel"), null)
                .setPositiveButton(isEnglish() ? "Revoke" : "撤销", (dialog, which) -> revokeDevice(deviceId, label))
                .create();
        applyDialogStyle(d);
        d.show();
    }

    private void revokeDevice(String deviceId, String label) {
        if (deviceId == null || deviceId.trim().isEmpty()) {
            return;
        }
        statusText.setText(isEnglish() ? "Revoking device..." : "正在撤销设备...");
        executor.submit(() -> {
            try {
                httpRequest(normalizedServerUrl() + "/api/devices/" + Uri.encode(deviceId), "DELETE");
                handler.post(() -> {
                    selectedDeviceId = "all";
                    SharedPreferences.Editor editor = prefs.edit().putString("selected_device_id", selectedDeviceId);
                    if (deviceId.equals(prefs.getString("paired_device_id", ""))) {
                        editor.remove("paired_device_id").remove("paired_device_name");
                    }
                    editor.apply();
                    statusText.setText(isEnglish() ? "Revoked " + label + "." : "已撤销 " + label + "。");
                    buildUi();
                    refreshDevices();
                    refreshRuns();
                });
            } catch (Exception e) {
                handler.post(() -> statusText.setText((isEnglish() ? "Revoke failed: " : "撤销失败：") + e.getMessage()));
            }
        });
    }

    private void renderRuns(JSONArray runs) {
        renderRuns(runs, false);
    }

    private void renderRuns(JSONArray runs, boolean fromCache) {
        JSONArray visibleRuns = filterRuns(runs);
        // Track whether anything is actively running so the poll loop can speed up.
        boolean active = false;
        for (int i = 0; i < runs.length(); i++) {
            JSONObject r = runs.optJSONObject(i);
            if (r != null) {
                String s = r.optString("status", "");
                if ("running".equals(s) || "created".equals(s)) {
                    active = true;
                    break;
                }
            }
        }
        hasActiveRunVisible = active;
        int failedCount = 0;
        for (int i = 0; i < visibleRuns.length(); i++) {
            JSONObject run = visibleRuns.optJSONObject(i);
            if (run != null && "failed".equals(run.optString("status", ""))) {
                failedCount++;
            }
        }
        String scope = "all".equals(selectedProjectFilter)
                ? ""
                : (isEnglish() ? " in " : "，项目 ") + projectFilterLabel(selectedProjectFilter);
        String suffix = "all".equals(selectedStatusFilter)
                ? (isEnglish() ? visibleRuns.length() + " run(s)" + scope + "." : visibleRuns.length() + " 条运行" + scope + "。")
                : (isEnglish() ? visibleRuns.length() + " " + selectedStatusFilter + " run(s)" + scope + "." : visibleRuns.length() + " 条" + statusFilterLabel(selectedStatusFilter) + "运行" + scope + "。");
        String prefix = fromCache ? (isEnglish() ? "Saved. " : "已保存。") : (isEnglish() ? "Updated. " : "已更新。");
        String failedPart = failedCount > 0
                ? (isEnglish() ? failedCount + " failed run(s). " : failedCount + " 条失败运行。")
                : "";
        statusText.setText(prefix + failedPart + suffix);
        // Notifications must fire regardless of which tab is showing (the run list
        // only exists on the Runs tab now), so notify before the container guard.
        if (!fromCache) {
            for (int i = 0; i < visibleRuns.length(); i++) {
                JSONObject run = visibleRuns.optJSONObject(i);
                if (run != null) {
                    maybeNotify(run);
                }
            }
        }
        // Skip the expensive full-list rebuild when nothing visible changed
        // (avoids re-inflating every run card on each poll).
        String runsSig = runsSignature(visibleRuns);
        if (runsContainer != null && runsContainer.getChildCount() > 0 && runsSig.equals(lastRunsSig)) {
            if (!fromCache) {
                firstLoad = false;
            }
            return;
        }
        if (runsContainer == null) {
            return;
        }
        lastRunsSig = runsSig;
        runsContainer.removeAllViews();

        for (int i = 0; i < visibleRuns.length(); i++) {
            JSONObject run = visibleRuns.optJSONObject(i);
            if (run == null) {
                continue;
            }
            try {
                runsContainer.addView(runView(run));
            } catch (Throwable throwable) {
                runsContainer.addView(runRenderErrorView(run, throwable));
            }
        }
        if (visibleRuns.length() == 0) {
            runsContainer.addView(emptyState(
                    isEnglish() ? "No runs yet" : "还没有运行记录",
                    isEnglish() ? "Try: hao echo hello" : "试试：hao echo hello",
                    "▶"
            ), matchWrap());
        }
        if (!fromCache) {
            firstLoad = false;
        }
    }

    private String runsSignature(JSONArray runs) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < runs.length(); i++) {
            JSONObject r = runs.optJSONObject(i);
            if (r == null) {
                continue;
            }
            sb.append(r.optString("id", "")).append('|')
              .append(r.optString("status", "")).append('|')
              .append(r.optString("updatedAt", "")).append('|')
              .append(r.optInt("outputLength", r.optString("outputTail", "").length())).append(';');
        }
        return sb.toString();
    }

    private JSONArray filterRuns(JSONArray runs) {
        boolean archivedView = "archived".equals(selectedStatusFilter);
        boolean allDevices = selectedDeviceId == null || "all".equals(selectedDeviceId);
        Set<String> archived = archivedRunIds();
        JSONArray filtered = new JSONArray();
        for (int i = 0; i < runs.length(); i++) {
            JSONObject run = runs.optJSONObject(i);
            if (run == null) {
                continue;
            }
            if (!allDevices && !selectedDeviceId.equals(run.optString("deviceId", ""))) {
                continue;
            }
            String id = run.optString("id", "");
            boolean isArchived = !id.isEmpty() && archived.contains(id);
            if (archivedView) {
                if (!isArchived) {
                    continue;
                }
            } else {
                if (isArchived) {
                    continue;
                }
                if (!statusMatchesFilter(run.optString("status", ""), selectedStatusFilter)) {
                    continue;
                }
            }
            if (!projectMatchesFilter(run.optString("project", ""), selectedProjectFilter)) {
                continue;
            }
            filtered.put(run);
        }
        return filtered;
    }

    private boolean projectMatchesFilter(String project, String filter) {
        String value = project == null ? "" : project.trim();
        if (filter == null || filter.trim().isEmpty() || "all".equals(filter)) {
            return true;
        }
        if ("__none__".equals(filter)) {
            return value.isEmpty();
        }
        return filter.trim().equals(value);
    }

    private boolean statusMatchesFilter(String status, String filter) {
        if (filter == null || filter.trim().isEmpty() || "all".equals(filter)) {
            return true;
        }
        if ("running".equals(filter)) {
            return "running".equals(status) || "created".equals(status);
        }
        return filter != null && filter.equals(status);
    }

    private void loadCachedRuns() {
        String cacheKey = runsCacheKey();
        String cacheAtKey = runsCacheAtKey();
        String cached = prefs.getString(cacheKey, "");
        if (cached == null || cached.isEmpty()) {
            // No cache yet for this exact filter combination (e.g. just chose a new project).
            // Fall back to the full cached runs so client-side filter can apply immediately.
            // This makes project/status filter selection instant from local data,
            // without waiting for a network refresh to populate the specific cache.
            cached = prefs.getString(CACHE_RUNS, "");
            cacheAtKey = CACHE_RUNS_AT;
            if (cached == null || cached.isEmpty()) {
                // try the current device/status specific if exists
                String broadKey = CACHE_RUNS_PREFIX + cachePart(selectedDeviceId) + "_" + cachePart(selectedStatusFilter) + "_all";
                cached = prefs.getString(broadKey, "");
                // cacheAtKey would be approximate
            }
        }
        if (cached == null || cached.isEmpty() || runsContainer == null) {
            return;
        }
        try {
            renderRuns(new JSONArray(cached), true);
            long savedAt = prefs.getLong(cacheAtKey, 0L);
            if (savedAt > 0L && statusText != null) {
            statusText.setText(isEnglish() ? "Saved results. Tap Refresh for latest." : "正在显示保存结果。点击刷新获取最新内容。");
            }
        } catch (Exception ignored) {
        }
    }

    private boolean hasCachedRuns() {
        String cached = prefs.getString(runsCacheKey(), "");
        if (cached != null && !cached.isEmpty()) {
            return true;
        }
        return "all".equals(selectedDeviceId)
                && "all".equals(selectedStatusFilter)
                && "all".equals(selectedProjectFilter)
                && !prefs.getString(CACHE_RUNS, "").isEmpty();
    }

    private void saveRunsCache(JSONArray runs) {
        mergeDevicesFromRuns(runs);
        long now = System.currentTimeMillis();
        SharedPreferences.Editor editor = prefs.edit()
                .putString(runsCacheKey(), runs.toString())
                .putLong(runsCacheAtKey(), now);
        if ("all".equals(selectedDeviceId) && "all".equals(selectedStatusFilter) && "all".equals(selectedProjectFilter)) {
            editor.putString(CACHE_RUNS, runs.toString())
                    .putLong(CACHE_RUNS_AT, now);
        }
        editor.apply();
    }

    private void mergeDevicesFromRuns(JSONArray runs) {
        if (runs == null) {
            return;
        }
        try {
            Map<String, JSONObject> mergedById = new HashMap<>();
            String cached = prefs.getString(CACHE_DEVICES, "");
            if (cached != null && !cached.isEmpty()) {
                JSONArray cachedDevices = new JSONArray(cached);
                for (int i = 0; i < cachedDevices.length(); i++) {
                    JSONObject device = cachedDevices.optJSONObject(i);
                    if (device == null) {
                        continue;
                    }
                    String id = device.optString("id", "").trim();
                    if (!id.isEmpty()) {
                        mergedById.put(id, device);
                    }
                }
            }
            boolean changed = false;
            for (int i = 0; i < runs.length(); i++) {
                JSONObject run = runs.optJSONObject(i);
                if (run == null) {
                    continue;
                }
                String id = run.optString("deviceId", "").trim();
                if (id.isEmpty()) {
                    continue;
                }
                String name = run.optString("deviceName", "").trim();
                if (name.isEmpty()) {
                    name = appDisplayName() + " CLI";
                }
                String seenAt = run.optString("updatedAt", "").trim();
                JSONObject existing = mergedById.get(id);
                if (existing == null) {
                    JSONObject device = new JSONObject();
                    device.put("id", id);
                    device.put("name", name);
                    device.put("createdAt", run.optString("startedAt", seenAt));
                    device.put("lastSeenAt", seenAt);
                    device.put("tokenLastUsedAt", seenAt);
                    device.put("revokedAt", "");
                    device.put("online", "running".equals(run.optString("status", "")) || "created".equals(run.optString("status", "")));
                    device.put("onlineWindowSeconds", 90);
                    mergedById.put(id, device);
                    changed = true;
                } else {
                    String existingSeen = existing.optString("lastSeenAt", "");
                    if (seenAt.compareTo(existingSeen) > 0) {
                        existing.put("lastSeenAt", seenAt);
                        existing.put("tokenLastUsedAt", seenAt);
                        changed = true;
                    }
                    if (existing.optString("name", "").trim().isEmpty()) {
                        existing.put("name", name);
                        changed = true;
                    }
                    if (!existing.optString("revokedAt", "").isEmpty()) {
                        existing.put("revokedAt", "");
                        changed = true;
                    }
                }
            }
            if (!changed) {
                return;
            }
            JSONArray merged = new JSONArray();
            for (JSONObject device : mergedById.values()) {
                merged.put(device);
            }
            prefs.edit().putString(CACHE_DEVICES, merged.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    private void mergeDevicesFromCachedRuns() {
        Map<String, ?> values = prefs.getAll();
        for (String key : values.keySet()) {
            if (!CACHE_RUNS.equals(key) && !key.startsWith(CACHE_RUNS_PREFIX)) {
                continue;
            }
            Object rawValue = values.get(key);
            if (!(rawValue instanceof String)) {
                continue;
            }
            String cached = (String) rawValue;
            if (cached.isEmpty()) {
                continue;
            }
            try {
                mergeDevicesFromRuns(new JSONArray(cached));
            } catch (Exception ignored) {
            }
        }
    }

    private String runsCacheKey() {
        return CACHE_RUNS_PREFIX + cachePart(selectedDeviceId) + "_" + cachePart(selectedStatusFilter) + "_" + cachePart(selectedProjectFilter);
    }

    private String runsCacheAtKey() {
        return CACHE_RUNS_AT_PREFIX + cachePart(selectedDeviceId) + "_" + cachePart(selectedStatusFilter) + "_" + cachePart(selectedProjectFilter);
    }

    private String cachePart(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "all";
        }
        return value.trim().replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private String cacheAtKeyForRunsKey(String runsKey) {
        if (CACHE_RUNS.equals(runsKey)) {
            return CACHE_RUNS_AT;
        }
        if (runsKey != null && runsKey.startsWith(CACHE_RUNS_PREFIX)) {
            return CACHE_RUNS_AT_PREFIX + runsKey.substring(CACHE_RUNS_PREFIX.length());
        }
        return "";
    }

    private View runView(JSONObject run) {
        String runId = run.optString("id", "");
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.setBackground(roundedBg(cardBg(), 12, cardStroke()));
        card.setElevation(0);
        card.setClickable(true);
        card.setOnClickListener(v -> openRunDetail(runId));

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, 0, 0, dp(6));
        card.setLayoutParams(cardParams);

        String status = run.optString("status", "unknown");
        LinearLayout topLine = new LinearLayout(this);
        topLine.setOrientation(LinearLayout.HORIZONTAL);
        topLine.setGravity(Gravity.CENTER_VERTICAL);

        TextView dot = new TextView(this);
        dot.setText("●");
        dot.setTextSize(12);
        dot.setTextColor(statusColor(status));
        topLine.addView(dot, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView command = new TextView(this);
        command.setText(displayText(run.optString("commandText", isEnglish() ? "(unknown command)" : "（未知命令）")));
        command.setTextSize(14);
        command.setTextColor(textPrimary());
        command.setTypeface(null, Typeface.BOLD);
        command.setSingleLine(true);
        command.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams commandParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        commandParams.setMargins(dp(8), 0, dp(8), 0);
        topLine.addView(command, commandParams);

        TextView label = new TextView(this);
        label.setText(statusLabel(status));
        label.setTextSize(11);
        label.setTypeface(null, Typeface.BOLD);
        label.setTextColor(statusColor(status));
        label.setPadding(dp(6), dp(2), dp(6), dp(2));
        label.setBackground(roundedBg(statusBadgeColor(status), 7, Color.TRANSPARENT));
        topLine.addView(label, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        card.addView(topLine, matchWrap());

        String deviceName = run.optString("deviceName", "");
        String projectName = run.optString("project", "").trim();
        TextView meta = new TextView(this);
        String shownDevice = deviceName.isEmpty() ? appDisplayName() + " CLI" : deviceName;
        String projectPrefix = projectName.isEmpty() ? "" : projectName + " · ";
        meta.setText(durationText(run) + " · " + projectPrefix + shownDevice + statusExitSuffix(run));
        meta.setTextSize(11);
        meta.setTextColor(textSecondary());
        meta.setPadding(0, dp(3), 0, 0);
        card.addView(meta, matchWrap());

        String latest = latestOutputLine(run);
        boolean hasOutput = !latest.isEmpty();
        TextView output = new TextView(this);
        output.setText(hasOutput ? displayText(latest) : (isEnglish() ? "(no output)" : "（暂无输出）"));
        output.setTextSize(11);
        output.setTextColor(hasOutput ? textPrimary() : textSecondary());
        output.setTypeface(android.graphics.Typeface.MONOSPACE);
        output.setSingleLine(true);
        output.setPadding(0, dp(5), 0, 0);
        card.addView(output, matchWrap());

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        actionParams.setMargins(0, dp(6), 0, 0);

        TextView consoleButton = actionButton(actionLabel("▣", t("console"), 1.12f));
        consoleButton.setOnClickListener(v -> openRunDetail(runId));
        actions.addView(consoleButton, new LinearLayout.LayoutParams(0, dp(36), 1));

        TextView deleteButton = actionButton(actionLabel("⌫", t("delete"), 1.12f));
        deleteButton.setOnClickListener(v -> deleteRun(runId));
        LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(
                0,
                dp(36),
                1
        );
        deleteParams.setMargins(dp(8), 0, 0, 0);
        actions.addView(deleteButton, deleteParams);
        card.addView(actions, actionParams);

        return card;
    }

    private View runRenderErrorView(JSONObject run, Throwable throwable) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(roundedBg(cardBg(), 14, cardStroke()));
        card.setElevation(0);

        TextView title = new TextView(this);
        title.setText(isEnglish() ? "Run card failed" : "运行卡片渲染失败");
        title.setTextSize(16);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(color("#B42318"));
        card.addView(title, matchWrap());

        TextView command = new TextView(this);
        command.setText(run == null ? (isEnglish() ? "(unknown run)" : "（未知运行）") : displayText(run.optString("commandText", isEnglish() ? "(unknown command)" : "（未知命令）")));
        command.setTextSize(13);
        command.setTextColor(textPrimary());
        command.setPadding(0, dp(6), 0, dp(6));
        card.addView(command, matchWrap());

        TextView detail = new TextView(this);
        detail.setText(crashText(throwable));
        detail.setTextSize(11);
        detail.setTypeface(android.graphics.Typeface.MONOSPACE);
        detail.setTextColor(textSecondary());
        card.addView(detail, matchWrap());

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, 0, 0, dp(10));
        card.setLayoutParams(cardParams);
        return card;
    }

    private void openRunDetail(String id) {
        if (id == null || id.isEmpty()) {
            return;
        }
        selectedRunId = id;
        selectedRunStatus = "";
        consoleOutputSyncedLength = 0;
        outputChunkSyncedCount = 0;
        consoleIncrementalUsesChunks = false;
        currentConsoleOutput = "";
        buildConsoleUi();
        loadCachedRunDetail(id);
        refreshRunDetail(id, true);
        // Re-arm the auto-refresh loop so the console starts polling within ~800ms
        // (not up to one list-cadence later) and is guaranteed running.
        handler.removeCallbacks(pollRunnable);
        handler.postDelayed(pollRunnable, CONSOLE_RUNNING_POLL_MS);
    }

    private void mergeRunDetailMetadata(JSONObject run) {
        if (run == null) {
            return;
        }
        if (currentRunDetail == null
                || !currentRunDetail.optString("id", "").equals(run.optString("id", ""))) {
            currentRunDetail = run;
            return;
        }
        for (String key : new String[]{"status", "updatedAt", "endedAt", "exitCode", "pid", "project", "deviceName"}) {
            if (run.has(key)) {
                try {
                    currentRunDetail.put(key, run.get(key));
                } catch (Exception ignored) {
                }
            }
        }
    }

    private void showRunInfoDialog() {
        JSONObject run = currentRunDetail;
        if (run == null) {
            statusText.setText(isEnglish() ? "Run details are still loading." : "运行信息尚未加载完成。");
            return;
        }
        String id = run.optString("id", "");
        boolean archived = isRunArchived(id);

        ScrollView scroll = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(22), dp(6), dp(22), dp(6));
        scroll.addView(box, matchWrap());

        String status = run.optString("status", "");
        addInfoRow(box, isEnglish() ? "Status" : "状态", statusLabel(status) + statusExitSuffix(run));
        addInfoRow(box, isEnglish() ? "Command" : "命令", run.optString("commandText", ""));
        addInfoRow(box, isEnglish() ? "Server" : "服务器", normalizedServerUrl());
        addInfoRow(box, isEnglish() ? "Device" : "设备", run.optString("deviceName", ""));
        addInfoRow(box, isEnglish() ? "Project" : "项目", run.optString("project", ""));
        addInfoRow(box, isEnglish() ? "Path" : "路径", run.optString("cwd", ""));
        int pid = run.optInt("pid", -1);
        addInfoRow(box, "PID", pid > 0 ? String.valueOf(pid) : "");
        addInfoRow(box, isEnglish() ? "CLI version" : "CLI 版本", run.optString("cliVersion", ""));
        addInfoRow(box, isEnglish() ? "OS" : "操作系统", run.optString("os", ""));
        addInfoRow(box, isEnglish() ? "Hostname" : "主机名", run.optString("hostname", ""));
        addInfoRow(box, isEnglish() ? "Run ID" : "运行 ID", id);
        addInfoRow(box, isEnglish() ? "Created" : "创建时间", formatIsoLocal(run.optString("startedAt", "")));
        addInfoRow(box, isEnglish() ? "Ended" : "结束时间", formatIsoLocal(run.optString("endedAt", "")));
        addInfoRow(box, isEnglish() ? "Updated" : "更新时间", formatIsoLocal(run.optString("updatedAt", "")));
        addInfoRow(box, isEnglish() ? "Duration" : "用时", durationText(run));
        addInfoRow(box, isEnglish() ? "Archived" : "归档",
                archived ? (isEnglish() ? "Yes" : "已归档") : (isEnglish() ? "No" : "未归档"));

        AlertDialog d = dialogBuilder()
                .setTitle(isEnglish() ? "Run details" : "运行详情")
                .setView(scroll)
                .setPositiveButton(isEnglish() ? "Copy" : "复制",
                        (dialog, which) -> copyText(appDisplayName() + " run info", buildRunInfoText(run)))
                .setNeutralButton(archived ? (isEnglish() ? "Unarchive" : "取消归档") : (isEnglish() ? "Archive" : "归档"),
                        (dialog, which) -> {
                            setRunArchived(id, !archived);
                            statusText.setText(archived
                                    ? (isEnglish() ? "Run unarchived." : "已取消归档。")
                                    : (isEnglish() ? "Run archived." : "已归档，可在状态筛选里查看。"));
                        })
                .setNegativeButton(t("close"), null)
                .create();
        applyDialogStyle(d);
        d.show();
    }

    private void addInfoRow(LinearLayout parent, String label, String value) {
        if (value == null) {
            value = "";
        }
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(8), 0, dp(8));

        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextSize(11);
        labelView.setTextColor(textSecondary());
        row.addView(labelView, matchWrap());

        TextView valueView = new TextView(this);
        valueView.setText(value.isEmpty() ? "—" : value);
        valueView.setTextSize(14);
        valueView.setTextColor(textPrimary());
        valueView.setPadding(0, dp(2), 0, 0);
        valueView.setTextIsSelectable(true);
        row.addView(valueView, matchWrap());

        parent.addView(row, matchWrap());

        View divider = new View(this);
        divider.setBackgroundColor(settingsDivider());
        int h = Math.max(1, Math.round(getResources().getDisplayMetrics().density * 0.7f));
        parent.addView(divider, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, h));
    }

    private String buildRunInfoText(JSONObject run) {
        String id = run.optString("id", "");
        int pid = run.optInt("pid", -1);
        StringBuilder sb = new StringBuilder();
        sb.append(isEnglish() ? "Status: " : "状态：").append(statusLabel(run.optString("status", ""))).append(statusExitSuffix(run)).append('\n');
        sb.append(isEnglish() ? "Command: " : "命令：").append(run.optString("commandText", "")).append('\n');
        sb.append(isEnglish() ? "Server: " : "服务器：").append(normalizedServerUrl()).append('\n');
        sb.append(isEnglish() ? "Device: " : "设备：").append(run.optString("deviceName", "")).append('\n');
        sb.append(isEnglish() ? "Project: " : "项目：").append(run.optString("project", "")).append('\n');
        sb.append(isEnglish() ? "Path: " : "路径：").append(run.optString("cwd", "")).append('\n');
        sb.append("PID: ").append(pid > 0 ? String.valueOf(pid) : "").append('\n');
        sb.append(isEnglish() ? "CLI version: " : "CLI 版本：").append(run.optString("cliVersion", "")).append('\n');
        sb.append(isEnglish() ? "OS: " : "操作系统：").append(run.optString("os", "")).append('\n');
        sb.append(isEnglish() ? "Hostname: " : "主机名：").append(run.optString("hostname", "")).append('\n');
        sb.append(isEnglish() ? "Run ID: " : "运行 ID：").append(id).append('\n');
        sb.append(isEnglish() ? "Created: " : "创建时间：").append(formatIsoLocal(run.optString("startedAt", ""))).append('\n');
        sb.append(isEnglish() ? "Ended: " : "结束时间：").append(formatIsoLocal(run.optString("endedAt", ""))).append('\n');
        sb.append(isEnglish() ? "Updated: " : "更新时间：").append(formatIsoLocal(run.optString("updatedAt", ""))).append('\n');
        sb.append(isEnglish() ? "Duration: " : "用时：").append(durationText(run));
        return sb.toString();
    }

    private String formatIsoLocal(String iso) {
        if (iso == null || iso.trim().isEmpty()) {
            return "";
        }
        try {
            String s = iso.trim();
            int dot = s.indexOf('.');
            if (dot > 0) {
                s = s.substring(0, dot);
            } else {
                s = s.replace("Z", "");
            }
            java.text.SimpleDateFormat in = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            in.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            java.util.Date dt = in.parse(s);
            java.text.SimpleDateFormat out = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
            return dt == null ? iso : out.format(dt);
        } catch (Exception e) {
            return iso;
        }
    }

    private Set<String> archivedRunIds() {
        Set<String> set = new HashSet<>();
        String raw = prefs.getString(PREF_ARCHIVED_RUNS, "");
        if (raw != null && !raw.isEmpty()) {
            try {
                JSONArray a = new JSONArray(raw);
                for (int i = 0; i < a.length(); i++) {
                    String s = a.optString(i, "");
                    if (!s.isEmpty()) {
                        set.add(s);
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return set;
    }

    private boolean isRunArchived(String id) {
        return id != null && !id.isEmpty() && archivedRunIds().contains(id);
    }

    private void setRunArchived(String id, boolean archived) {
        if (id == null || id.isEmpty()) {
            return;
        }
        Set<String> set = archivedRunIds();
        if (archived) {
            set.add(id);
        } else {
            set.remove(id);
        }
        JSONArray a = new JSONArray();
        for (String s : set) {
            a.put(s);
        }
        prefs.edit().putString(PREF_ARCHIVED_RUNS, a.toString()).apply();
        loadCachedRuns();
        refreshRuns();
    }

    private void buildConsoleUi() {
        consoleAutoScroll = true;
        consoleSearchVisible = false;
        consoleRenderLimit = CONSOLE_RENDER_INITIAL_CHARS;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), statusBarHeight() + dp(18), dp(18), navigationBarHeight() + dp(18));
        root.setBackgroundColor(appBg());

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView backButton = actionButton(actionLabel("‹", t("back"), 1.25f));
        backButton.setOnClickListener(v -> returnToList());
        top.addView(backButton, new LinearLayout.LayoutParams(dp(86), dp(46)));

        TextView title = new TextView(this);
        title.setText(t("console"));
        title.setTextSize(24);
        title.setTextColor(textPrimary());
        title.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        titleParams.setMargins(dp(10), 0, 0, 0);
        top.addView(title, titleParams);

        TextView searchToggle = actionButton(actionLabel("⌕", t("search"), 1.12f));
        searchToggle.setOnClickListener(v -> {
            consoleSearchVisible = !consoleSearchVisible;
            if (consoleSearchInput != null) {
                consoleSearchInput.setVisibility(consoleSearchVisible ? View.VISIBLE : View.GONE);
                if (!consoleSearchVisible) {
                    consoleSearchInput.setText("");
                } else {
                    consoleSearchInput.requestFocus();
                }
            }
        });
        root.addView(top, matchWrap());

        LinearLayout consoleActions = new LinearLayout(this);
        consoleActions.setOrientation(LinearLayout.HORIZONTAL);
        consoleActions.setGravity(Gravity.CENTER_VERTICAL);
        consoleActions.addView(searchToggle, new LinearLayout.LayoutParams(0, dp(44), 1));

        TextView copyButton = actionButton(actionLabel("⧉", t("copy"), 1.12f));
        copyButton.setOnClickListener(v -> copyConsoleOutput());
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0, dp(44), 1);
        copyParams.setMargins(dp(8), 0, 0, 0);
        consoleActions.addView(copyButton, copyParams);

        consoleAutoScrollButton = actionButton(actionLabel("↓", "Auto", 1.12f));
        consoleAutoScrollButton.setOnClickListener(v -> {
            consoleAutoScroll = !consoleAutoScroll;
            updateConsoleAutoScrollButton();
            if (consoleAutoScroll && consoleVerticalScroll != null) {
                consoleVerticalScroll.post(() -> consoleVerticalScroll.fullScroll(View.FOCUS_DOWN));
            }
        });
        LinearLayout.LayoutParams autoParams = new LinearLayout.LayoutParams(0, dp(44), 1);
        autoParams.setMargins(dp(8), 0, 0, 0);
        consoleActions.addView(consoleAutoScrollButton, autoParams);

        TextView infoButton = actionButton(actionLabel("ⓘ", isEnglish() ? "Info" : "信息", 1.12f));
        infoButton.setOnClickListener(v -> showRunInfoDialog());
        LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(0, dp(44), 1);
        infoParams.setMargins(dp(8), 0, 0, 0);
        consoleActions.addView(infoButton, infoParams);

        LinearLayout.LayoutParams actionRowParams = matchWrap();
        actionRowParams.setMargins(0, dp(8), 0, dp(8));
        root.addView(consoleActions, actionRowParams);

        detailCommand = new TextView(this);
        detailCommand.setText(isEnglish() ? "Loading command..." : "正在加载命令...");
        detailCommand.setTextSize(16);
        detailCommand.setTextColor(textPrimary());
        detailCommand.setTypeface(null, Typeface.BOLD);
        detailCommand.setPadding(0, dp(12), 0, 0);
        // Long commands are clamped so they don't crowd the console; tap to see the full command.
        detailCommand.setMaxLines(2);
        detailCommand.setEllipsize(android.text.TextUtils.TruncateAt.END);
        detailCommand.setOnClickListener(v -> {
            CharSequence full = detailCommand.getText();
            if (full == null || full.length() == 0) {
                return;
            }
            AlertDialog d = dialogBuilder()
                    .setTitle(isEnglish() ? "Command" : "命令")
                    .setMessage(full.toString())
                    .setPositiveButton(isEnglish() ? "Copy" : "复制", (dialog, which) -> copyText(appDisplayName() + " command", full.toString()))
                    .setNegativeButton(t("close"), null)
                    .create();
            applyDialogStyle(d);
            d.show();
        });
        root.addView(detailCommand, matchWrap());

        detailMeta = new TextView(this);
        detailMeta.setText("");
        detailMeta.setTextSize(13);
        detailMeta.setTextColor(textSecondary());
        detailMeta.setPadding(0, dp(5), 0, dp(8));
        root.addView(detailMeta, matchWrap());

        consoleInterruptButton = actionButton(actionLabel("■", t("interrupt"), 1.12f));
        consoleInterruptButton.setTextColor(color("#B42318"));
        consoleInterruptButton.setOnClickListener(v -> confirmInterruptRun());
        LinearLayout.LayoutParams interruptParams = matchWrap();
        interruptParams.setMargins(0, dp(4), 0, dp(4));
        consoleInterruptButton.setVisibility(View.GONE);
        root.addView(consoleInterruptButton, interruptParams);

        statusText = new TextView(this);
        statusText.setText(isEnglish() ? "Loading console..." : "正在加载控制台...");
        statusText.setTextSize(13);
        statusText.setTextColor(textSecondary());
        statusText.setPadding(0, 0, 0, dp(8));
        root.addView(statusText, matchWrap());

        consoleSearchInput = new EditText(this);
        consoleSearchInput.setSingleLine(true);
        consoleSearchInput.setTextSize(14);
        consoleSearchInput.setHint(isEnglish() ? "Search console" : "搜索控制台");
        consoleSearchInput.setInputType(InputType.TYPE_CLASS_TEXT);
        consoleSearchInput.setVisibility(View.GONE);
        styleInput(consoleSearchInput);
        consoleSearchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                renderConsoleText();
            }
        });
        root.addView(consoleSearchInput, matchWrap());

        consoleVerticalScroll = new ScrollView(this);
        consoleVerticalScroll.setBackground(roundedBg(consoleBg(), 14, consoleStroke()));
        consoleVerticalScroll.getViewTreeObserver().addOnScrollChangedListener(() -> {
            if (consoleVerticalScroll == null) {
                return;
            }
            View child = consoleVerticalScroll.getChildAt(0);
            if (child == null) {
                return;
            }
            int distanceFromBottom = child.getBottom() - (consoleVerticalScroll.getHeight() + consoleVerticalScroll.getScrollY());
            consoleAutoScroll = distanceFromBottom < dp(32);
            updateConsoleAutoScrollButton();
            updateConsoleMoreButton();
        });
        HorizontalScrollView horizontalScroll = new HorizontalScrollView(this);
        LinearLayout consoleContent = new LinearLayout(this);
        consoleContent.setOrientation(LinearLayout.VERTICAL);

        consoleTopMoreButton = actionButton(actionLabel("↑", isEnglish() ? "Load older" : "加载更早", 1.12f));
        consoleTopMoreButton.setOnClickListener(v -> {
            consoleRenderLimit = Math.min(consoleHistoryLimit(), consoleRenderLimit + CONSOLE_RENDER_STEP_CHARS);
            consoleAutoScroll = false;
            renderConsoleText();
            if (consoleVerticalScroll != null) {
                consoleVerticalScroll.post(() -> consoleVerticalScroll.fullScroll(View.FOCUS_UP));
            }
        });
        LinearLayout.LayoutParams topMoreParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(42)
        );
        topMoreParams.setMargins(dp(10), dp(10), dp(10), 0);
        consoleContent.addView(consoleTopMoreButton, topMoreParams);

        detailConsole = new TextView(this);
        detailConsole.setText(isEnglish() ? "Loading..." : "正在加载...");
        detailConsole.setTextSize(12);
        detailConsole.setTextColor(consoleText());
        detailConsole.setBackgroundColor(Color.TRANSPARENT);
        detailConsole.setPadding(dp(12), dp(12), dp(12), dp(12));
        detailConsole.setTypeface(android.graphics.Typeface.MONOSPACE);
        detailConsole.setHorizontallyScrolling(true);
        horizontalScroll.addView(detailConsole, new HorizontalScrollView.LayoutParams(
                HorizontalScrollView.LayoutParams.WRAP_CONTENT,
                HorizontalScrollView.LayoutParams.WRAP_CONTENT
        ));
        consoleContent.addView(horizontalScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        consoleVerticalScroll.addView(consoleContent, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));
        root.addView(consoleVerticalScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        setContentView(root);
        updateConsoleAutoScrollButton();
        updateConsoleMoreButton();
    }

    private void updateConsoleAutoScrollButton() {
        if (consoleAutoScrollButton != null) {
            consoleAutoScrollButton.setText(actionLabel("↓", consoleAutoScroll ? t("auto_on") : t("auto_off"), 1.12f));
        }
    }

    private void updateConsoleMoreButton() {
        if (consoleTopMoreButton == null) {
            return;
        }
        boolean shouldShow = isConsoleRenderClipped() && isConsoleScrolledToTop();
        consoleTopMoreButton.setVisibility(shouldShow ? View.VISIBLE : View.GONE);
        consoleTopMoreButton.setEnabled(shouldShow);
    }

    private boolean isConsoleScrolledToTop() {
        return consoleVerticalScroll == null || consoleVerticalScroll.getScrollY() <= dp(8);
    }

    private void copyConsoleOutput() {
        String output = currentConsoleOutput == null || currentConsoleOutput.isEmpty() ? (isEnglish() ? "No output yet." : "还没有输出。") : displayText(collapseCarriageReturns(currentConsoleOutput));
        copyText(appDisplayName() + " console", output);
        if (statusText != null) {
            statusText.setText(isEnglish() ? "Console copied." : "控制台已复制。");
        }
    }

    private void updateConsoleInterruptButton(boolean visible) {
        if (consoleInterruptButton == null) {
            return;
        }
        consoleInterruptButton.setVisibility(visible ? View.VISIBLE : View.GONE);
        consoleInterruptButton.setEnabled(visible);
    }

    private void confirmInterruptRun() {
        if (selectedRunId == null || selectedRunId.isEmpty()) {
            return;
        }
        AlertDialog d = dialogBuilder()
                .setTitle(t("interrupt"))
                .setMessage(t("interrupt_confirm"))
                .setNegativeButton(t("cancel"), null)
                .setPositiveButton(t("interrupt"), (dialog, which) -> interruptRun(selectedRunId))
                .create();
        applyDialogStyle(d);
        d.show();
    }

    private void interruptRun(String id) {
        if (id == null || id.isEmpty()) {
            return;
        }
        if (statusText != null) {
            statusText.setText(isEnglish() ? "Sending interrupt..." : "正在发送中断请求...");
        }
        if (consoleInterruptButton != null) {
            consoleInterruptButton.setEnabled(false);
        }
        executor.submit(() -> {
            try {
                httpPostJson(normalizedServerUrl() + "/api/runs/" + Uri.encode(id) + "/interrupt", "{}");
                handler.post(() -> {
                    if (!id.equals(selectedRunId)) {
                        return;
                    }
                    if (statusText != null) {
                        statusText.setText(isEnglish() ? "Interrupt sent. Waiting for command to stop..." : "中断请求已发送，等待命令停止...");
                    }
                    refreshRunDetail(id, false);
                });
            } catch (Exception e) {
                handler.post(() -> {
                    if (!id.equals(selectedRunId)) {
                        return;
                    }
                    updateConsoleInterruptButton(true);
                    if (statusText != null) {
                        statusText.setText((isEnglish() ? "Interrupt failed: " : "中断失败：") + e.getMessage());
                    }
                });
            }
        });
    }

    private void refreshRunDetail(String id, boolean showLoading) {
        if (showLoading && statusText != null) {
            statusText.setText(isEnglish() ? "Loading console..." : "正在加载控制台...");
        }
        executor.submit(() -> {
            try {
                StringBuilder url = new StringBuilder(normalizedServerUrl()).append("/api/runs/").append(id);
                if (!showLoading && id.equals(selectedRunId)) {
                    if (consoleIncrementalUsesChunks && outputChunkSyncedCount > 0) {
                        url.append("?outputSince=").append(outputChunkSyncedCount);
                    } else if (consoleOutputSyncedLength > 0) {
                        url.append("?outputLength=").append(consoleOutputSyncedLength);
                    }
                }
                JSONObject payload = new JSONObject(httpGet(url.toString()));
                final boolean incremental = payload.optBoolean("incremental", false);
                final JSONObject run = decryptRun(payload.getJSONObject("run"), payload.optJSONArray("outputChunks"));
                if (!incremental) {
                    prefs.edit().putString(CACHE_RUN_PREFIX + id, run.toString()).apply();
                }
                handler.post(() -> {
                    if (id.equals(selectedRunId)) {
                        if (incremental) {
                            applyIncrementalRunDetail(run, payload);
                        } else {
                            updateRunDetail(run);
                        }
                    }
                });
            } catch (Exception e) {
                handler.post(() -> {
                    if (id.equals(selectedRunId) && statusText != null) {
                        statusText.setText((isEnglish() ? "Cannot load console: " : "无法加载控制台：") + e.getMessage());
                    }
                });
            }
        });
    }

    private void loadCachedRunDetail(String id) {
        String cached = prefs.getString(CACHE_RUN_PREFIX + id, "");
        if (cached == null || cached.isEmpty()) {
            return;
        }
        try {
            updateRunDetail(new JSONObject(cached), true);
        } catch (Exception ignored) {
        }
    }

    private void applyIncrementalRunDetail(JSONObject run, JSONObject payload) {
        String status = run.optString("status", "unknown");
        if (selectedRunId != null && selectedRunId.equals(run.optString("id", ""))) {
            selectedRunStatus = status;
        }
        mergeRunDetailMetadata(run);
        String projectName = run.optString("project", "").trim();
        String projectSuffix = projectName.isEmpty() ? "" : " · " + projectName;
        detailMeta.setText(status.toUpperCase(Locale.US) + projectSuffix + statusSuffix(run));
        detailMeta.setTextColor(statusColor(status));
        updateConsoleInterruptButton("running".equals(status) || "created".equals(status));

        String appendText = payload.optString("outputAppend", "");
        if (!appendText.isEmpty()) {
            currentConsoleOutput = (currentConsoleOutput == null ? "" : currentConsoleOutput) + appendText;
            consoleOutputSyncedLength = currentConsoleOutput.length();
        }
        JSONArray chunks = payload.optJSONArray("outputChunks");
        if (chunks != null && chunks.length() > 0) {
            consoleIncrementalUsesChunks = true;
            currentConsoleOutput = (currentConsoleOutput == null ? "" : currentConsoleOutput) + decryptOutputChunks(run.optString("id", ""), chunks);
            outputChunkSyncedCount += chunks.length();
            consoleOutputSyncedLength = currentConsoleOutput.length();
        }
        if (payload.has("outputLength")) {
            consoleOutputSyncedLength = Math.max(consoleOutputSyncedLength, payload.optInt("outputLength", consoleOutputSyncedLength));
        }

        renderConsoleText();
        maybeNotify(run);
        if (("running".equals(status) || "created".equals(status)) && consoleAutoScroll && consoleVerticalScroll != null) {
            consoleVerticalScroll.post(() -> consoleVerticalScroll.fullScroll(View.FOCUS_DOWN));
        }
        if (statusText != null && !isConsoleRenderClipped() && (consoleSearchInput == null || consoleSearchInput.getText().toString().trim().isEmpty())) {
            statusText.setText(isEnglish() ? "Console updated." : "控制台已更新。");
        }
    }

    private void updateRunDetail(JSONObject run) {
        updateRunDetail(run, false);
    }

    private void updateRunDetail(JSONObject run, boolean fromCache) {
        if (!fromCache) {
            maybeNotify(run);
        }
        currentRunDetail = run;
        String status = run.optString("status", "unknown");
        if (selectedRunId != null && selectedRunId.equals(run.optString("id", ""))) {
            selectedRunStatus = status;
        }
        detailCommand.setText(displayText(run.optString("commandText", isEnglish() ? "(unknown command)" : "（未知命令）")));
        String projectName = run.optString("project", "").trim();
        String projectSuffix = projectName.isEmpty() ? "" : " · " + projectName;
        detailMeta.setText(status.toUpperCase(Locale.US) + projectSuffix + statusSuffix(run));
        detailMeta.setTextColor(statusColor(status));
        updateConsoleInterruptButton("running".equals(status) || "created".equals(status));
        currentConsoleOutput = consoleOutput(run);
        JSONArray chunks = run.optJSONArray("outputChunks");
        outputChunkSyncedCount = chunks == null ? 0 : chunks.length();
        consoleIncrementalUsesChunks = outputChunkSyncedCount > 0;
        consoleOutputSyncedLength = currentConsoleOutput == null ? 0 : currentConsoleOutput.length();
        renderConsoleText();
        if (("running".equals(status) || "created".equals(status)) && consoleAutoScroll && consoleVerticalScroll != null) {
            consoleVerticalScroll.post(() -> consoleVerticalScroll.fullScroll(View.FOCUS_DOWN));
        }
        if (statusText != null && !isConsoleRenderClipped() && (consoleSearchInput == null || consoleSearchInput.getText().toString().trim().isEmpty())) {
            statusText.setText(fromCache ? (isEnglish() ? "Saved console." : "已保存控制台。") : (isEnglish() ? "Console updated." : "控制台已更新。"));
        }
    }

    private void renderConsoleText() {
        if (detailConsole == null) {
            return;
        }
        String query = consoleSearchInput == null ? "" : consoleSearchInput.getText().toString().trim();
        if (query.isEmpty()) {
            detailConsole.setText(currentConsoleOutput == null || currentConsoleOutput.isEmpty() ? (isEnglish() ? "No output yet." : "还没有输出。") : displayConsoleOutput());
            if (statusText != null) {
                statusText.setText(consoleRenderStatusText());
            }
            updateConsoleMoreButton();
            return;
        }

        String lowerQuery = query.toLowerCase(Locale.US);
        String searchable = collapseCarriageReturns(consoleWindowRaw());
        String[] lines = searchable.split("\\r?\\n", -1);
        StringBuilder matches = new StringBuilder();
        int count = 0;
        for (String line : lines) {
            if (line.toLowerCase(Locale.US).contains(lowerQuery)) {
                matches.append(displayText(line)).append("\n");
                count++;
            }
        }
        if (count == 0) {
            detailConsole.setText(isEnglish() ? "No matching console lines." : "没有匹配的控制台行。");
        } else {
            detailConsole.setText(matches.toString());
        }
        if (statusText != null) {
            String suffix = isConsoleRenderClipped()
                    ? (isEnglish() ? " in shown output. Scroll to top to load older output." : "（当前显示范围）。滑到顶部可加载更早输出。")
                    : ".";
            statusText.setText(isEnglish() ? count + " matching line(s)" + suffix : count + " 行匹配" + suffix);
        }
        updateConsoleMoreButton();
    }

    // Render carriage-return progress frames the way a terminal would: each '\r'
    // rewrites the current line in place, so an updating progress bar collapses to
    // its latest frame instead of spamming a new line per update. While the bar is
    // still running it is simply the last (un-terminated) line, which sits at the
    // bottom of the console; once it finishes (a '\n' arrives) it stays as one line
    // in the history.
    private String collapseCarriageReturns(String s) {
        if (s == null) {
            return "";
        }
        if (s.indexOf('\r') < 0) {
            return s;
        }
        StringBuilder out = new StringBuilder(s.length());
        int lineStart = 0;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\n') {
                out.append('\n');
                lineStart = out.length();
            } else if (ch == '\r') {
                // "\r\n" is a normal line ending (a PTY maps \n -> \r\n), so the
                // \r must NOT wipe the line — let the following \n finalize it.
                // Only a bare \r (carriage return) is an in-place overwrite
                // (progress-bar frame), which restarts the current line.
                if (i + 1 < s.length() && s.charAt(i + 1) == '\n') {
                    continue;
                }
                out.setLength(lineStart);
            } else {
                out.append(ch);
            }
        }
        return out.toString();
    }

    private String displayConsoleOutput() {
        String output = collapseCarriageReturns(consoleWindowRaw());
        if (output.isEmpty()) {
            return "";
        }
        if (!isConsoleRenderClipped()) {
            return displayText(output);
        }
        String note = isEnglish()
                ? "[Haoleme] Showing last " + consoleRenderLabel(consoleRenderLimit) + " for smooth scrolling. Scroll to top to load older output.\n...\n"
                : "[好了么] 为了保持流畅，正在显示最后 " + consoleRenderLabel(consoleRenderLimit) + "。滑到顶部可加载更早输出。\n...\n";
        return note + displayText(output);
    }

    private String consoleWindowRaw() {
        String output = currentConsoleOutput == null ? "" : currentConsoleOutput;
        if (output.length() <= consoleRenderLimit) {
            return output;
        }
        return output.substring(output.length() - consoleRenderLimit);
    }

    private boolean isConsoleRenderClipped() {
        return currentConsoleOutput != null && currentConsoleOutput.length() > consoleRenderLimit;
    }

    private String consoleRenderStatusText() {
        if (!isConsoleRenderClipped()) {
            return isEnglish() ? "Console ready." : "控制台就绪。";
        }
        return isEnglish()
                ? "Showing last " + consoleRenderLabel(consoleRenderLimit) + " of " + consoleRenderLabel(currentConsoleOutput.length()) + "."
                : "正在显示最后 " + consoleRenderLabel(consoleRenderLimit) + " / 共 " + consoleRenderLabel(currentConsoleOutput.length()) + "。";
    }

    private String consoleRenderLabel(int chars) {
        if (chars >= 1000000) {
            return (chars / 1000000) + "M chars";
        }
        return Math.max(1, chars / 1000) + "k chars";
    }

    private String consoleOutput(JSONObject run) {
        String output = run.optString("outputTail", "");
        if (!output.isEmpty()) {
            return limitConsoleOutput(output);
        }

        String stdout = run.optString("stdoutTail", "");
        String stderr = run.optString("stderrTail", "");
        StringBuilder combined = new StringBuilder();
        if (!stdout.isEmpty()) {
            combined.append("$ stdout\n").append(stdout);
            if (!stdout.endsWith("\n")) {
                combined.append("\n");
            }
        }
        if (!stderr.isEmpty()) {
            combined.append("$ stderr\n").append(stderr);
        }
        if (combined.length() == 0) {
            return isEnglish() ? "No output yet." : "还没有输出。";
        }
        return limitConsoleOutput(combined.toString());
    }

    private String limitConsoleOutput(String output) {
        if (output == null) {
            return "";
        }
        int limit = consoleHistoryLimit();
        if (output.length() <= limit) {
            return output;
        }
        String clipped = output.substring(output.length() - limit);
        String note = isEnglish()
                ? "[Haoleme] Showing last " + consoleHistoryLabel() + ". Increase Console History in Settings for a larger window.\n...\n"
                : "[好了么] 正在显示最后 " + consoleHistoryLabel() + "。可在设置里增大控制台历史窗口。\n...\n";
        return note + clipped;
    }

    private void returnToList() {
        selectedRunId = null;
        selectedRunStatus = "";
        buildUi();
        refreshRuns();
    }

    private void deleteRun(String id) {
        if (id == null || id.isEmpty()) {
            return;
        }
        statusText.setText(isEnglish() ? "Deleting..." : "正在删除...");
        executor.submit(() -> {
            try {
                httpRequest(normalizedServerUrl() + "/api/runs/" + id, "DELETE");
                knownStatuses.remove(id);
                removeRunFromCaches(id);
                handler.post(() -> {
                    loadCachedRuns();
                    refreshRuns();
                });
            } catch (Exception e) {
                handler.post(() -> statusText.setText((isEnglish() ? "Delete failed: " : "删除失败：") + e.getMessage()));
            }
        });
    }

    private void removeRunFromCaches(String id) {
        SharedPreferences.Editor editor = prefs.edit()
                .remove(CACHE_RUN_PREFIX + id)
                .remove("notified_terminal_" + id);
        long now = System.currentTimeMillis();
        Map<String, ?> values = prefs.getAll();
        for (String key : values.keySet()) {
            if (!CACHE_RUNS.equals(key) && !key.startsWith(CACHE_RUNS_PREFIX)) {
                continue;
            }
            Object rawValue = values.get(key);
            if (!(rawValue instanceof String)) {
                continue;
            }
            JSONArray updated = removeRunFromJsonArray((String) rawValue, id);
            if (updated == null) {
                continue;
            }
            editor.putString(key, updated.toString());
            String atKey = cacheAtKeyForRunsKey(key);
            if (!atKey.isEmpty()) {
                editor.putLong(atKey, now);
            }
        }
        editor.apply();
    }

    private void removeDeviceRunsFromCaches(String deviceId) {
        SharedPreferences.Editor editor = prefs.edit();
        long now = System.currentTimeMillis();
        Map<String, ?> values = prefs.getAll();
        for (String key : values.keySet()) {
            if (!CACHE_RUNS.equals(key) && !key.startsWith(CACHE_RUNS_PREFIX)) {
                continue;
            }
            Object rawValue = values.get(key);
            if (!(rawValue instanceof String)) {
                continue;
            }
            JSONArray updated = removeDeviceRunsFromJsonArray((String) rawValue, deviceId);
            if (updated == null) {
                continue;
            }
            editor.putString(key, updated.toString());
            String atKey = cacheAtKeyForRunsKey(key);
            if (!atKey.isEmpty()) {
                editor.putLong(atKey, now);
            }
        }
        editor.apply();
    }

    private JSONArray removeDeviceRunsFromJsonArray(String raw, String deviceId) {
        try {
            JSONArray original = new JSONArray(raw);
            JSONArray kept = new JSONArray();
            boolean removed = false;
            for (int i = 0; i < original.length(); i++) {
                JSONObject run = original.optJSONObject(i);
                if (run != null && deviceId.equals(run.optString("deviceId", ""))) {
                    removed = true;
                    String id = run.optString("id", "");
                    if (!id.isEmpty()) {
                        prefs.edit().remove(CACHE_RUN_PREFIX + id).remove("notified_terminal_" + id).apply();
                    }
                    continue;
                }
                kept.put(original.get(i));
            }
            return removed ? kept : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private JSONArray removeRunFromJsonArray(String raw, String id) {
        try {
            JSONArray original = new JSONArray(raw);
            JSONArray kept = new JSONArray();
            boolean removed = false;
            for (int i = 0; i < original.length(); i++) {
                JSONObject run = original.optJSONObject(i);
                if (run != null && id.equals(run.optString("id", ""))) {
                    removed = true;
                    continue;
                }
                kept.put(original.get(i));
            }
            return removed ? kept : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private void maybeNotify(JSONObject run) {
        String id = run.optString("id", "");
        String status = run.optString("status", "");
        if (id.isEmpty()) {
            return;
        }

        String previous = knownStatuses.put(id, status);
        if (previous != null && previous.equals(status)) {
            return;
        }

        boolean isTerminal = "succeeded".equals(status) || "failed".equals(status) || "cancelled".equals(status);
        if (!isTerminal) {
            return;
        }

        boolean wasRunning = "created".equals(previous) || "running".equals(previous);
        boolean completedDuringSession = runTerminalAtMillis(run) >= notificationSessionStartedAt;
        if ((!wasRunning && !completedDuringSession) || (firstLoad && !completedDuringSession)) {
            return;
        }
        if (!shouldNotifyTerminalRun(run, status)) {
            return;
        }
        String notifyKey = "notified_terminal_" + id;
        if (status.equals(prefs.getString(notifyKey, ""))) {
            return;
        }
        sendNotification(run);
        prefs.edit().putString(notifyKey, status).apply();
    }

    private boolean shouldNotifyTerminalRun(JSONObject run, String status) {
        if ("succeeded".equals(status) && !notifySuccessEnabled()) {
            return false;
        }
        if (("failed".equals(status) || "cancelled".equals(status)) && !notifyFailureEnabled()) {
            return false;
        }
        int minSeconds = notifyMinSeconds();
        if (minSeconds > 0 && runDurationSeconds(run) < minSeconds) {
            return false;
        }
        return !quietHoursEnabled() || !isQuietHourNow();
    }

    private long runDurationSeconds(JSONObject run) {
        long started = parseTimestamp(run.optString("startedAt", ""));
        long ended = runTerminalAtMillis(run);
        if (started <= 0 || ended <= 0 || ended < started) {
            return 0L;
        }
        return Math.max(0L, (ended - started) / 1000L);
    }

    private long runTerminalAtMillis(JSONObject run) {
        String endedRaw = run.optString("endedAt", "");
        return endedRaw.isEmpty() || "null".equals(endedRaw)
                ? parseTimestamp(run.optString("updatedAt", ""))
                : parseTimestamp(endedRaw);
    }

    private boolean isQuietHourNow() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        return hour >= 22 || hour < 8;
    }

    private void sendNotification(JSONObject run) {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        android.app.Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new android.app.Notification.Builder(this, CHANNEL_ID)
                : new android.app.Notification.Builder(this);

        String command = displayText(run.optString("commandText", "Command"));
        String status = run.optString("status", "finished");
        String summary = notificationSummary(run, command, status);
        builder.setContentTitle(appDisplayName() + ": " + status)
                .setContentText(summary)
                .setSmallIcon(android.R.drawable.stat_notify_more)
                .setAutoCancel(true);
        manager.notify(run.optString("id", command).hashCode(), builder.build());
    }

    private void startHaolemeForegroundService() {
        Intent intent = new Intent(this, HaolemeForegroundService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
        } catch (IllegalStateException | SecurityException ignored) {
        }
    }

    private String httpGet(String target) throws Exception {
        return httpRequest(target, "GET", true, null, true, HTTP_READ_TIMEOUT_MS);
    }

    private String httpGet(String target, int readTimeoutMs) throws Exception {
        return httpRequest(target, "GET", true, null, true, readTimeoutMs);
    }

    private String httpGetPublic(String target) throws Exception {
        return httpRequest(target, "GET", false);
    }

    private String httpRequest(String target, String method) throws Exception {
        return httpRequest(target, method, true);
    }

    private String httpRequest(String target, String method, boolean includeToken) throws Exception {
        return httpRequest(target, method, includeToken, null);
    }

    private String httpPostJson(String target, String bodyJson) throws Exception {
        return httpRequest(target, "POST", true, bodyJson);
    }

    private String httpRequest(String target, String method, boolean includeToken, String bodyJson) throws Exception {
        return httpRequest(target, method, includeToken, bodyJson, true);
    }

    private String httpRequest(String target, String method, boolean includeToken, String bodyJson, boolean allowRegisterRetry) throws Exception {
        return httpRequest(target, method, includeToken, bodyJson, allowRegisterRetry, HTTP_READ_TIMEOUT_MS);
    }

    private String httpRequest(
            String target,
            String method,
            boolean includeToken,
            String bodyJson,
            boolean allowRegisterRetry,
            int readTimeoutMs
    ) throws Exception {
        try {
            return httpRequestOnce(target, method, includeToken, bodyJson, allowRegisterRetry, readTimeoutMs);
        } catch (SocketException e) {
            if (isConnectionReset(e)) {
                return httpRequestOnce(target, method, includeToken, bodyJson, allowRegisterRetry, readTimeoutMs);
            }
            throw e;
        }
    }

    private String httpRequestOnce(
            String target,
            String method,
            boolean includeToken,
            String bodyJson,
            boolean allowRegisterRetry,
            int readTimeoutMs
    ) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(target).openConnection();
        try {
            connection.setConnectTimeout(HTTP_CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(readTimeoutMs);
            connection.setRequestMethod(method);
            connection.setRequestProperty("Accept", "application/json");
            // Prevent any intermediate or local caching for things like update manifests
            connection.setRequestProperty("Cache-Control", "no-cache");
            connection.setRequestProperty("Pragma", "no-cache");
            if (bodyJson != null) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            }
            String token = normalizedToken();
            if (includeToken && !token.isEmpty()) {
                connection.setRequestProperty("Authorization", "Bearer " + token);
            }
            if (bodyJson != null) {
                byte[] body = bodyJson.getBytes(StandardCharsets.UTF_8);
                connection.setFixedLengthStreamingMode(body.length);
                connection.getOutputStream().write(body);
            }
            int code = connection.getResponseCode();
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream(),
                    StandardCharsets.UTF_8
            ));
            StringBuilder body = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                body.append(line);
            }
            reader.close();
            if (code < 200 || code >= 300) {
                if (code == 401 && includeToken && allowRegisterRetry && shouldRegisterBeforeRetry(target) && registerAppToken(serverBaseUrl(target))) {
                    return httpRequest(target, method, includeToken, bodyJson, false, readTimeoutMs);
                }
                throw new HaolemeHttpException(code, body.toString());
            }
            return body.toString();
        } finally {
            connection.disconnect();
        }
    }

    private boolean isConnectionReset(SocketException e) {
        String message = e.getMessage();
        return message != null && message.toLowerCase(Locale.US).contains("connection reset");
    }

    private String safeRequestLabel(String target) {
        try {
            Uri uri = Uri.parse(target);
            String host = uri.getHost();
            String path = uri.getPath();
            return (host == null ? "" : host) + (path == null ? "" : path);
        } catch (Exception ignored) {
            return "request";
        }
    }

    private boolean shouldRegisterBeforeRetry(String target) {
        try {
            String path = Uri.parse(target).getPath();
            if (path == null) {
                return false;
            }
            if (path.startsWith("/api/pair/") || path.startsWith("/api/space/")) {
                return true;
            }
            return hasPairedDevice() || !prefs.getString("space_id", "").trim().isEmpty();
        } catch (Exception ignored) {
            return false;
        }
    }

    private String serverBaseUrl(String target) throws Exception {
        URL url = new URL(target);
        String base = url.getProtocol() + "://" + url.getHost();
        int port = url.getPort();
        if (port > 0) {
            base += ":" + port;
        }
        return normalizeServerUrl(base);
    }

    private boolean registerAppToken(String serverUrl) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("clientId", appClientId());
            payload.put("clientName", appDisplayName() + " Android");
            payload.put("platform", "android");
            payload.put("appVersionCode", currentVersionCode());
            payload.put("appVersionName", currentVersionName());
            String responseText = httpRequest(normalizeServerUrl(serverUrl) + "/api/apps/register", "POST", true, payload.toString(), false);
            JSONObject response = responseText.isEmpty() ? new JSONObject() : new JSONObject(responseText);
            String clientId = response.optString("clientId", "").trim();
            String spaceId = response.optString("spaceId", "").trim();
            SharedPreferences.Editor editor = prefs.edit().putString("app_registered_at", response.optString("registeredAt", ""));
            if (!clientId.isEmpty()) {
                editor.putString(PREF_APP_CLIENT_ID, clientId);
            }
            if (!spaceId.isEmpty() && prefs.getString("space_id", "").trim().isEmpty()) {
                editor.putString("space_id", spaceId);
            }
            editor.apply();
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private String appClientId() {
        String saved = prefs.getString(PREF_APP_CLIENT_ID, "");
        if (saved != null && saved.startsWith("app_")) {
            return saved;
        }
        byte[] random = new byte[12];
        new SecureRandom().nextBytes(random);
        String clientId = "app_" + Base64.encodeToString(random, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING)
                .replace("-", "_");
        prefs.edit().putString(PREF_APP_CLIENT_ID, clientId).apply();
        return clientId;
    }

    private String cloudFailureMessage(Exception e) {
        Throwable cause = rootCause(e);
        if (cause instanceof UnknownHostException) {
            return isEnglish() ? "Cloud refresh failed: DNS cannot find the server." : "云端刷新失败：DNS 找不到服务器。";
        }
        if (cause instanceof SocketTimeoutException) {
            return isEnglish() ? "Cloud refresh failed: server timed out." : "云端刷新失败：服务器超时。";
        }
        if (cause instanceof ConnectException) {
            return isEnglish() ? "Cloud refresh failed: cannot connect to the server." : "云端刷新失败：无法连接服务器。";
        }
        if (cause instanceof SocketException && cause.getMessage() != null
                && cause.getMessage().toLowerCase(Locale.US).contains("connection reset")) {
            return isEnglish()
                    ? "Cloud refresh failed: HTTPS handshake was reset. Check server TLS settings."
                    : "云端刷新失败：HTTPS 握手被断开，请检查服务器 TLS 配置。";
        }
        if (cause instanceof IOException && cause.getMessage() != null && cause.getMessage().toLowerCase(Locale.US).contains("ssl")) {
            return isEnglish() ? "Cloud refresh failed: secure connection failed." : "云端刷新失败：安全连接失败。";
        }
        if (e instanceof HaolemeHttpException) {
            HaolemeHttpException http = (HaolemeHttpException) e;
            if (http.statusCode == 401 || http.statusCode == 403) {
                return isEnglish() ? "Cloud refresh failed: login expired. Pair again." : "云端刷新失败：登录已失效，请重新配对。";
            }
            if (http.statusCode == 426) {
                return isEnglish() ? "Cloud refresh failed: app is too old. Update first." : "云端刷新失败：App 版本太旧，请先更新。";
            }
            if (http.statusCode >= 500) {
                return isEnglish() ? "Cloud refresh failed: cloud service is temporarily unavailable." : "云端刷新失败：云服务暂时不可用。";
            }
            String serverMessage = http.errorMessage();
            if (!serverMessage.isEmpty()) {
                return (isEnglish() ? "Cloud refresh failed: " : "云端刷新失败：") + serverMessage;
            }
            return isEnglish() ? "Cloud refresh failed: HTTP " + http.statusCode + "." : "云端刷新失败：HTTP " + http.statusCode + "。";
        }
        String message = e == null ? "" : e.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return isEnglish() ? "Cloud refresh failed." : "云端刷新失败。";
        }
        return (isEnglish() ? "Cloud refresh failed: " : "云端刷新失败：") + message.trim();
    }

    private String pairFailureMessage(Exception e) {
        Throwable cause = rootCause(e);
        if (cause instanceof UnknownHostException) {
            return isEnglish() ? "Pair failed: network cannot find the cloud server. Check Wi-Fi or mobile data." : "配对失败：网络找不到云端服务器。请检查 Wi-Fi 或移动数据。";
        }
        if (cause instanceof SocketTimeoutException) {
            return isEnglish() ? "Pair failed: server timed out. The network may be slow, try again." : "配对失败：服务器超时。网络可能较慢，请重试。";
        }
        if (cause instanceof ConnectException) {
            return isEnglish() ? "Pair failed: cannot connect to the server. Try again later." : "配对失败：无法连接服务器，请稍后重试。";
        }
        if (cause instanceof IOException && cause.getMessage() != null && cause.getMessage().toLowerCase(Locale.US).contains("ssl")) {
            return isEnglish() ? "Pair failed: secure connection failed. Check the phone date and network." : "配对失败：安全连接失败。请检查手机时间和网络。";
        }
        if (e instanceof HaolemeHttpException) {
            HaolemeHttpException http = (HaolemeHttpException) e;
            String errorCode = http.errorCode();
            if ("app_version_too_old".equals(errorCode) || http.statusCode == 426) {
                return isEnglish() ? "Pair failed: this app is too old. Tap Update first, then pair again." : "配对失败：当前 App 版本太旧。请先更新再配对。";
            }
            if ("pair_code_expired".equals(errorCode) || http.statusCode == 404) {
                return isEnglish() ? "Pair failed: code expired or does not exist. Run hao login again for a new code." : "配对失败：配对码已过期或不存在。请重新运行 hao login。";
            }
            if ("pair_code_used".equals(errorCode) || http.statusCode == 409) {
                return isEnglish() ? "Pair failed: this code was already used. Run hao login again." : "配对失败：这个配对码已经使用过。请重新运行 hao login。";
            }
            if (http.statusCode == 401) {
                return isEnglish() ? "Pair failed: account token was rejected. Restart the app and try again." : "配对失败：账号 token 被拒绝。请重启 App 后重试。";
            }
            if (http.statusCode >= 500) {
                return isEnglish() ? "Pair failed: cloud service is temporarily unavailable. Try again later." : "配对失败：云服务暂时不可用，请稍后重试。";
            }
            String serverMessage = http.errorMessage();
            if (!serverMessage.isEmpty()) {
                return (isEnglish() ? "Pair failed: " : "配对失败：") + serverMessage;
            }
            return isEnglish() ? "Pair failed: server returned HTTP " + http.statusCode + "." : "配对失败：服务器返回 HTTP " + http.statusCode + "。";
        }
        String message = e.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return isEnglish() ? "Pair failed. Check the network and try again." : "配对失败。请检查网络后重试。";
        }
        return (isEnglish() ? "Pair failed: " : "配对失败：") + message;
    }

    private String syncSpaceFailureMessage(Exception e) {
        Throwable cause = rootCause(e);
        if (cause instanceof UnknownHostException) {
            return isEnglish() ? "Sync failed: network cannot find the cloud server." : "同步失败：网络找不到云端服务器。";
        }
        if (cause instanceof SocketTimeoutException) {
            return isEnglish() ? "Sync failed: server timed out. Try again." : "同步失败：服务器超时，请重试。";
        }
        if (cause instanceof ConnectException) {
            return isEnglish() ? "Sync failed: cannot connect to the server." : "同步失败：无法连接服务器。";
        }
        if (e instanceof HaolemeHttpException) {
            HaolemeHttpException http = (HaolemeHttpException) e;
            String errorCode = http.errorCode();
            if ("app_version_too_old".equals(errorCode) || http.statusCode == 426) {
                return isEnglish() ? "Sync failed: this app is too old. Update first." : "同步失败：当前 App 版本太旧，请先更新。";
            }
            if ("space_code_expired".equals(errorCode) || http.statusCode == 404) {
                return isEnglish() ? "Sync failed: code expired or does not exist." : "同步失败：共享空间码已过期或不存在。";
            }
            if ("space_code_used".equals(errorCode) || http.statusCode == 409) {
                return isEnglish() ? "Sync failed: this code was already used." : "同步失败：这个共享空间码已经使用过。";
            }
            if ("space_share_token_invalid".equals(errorCode) || http.statusCode == 403) {
                return isEnglish() ? "Sync failed: QR token is invalid. Generate a new code." : "同步失败：二维码令牌无效，请重新生成。";
            }
            if (http.statusCode >= 500) {
                return isEnglish() ? "Sync failed: cloud service is temporarily unavailable." : "同步失败：云服务暂时不可用。";
            }
            String serverMessage = http.errorMessage();
            if (!serverMessage.isEmpty()) {
                return (isEnglish() ? "Sync failed: " : "同步失败：") + serverMessage;
            }
        }
        String message = e.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return isEnglish() ? "Sync failed. Check the network and try again." : "同步失败。请检查网络后重试。";
        }
        return (isEnglish() ? "Sync failed: " : "同步失败：") + message;
    }


    private Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private void confirmPairing() {
        String code = pairInput == null ? "" : pairInput.getText().toString().replaceAll("\\D", "");
        if (code.length() != 6) {
            statusText.setText(isEnglish() ? "Enter the 6-digit pair code." : "请输入 6 位配对码。");
            return;
        }
        confirmPairingCode(code, normalizedServerUrl());
    }

    private void schedulePairAutoConfirm() {
        if (pairAutoRunnable != null) {
            handler.removeCallbacks(pairAutoRunnable);
            pairAutoRunnable = null;
        }
        String code = pairInput == null ? "" : pairInput.getText().toString().replaceAll("\\D", "");
        if (code.length() != 6) {
            return;
        }
        pairAutoRunnable = () -> {
            String current = pairInput == null ? "" : pairInput.getText().toString().replaceAll("\\D", "");
            if (current.equals(code) && !pairingInProgress) {
                confirmPairingCode(code, normalizedServerUrl());
            }
        };
        handler.postDelayed(pairAutoRunnable, 450);
    }

    private void confirmPairingCode(String code, String serverUrl) {
        String normalizedCode = code == null ? "" : code.replaceAll("\\D", "");
        if (normalizedCode.length() != 6) {
            statusText.setText(isEnglish() ? "Enter the 6-digit pair code." : "请输入 6 位配对码。");
            return;
        }
        if (pairingInProgress) {
            return;
        }

        String targetServer = normalizeServerUrl(serverUrl);
        prefs.edit()
                .putString("server_url", targetServer)
                .putString("token", normalizedToken())
                .putBoolean("inputs_locked", true)
                .apply();

        pairingInProgress = true;
        if (pairButton != null) {
            pairButton.setEnabled(false);
        }
        statusText.setText(isEnglish() ? "Pairing..." : "正在配对...");
        executor.submit(() -> {
            try {
                // Step 1: fetch pair info. This is idempotent (a read), so it's
                // safe to retry on a flaky network.
                JSONObject info = null;
                int maxInfoAttempts = 3;
                for (int attempt = 1; attempt <= maxInfoAttempts; attempt++) {
                    try {
                        JSONObject infoPayload = new JSONObject();
                        infoPayload.put("code", normalizedCode);
                        String infoText = httpPostJson(targetServer + "/api/pair/info", infoPayload.toString());
                        info = infoText.isEmpty() ? new JSONObject() : new JSONObject(infoText);
                        break;
                    } catch (HaolemeHttpException he) {
                        throw he;
                    } catch (Exception netErr) {
                        if (attempt >= maxInfoAttempts) {
                            throw netErr;
                        }
                        final int shownAttempt = attempt;
                        handler.post(() -> statusText.setText(isEnglish()
                                ? "Weak network — retrying (" + shownAttempt + "/" + (maxInfoAttempts - 1) + ")..."
                                : "网络不稳，正在重试（" + shownAttempt + "/" + (maxInfoAttempts - 1) + "）..."));
                        Thread.sleep(900L);
                    }
                }

                // Step 2: build the confirm payload (encrypt the account key to
                // the CLI's public key).
                JSONObject payload = new JSONObject();
                payload.put("code", normalizedCode);
                payload.put("appVersionCode", currentVersionCode());
                payload.put("appVersionName", currentVersionName());
                payload.put("platform", "android");
                String reusableDeviceId = reusablePairDeviceId();
                if (!reusableDeviceId.isEmpty()) {
                    payload.put("replaceDeviceId", reusableDeviceId);
                }
                String publicKey = info == null ? "" : info.optString("publicKey", "").trim();
                if (!publicKey.isEmpty()) {
                    payload.put("encryptedAccountKey", encryptAccountKeyForPair(publicKey));
                    payload.put("encryptedAccountKeyAlgorithm", "RSA-OAEP-SHA256");
                    payload.put("e2eeVersion", 1);
                }

                // Step 3: confirm exactly ONCE. Confirm consumes the pair code, so
                // it must NOT be retried — a retry after a dropped response would
                // hit an already-used/deleted code and wrongly report "expired".
                // A network error here surfaces as an honest network message.
                String responseText = httpPostJson(targetServer + "/api/pair/confirm", payload.toString());
                JSONObject response = responseText.isEmpty() ? new JSONObject() : new JSONObject(responseText);
                String deviceName = response.optString("deviceName", "").trim();
                if (deviceName.isEmpty()) {
                    deviceName = appDisplayName() + " device";
                }
                String account = response.optString("account", "default").trim();
                String pairedAt = response.optString("pairedAt", "").trim();
                String deviceId = response.optString("deviceId", "").trim();
                String finalDeviceName = deviceName;
                String finalAccount = account.isEmpty() ? "default" : account;
                String finalPairedAt = pairedAt;
                String finalDeviceId = deviceId;
                handler.post(() -> {
                    pairingInProgress = false;
                    if (pairButton != null) {
                        pairButton.setEnabled(true);
                    }
                    if (pairInput != null) {
                        pairInput.setText("");
                    }
                    prefs.edit()
                            .putString("paired_device_name", finalDeviceName)
                            .putString("paired_device_id", finalDeviceId)
                            .putString("paired_account", finalAccount)
                            .putString("paired_at", finalPairedAt)
                            .putString("paired_server_url", targetServer)
                            .apply();
                    if (!finalDeviceId.isEmpty()) {
                        selectedDeviceId = finalDeviceId;
                        prefs.edit().putString("selected_device_id", selectedDeviceId).apply();
                        cachePairedDevice(finalDeviceId, finalDeviceName, finalPairedAt);
                        currentTab = "devices";
                        buildUi();
                    }
                    statusText.setText(isEnglish() ? "Paired with " + finalDeviceName + ". Refreshing..." : "已配对 " + finalDeviceName + "，正在刷新...");
                    refreshDevices();
                    refreshRuns();
                });
            } catch (Exception e) {
                handler.post(() -> {
                    pairingInProgress = false;
                    if (pairButton != null) {
                        pairButton.setEnabled(true);
                    }
                    statusText.setText(pairFailureMessage(e));
                });
            }
        });
    }

    private String reusablePairDeviceId() {
        String deviceId = selectedDeviceId == null ? "" : selectedDeviceId.trim();
        if (!deviceId.isEmpty() && !"all".equals(deviceId)) {
            return deviceId;
        }
        String pairedId = prefs.getString("paired_device_id", "");
        return pairedId == null ? "" : pairedId.trim();
    }

    @ExperimentalGetImage
    private void startQrScan() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_REQUEST);
            return;
        }
        showQrScanner();
    }

    @Override
    @ExperimentalGetImage
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showQrScanner();
            } else {
                statusText.setText(isEnglish() ? "Camera permission is required to scan QR codes." : "需要相机权限才能扫码。");
            }
        }
    }

    @ExperimentalGetImage
    private void showQrScanner() {
        scannerVisible = true;
        decodingFrame = false;
        stopScannerCamera();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), statusBarHeight() + dp(18), dp(18), navigationBarHeight() + dp(18));
        root.setBackgroundColor(appBg());

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView backButton = actionButton(actionLabel("‹", t("back"), 1.25f));
        backButton.setOnClickListener(v -> closeScanner());
        top.addView(backButton, new LinearLayout.LayoutParams(dp(86), dp(46)));

        TextView title = new TextView(this);
        title.setText(t("scan_pair_qr"));
        title.setTextSize(22);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(textPrimary());
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        titleParams.setMargins(dp(10), 0, 0, 0);
        top.addView(title, titleParams);
        root.addView(top, matchWrap());

        scannerStatus = new TextView(this);
        scannerStatus.setText(isEnglish() ? "Point the camera at the QR code from hao login. Fill most of the view." : "把摄像头对准 hao login 的二维码，尽量让二维码占满画面。");
        scannerStatus.setTextSize(13);
        scannerStatus.setTextColor(textSecondary());
        scannerStatus.setPadding(0, dp(8), 0, dp(8));
        root.addView(scannerStatus, matchWrap());

        scannerPreviewView = new PreviewView(this);
        scannerPreviewView.setBackgroundColor(Color.BLACK);
        scannerPreviewView.setImplementationMode(PreviewView.ImplementationMode.PERFORMANCE);
        scannerPreviewView.setScaleType(PreviewView.ScaleType.FILL_CENTER);
        root.addView(scannerPreviewView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        TextView manualButton = actionButton(isEnglish() ? "Use Pair Code" : "使用配对码");
        manualButton.setOnClickListener(v -> closeScanner());
        root.addView(manualButton, matchWrap());

        setContentView(root);
        startScannerCamera();
    }

    private void closeScanner() {
        stopScannerCamera();
        scannerVisible = false;
        buildUi();
        refreshRuns();
    }

    @ExperimentalGetImage
    private void startScannerCamera() {
        if (!scannerVisible || scannerPreviewView == null) {
            return;
        }
        stopScannerCamera();

        BarcodeScannerOptions options = new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build();
        barcodeScanner = BarcodeScanning.getClient(options);

        ListenableFuture<ProcessCameraProvider> providerFuture = ProcessCameraProvider.getInstance(this);
        providerFuture.addListener(() -> {
            try {
                cameraProvider = providerFuture.get();
                bindScannerCamera(cameraProvider);
            } catch (Exception e) {
                if (scannerStatus != null) {
                    scannerStatus.setText((isEnglish() ? "Cannot open camera: " : "无法打开相机：") + e.getMessage());
                }
                stopScannerCamera();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @ExperimentalGetImage
    private void bindScannerCamera(ProcessCameraProvider provider) {
        if (!scannerVisible || scannerPreviewView == null) {
            provider.unbindAll();
            return;
        }

        Preview preview = new Preview.Builder()
                .setTargetResolution(new Size(1280, 720))
                .build();
        preview.setSurfaceProvider(scannerPreviewView.getSurfaceProvider());

        ImageAnalysis analysis = new ImageAnalysis.Builder()
                .setTargetResolution(new Size(1280, 720))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();
        analysis.setAnalyzer(executor, this::analyzeQrFrame);

        provider.unbindAll();
        provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis);
        if (scannerStatus != null) {
            scannerStatus.setText(isEnglish() ? "Scanning... keep the QR code near the center." : "正在扫描...请把二维码放在画面中央。");
        }
    }

    @ExperimentalGetImage
    private void analyzeQrFrame(ImageProxy imageProxy) {
        if (!scannerVisible || decodingFrame || barcodeScanner == null) {
            imageProxy.close();
            return;
        }
        Image mediaImage = imageProxy.getImage();
        if (mediaImage == null) {
            imageProxy.close();
            return;
        }
        decodingFrame = true;
        InputImage image = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());
        barcodeScanner.process(image)
                .addOnSuccessListener(barcodes -> {
                    for (Barcode barcode : barcodes) {
                        String text = barcode.getRawValue();
                        if (text != null && !text.trim().isEmpty()) {
                            handler.post(() -> {
                                stopScannerCamera();
                                scannerVisible = false;
                                buildUi();
                                handlePairText(text);
                            });
                            return;
                        }
                    }
                })
                .addOnFailureListener(e -> handler.post(() -> {
                    if (scannerStatus != null) {
                        scannerStatus.setText((isEnglish() ? "Scan failed: " : "扫码失败：") + e.getMessage());
                    }
                }))
                .addOnCompleteListener(task -> {
                    decodingFrame = false;
                    imageProxy.close();
                });
    }

    private void stopScannerCamera() {
        decodingFrame = false;
        if (cameraProvider != null) {
            try {
                cameraProvider.unbindAll();
            } catch (Exception ignored) {
            }
        }
        cameraProvider = null;
        if (barcodeScanner != null) {
            try {
                barcodeScanner.close();
            } catch (Exception ignored) {
            }
        }
        barcodeScanner = null;
    }

    private void handlePairIntent(Intent intent) {
        if (intent == null || intent.getData() == null) {
            return;
        }
        handlePairUri(intent.getData());
    }

    private void handlePairText(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.startsWith("haoleme://space")) {
            handleSyncSpaceUri(Uri.parse(text));
            return;
        }
        if (text.startsWith("haoleme://") || text.startsWith("haoleme://")) {
            handlePairUri(Uri.parse(text));
            return;
        }
        String code = text.replaceAll("\\D", "");
        if (code.length() == 6) {
            if (pairInput != null) {
                pairInput.setText(code);
            }
            confirmPairingCode(code, normalizedServerUrl());
            return;
        }
        statusText.setText(isEnglish() ? "QR code is not a Haoleme pair code." : "二维码不是好了么配对码。");
    }

    private void handlePairUri(Uri uri) {
        if (uri == null || (!"haoleme".equals(uri.getScheme()) && !"haoleme".equals(uri.getScheme())) || !"pair".equals(uri.getHost())) {
            return;
        }
        String code = uri.getQueryParameter("code");
        String server = uri.getQueryParameter("server");
        if (server == null || server.trim().isEmpty()) {
            server = normalizedServerUrl();
        }
        if (pairInput != null && code != null) {
            pairInput.setText(code.replaceAll("\\D", ""));
        }
        confirmPairingCode(code, server);
    }

    private void handleSyncSpaceUri(Uri uri) {
        if (uri == null || !"haoleme".equals(uri.getScheme()) || !"space".equals(uri.getHost())) {
            return;
        }
        String code = uri.getQueryParameter("code");
        String shareToken = uri.getQueryParameter("share");
        String server = uri.getQueryParameter("server");
        String deviceId = uri.getQueryParameter("deviceId");
        if (server == null || server.trim().isEmpty()) {
            server = normalizedServerUrl();
        }
        joinSyncSpaceCode(code, shareToken, server, deviceId);
    }

    private void checkForUpdates(boolean showStatus) {
        List<String> updateUrls = normalizedUpdateUrls();
        if (updateUrls.isEmpty()) {
            if (showStatus) {
                statusText.setText(isEnglish() ? "Update source unavailable." : "更新源不可用。");
            }
            return;
        }

        // Always bust cache for update manifest so manual check from settings sees the latest
        // server version immediately, instead of possibly hitting a stale cached response
        // that only gets refreshed on app restart.
        long cacheBust = System.currentTimeMillis();
        List<String> urlsToTry = new ArrayList<>();
        for (String u : updateUrls) {
            String sep = u.contains("?") ? "&" : "?";
            urlsToTry.add(u + sep + "_t=" + cacheBust);
        }

        if (showStatus) {
            statusText.setText(isEnglish() ? "Checking update..." : "正在检查更新...");
        }
        if (!updateDownloading) {
            latestDownloadUrl = "";
            latestVersionName = "";
            if (updateBadgeButton != null) {
                updateBadgeButton.setVisibility(View.GONE);
            }
        }
        executor.submit(() -> {
            try {
                String body = "";
                String usedUrl = "";
                Exception lastError = null;
                for (String updateUrl : urlsToTry) {
                    try {
                        body = httpGetPublic(updateUrl);
                        usedUrl = updateUrl;
                        break;
                    } catch (Exception e) {
                        lastError = e;
                    }
                }
                if (body.isEmpty()) {
                    throw lastError == null ? new IllegalStateException("No update source worked") : lastError;
                }

                JSONObject payload = new JSONObject(body);
                JSONObject androidUpdate = payload.optJSONObject("android");
                if (androidUpdate == null) {
                    androidUpdate = payload;
                }
                int latestCode = androidUpdate.optInt("versionCode", 0);
                String latestName = androidUpdate.optString("versionName", "");
                String latestNotes = androidUpdate.optString("notes", "");
                String latestSha256 = androidUpdate.optString("sha256", "").trim().toLowerCase(Locale.US);
                boolean forceUpdate = androidUpdate.optBoolean("forceUpdate", false);
                int minSupportedVersionCode = androidUpdate.optInt("minSupportedVersionCode", 0);
                final List<String> downloadCandidates = orderedDownloadUrls(androidUpdate, usedUrl);
                String apkUrl = downloadCandidates.isEmpty() ? "" : downloadCandidates.get(0);
                int currentCode = currentVersionCode();
                String currentName = currentVersionName();

                // Also sync latest CLI (python) version from the same manifest
                JSONObject pythonUpdate = payload.optJSONObject("python");
                String latestCliVersion = pythonUpdate != null ? pythonUpdate.optString("version", "") : "";
                if (!latestCliVersion.isEmpty()) {
                    prefs.edit().putString("latest_cli_version", latestCliVersion).apply();
                }

                handler.post(() -> {
                    if (latestCode > currentCode) {
                        latestDownloadUrls.clear();
                        latestDownloadUrls.addAll(downloadCandidates);
                        latestDownloadUrl = apkUrl;
                        latestVersionName = latestName;
                        latestApkSha256 = latestSha256;
                        String label = latestName.isEmpty() ? String.valueOf(latestCode) : latestName;
                        prefs.edit()
                                .putInt("latest_version_code", latestCode)
                                .putString("latest_version_name", latestName)
                                .putString("latest_update_notes", latestNotes)
                                .putString("latest_download_url", latestDownloadUrl)
                                .putString("latest_download_urls", new JSONArray(downloadCandidates).toString())
                                .putString("latest_apk_sha256", latestSha256)
                                .putBoolean("latest_force_update", forceUpdate)
                                .putInt("latest_min_supported_version_code", minSupportedVersionCode)
                                .apply();
                        if (updateBadgeButton != null && !latestDownloadUrl.isEmpty() && !updateDownloading) {
                            showUpdateBadge(label);
                        }
                        if (showStatus) {
                            statusText.setText(isEnglish() ? "Update available: " + label + " (current " + currentName + ")" : "发现更新：" + label + "（当前 " + currentName + "）");
                            confirmUpdateDownload();
                        }
                    } else {
                        prefs.edit()
                                .remove("latest_version_code")
                                .remove("latest_version_name")
                                .remove("latest_update_notes")
                                .remove("latest_download_url")
                                .remove("latest_download_urls")
                                .remove("latest_apk_sha256")
                                .remove("latest_force_update")
                                .remove("latest_min_supported_version_code")
                                .apply();
                        latestApkSha256 = "";
                        if (updateBadgeButton != null && !updateDownloading) {
                            updateBadgeButton.setVisibility(View.GONE);
                        }
                        if (showStatus) {
                            statusText.setText(isEnglish() ? "Already up to date: " + currentName : "已是最新版本：" + currentName);
                        }
                    }
                });
            } catch (Exception e) {
                if (showStatus) {
                    handler.post(() -> statusText.setText(isEnglish() ? "Update check failed." : "更新检查失败。"));
                }
            }
        });
    }

    private void startUpdateDownload() {
        if (updateDownloading) {
            return;
        }
        if (!ensureCanInstallPackages()) {
            return;
        }
        String url = latestDownloadUrl == null || latestDownloadUrl.isEmpty()
                ? prefs.getString("latest_download_url", "")
                : latestDownloadUrl;
        loadLatestDownloadUrlsFromPrefs();
        if ((url == null || url.trim().isEmpty()) && !latestDownloadUrls.isEmpty()) {
            url = latestDownloadUrls.get(0);
        }
        if (url == null || url.trim().isEmpty()) {
            statusText.setText(isEnglish() ? "Update unavailable. Try Refresh later." : "更新不可用，请稍后刷新重试。");
            return;
        }

        latestDownloadUrl = url.trim();
        latestVersionName = latestVersionName == null || latestVersionName.isEmpty()
                ? prefs.getString("latest_version_name", "")
                : latestVersionName;
        latestApkSha256 = latestApkSha256 == null || latestApkSha256.isEmpty()
                ? prefs.getString("latest_apk_sha256", "")
                : latestApkSha256;
        updateDownloading = true;
        updateDownloadId = -1L;
        if (updateBadgeButton != null) {
            updateBadgeButton.setVisibility(View.GONE);
        }
        statusText.setText(isEnglish() ? "Downloading update 0%..." : "正在下载更新 0%...");
        executor.submit(() -> {
            Exception lastError = null;
            try {
                if (latestDownloadUrls.isEmpty()) {
                    latestDownloadUrls.add(latestDownloadUrl);
                }
                String version = latestVersionName == null || latestVersionName.trim().isEmpty()
                        ? "latest"
                        : latestVersionName.trim();
                List<String> candidates = new ArrayList<>(latestDownloadUrls);
                for (int i = 0; i < candidates.size(); i++) {
                    String candidate = candidates.get(i);
                    if (candidate == null || candidate.trim().isEmpty()) {
                        continue;
                    }
                    latestDownloadUrl = candidate.trim();
                    final int sourceIndex = i + 1;
                    final int sourceCount = candidates.size();
                    handler.post(() -> statusText.setText(isEnglish()
                            ? "Downloading update 0% (" + sourceIndex + "/" + sourceCount + ")..."
                            : "正在下载更新 0%（" + sourceIndex + "/" + sourceCount + "）..."));
                    try {
                        File apkFile = downloadApkInApp(latestDownloadUrl, version, sourceIndex, sourceCount);
                        String expectedSha = expectedApkSha256();
                        if (!isValidSha256(expectedSha)) {
                            throw new SecurityException("missing APK checksum");
                        }
                        String actualSha = sha256ForFile(apkFile);
                        if (!expectedSha.equalsIgnoreCase(actualSha)) {
                            throw new SecurityException("APK checksum mismatch");
                        }
                        Uri apkUri = FileProvider.getUriForFile(
                                this,
                                BuildConfig.APPLICATION_ID + ".fileprovider",
                                apkFile
                        );
                        handler.post(() -> {
                            updateDownloading = false;
                            statusText.setText(isEnglish() ? "Download complete. Opening installer..." : "下载完成，正在打开安装器...");
                            openDownloadedApk(apkUri);
                        });
                        return;
                    } catch (Exception e) {
                        lastError = e;
                        latestDownloadUrls.remove(latestDownloadUrl);
                    }
                }
                throw lastError == null ? new IOException("download failed") : lastError;
            } catch (Exception e) {
                handler.post(() -> {
                    lastUpdateDownloadError = friendlyDownloadError(e);
                    retryOrShowUpdateDownloadFailed(null);
                });
            }
        });
    }

    private File downloadApkInApp(String downloadUrl, String version, int sourceIndex, int sourceCount) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(downloadUrl).openConnection();
        connection.setInstanceFollowRedirects(true);
        connection.setConnectTimeout(20000);
        connection.setReadTimeout(30000);
        connection.setRequestProperty("User-Agent", "Haoleme/" + currentVersionName() + " Android");
        int code = connection.getResponseCode();
        if (code < 200 || code >= 300) {
            throw new IOException("HTTP " + code);
        }
        int total = connection.getContentLength();
        File dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (dir == null) {
            dir = getCacheDir();
        }
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("could not create download folder");
        }
        File apkFile = new File(dir, "Haoleme-" + version + ".apk");
        byte[] buffer = new byte[64 * 1024];
        int read;
        long downloaded = 0L;
        int lastPercent = -1;
        try (InputStream input = connection.getInputStream();
             OutputStream output = new FileOutputStream(apkFile, false)) {
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
                downloaded += read;
                if (total > 0) {
                    int percent = Math.max(0, Math.min(100, (int) ((downloaded * 100L) / total)));
                    if (percent != lastPercent) {
                        lastPercent = percent;
                        final int shownPercent = percent;
                        handler.post(() -> statusText.setText(isEnglish()
                                ? "Downloading update " + shownPercent + "% (" + sourceIndex + "/" + sourceCount + ")..."
                                : "正在下载更新 " + shownPercent + "%（" + sourceIndex + "/" + sourceCount + "）..."));
                    }
                }
            }
        } finally {
            connection.disconnect();
        }
        if (apkFile.length() <= 0L) {
            throw new IOException("empty APK download");
        }
        return apkFile;
    }

    private void restoreUpdateBadgeFromPrefs() {
        int latestCode = prefs.getInt("latest_version_code", 0);
        String latestName = prefs.getString("latest_version_name", "");
        String downloadUrl = prefs.getString("latest_download_url", "");
        if (latestCode <= currentVersionCode() || downloadUrl == null || downloadUrl.trim().isEmpty()) {
            return;
        }
        loadLatestDownloadUrlsFromPrefs();
        latestDownloadUrl = downloadUrl.trim();
        latestVersionName = latestName == null ? "" : latestName.trim();
        latestApkSha256 = prefs.getString("latest_apk_sha256", "");
        showUpdateBadge(latestVersionName.isEmpty() ? String.valueOf(latestCode) : latestVersionName);
    }

    private void showUpdateBadge(String label) {
        if (updateBadgeButton == null) {
            return;
        }
        String cleanLabel = label == null || label.trim().isEmpty() ? "latest" : label.trim();
        updateBadgeButton.setText(t("update") + " " + cleanLabel);
        updateBadgeButton.setContentDescription((isEnglish() ? "Update available: " : "发现更新：") + cleanLabel);
        updateBadgeButton.setTag(cleanLabel);
        updateBadgeButton.setVisibility(View.VISIBLE);
    }

    private void confirmUpdateDownload() {
        loadLatestDownloadUrlsFromPrefs();
        if (!hasAvailableUpdate()) {
            checkForUpdates(true);
            return;
        }
        // Proactively guide for install permission so self-update can complete
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !getPackageManager().canRequestPackageInstalls()) {
            ensureCanInstallPackages();
            return;
        }
        String target = prefs.getString("latest_version_name", "");
        if (target == null || target.trim().isEmpty()) {
            target = isEnglish() ? "latest" : "最新版本";
        }
        String current = currentVersionName();
        String notes = prefs.getString("latest_update_notes", "");
        boolean forceUpdate = prefs.getBoolean("latest_force_update", false);
        String extra = "";
        if (notes != null && !notes.trim().isEmpty()) {
            extra += "\n\n" + notes.trim();
        }
        if (forceUpdate) {
            extra += isEnglish() ? "\n\nThis update is recommended before pairing new devices." : "\n\n建议先更新后再配对新设备。";
        }
        extra += isEnglish() ? "\n\nThe APK signature and checksum will be verified before install." : "\n\n安装前会校验 APK 签名和校验和。";
        String sigNoteEn = "\n\nIMPORTANT: If your currently installed Haoleme uses a different signing certificate (older dev builds), Android treats the new APK as a different app. You must uninstall the old app once via system Settings. After installing any fixed-key build (0.7.22+), all future updates will be seamless.";
        String sigNoteCn = "\n\n重要：如果你当前安装的版本签名与新版不同，系统会认为这是另一个 App。需要在手机「设置-应用」里先完全卸载旧版 Haoleme。安装过使用固定签名的版本后，以后更新可直接覆盖，无需再卸载。";
        AlertDialog d = dialogBuilder()
                .setTitle(t("update") + " " + appDisplayName())
                .setMessage(isEnglish()
                        ? "Download and install Haoleme " + target.trim() + "?\n\nCurrent version: " + current + sigNoteEn + extra
                        : "下载并安装 Haoleme " + target.trim() + "？\n\n当前版本：" + current + sigNoteCn + extra)
                .setNegativeButton(t("cancel"), null)
                .setPositiveButton(t("update"), (dialog, which) -> startUpdateDownload())
                .create();
        applyDialogStyle(d);
        d.show();
    }

    private void pollUpdateDownload(DownloadManager manager, long downloadId) {
        if (downloadId < 0 || !updateDownloading) {
            return;
        }
        executor.submit(() -> {
            Cursor cursor = null;
            try {
                DownloadManager.Query query = new DownloadManager.Query().setFilterById(downloadId);
                cursor = manager.query(query);
                if (cursor == null || !cursor.moveToFirst()) {
                    throw new IllegalStateException("download not found");
                }
                int status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                int downloaded = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                int total = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    Uri apkUri = manager.getUriForDownloadedFile(downloadId);
                    String expectedSha = expectedApkSha256();
                    if (!isValidSha256(expectedSha)) {
                        throw new SecurityException("missing APK checksum");
                    }
                    String actualSha = sha256ForUri(apkUri);
                    if (!expectedSha.equalsIgnoreCase(actualSha)) {
                        throw new SecurityException("APK checksum mismatch");
                    }
                    handler.post(() -> {
                        updateDownloading = false;
                        statusText.setText(isEnglish() ? "Download complete. Opening installer..." : "下载完成，正在打开安装器...");
                        openDownloadedApk(apkUri);
                    });
                    return;
                }
                if (status == DownloadManager.STATUS_FAILED) {
                    throw new IllegalStateException("download failed");
                }
                int percent = total > 0 ? Math.max(0, Math.min(100, (int) ((downloaded * 100L) / total))) : -1;
                handler.post(() -> {
                    if (percent >= 0) {
                        statusText.setText(isEnglish() ? "Downloading update " + percent + "%..." : "正在下载更新 " + percent + "%...");
                    } else {
                        statusText.setText(isEnglish() ? "Downloading update..." : "正在下载更新...");
                    }
                    handler.postDelayed(() -> pollUpdateDownload(manager, downloadId), 700);
                });
            } catch (Exception e) {
                handler.post(() -> {
                    retryOrShowUpdateDownloadFailed(manager);
                });
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        });
    }

    private void openDownloadedApk(Uri apkUri) {
        if (apkUri == null) {
            statusText.setText(isEnglish() ? "Update downloaded, but installer could not open." : "更新已下载，但无法打开安装器。");
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            statusText.setText(isEnglish() ? "Update downloaded. Enable APK installs and try again." : "更新已下载。请允许安装 APK 后重试。");
        }
    }

    private boolean ensureCanInstallPackages() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!getPackageManager().canRequestPackageInstalls()) {
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:" + getPackageName()));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    statusText.setText(isEnglish()
                            ? "Allow 'install unknown apps' for Haoleme, then tap Update again."
                            : "请在设置中允许「好了么」安装未知应用，然后再点更新。");
                    return false;
                } catch (Exception ignored) {
                    // fall through to generic guidance
                }
            }
        }
        return true;
    }

    private String expectedApkSha256() {
        String value = latestApkSha256 == null || latestApkSha256.trim().isEmpty()
                ? prefs.getString("latest_apk_sha256", "")
                : latestApkSha256;
        return value == null ? "" : value.trim().toLowerCase(Locale.US);
    }

    private boolean isValidSha256(String value) {
        return value != null && value.matches("(?i)[0-9a-f]{64}");
    }

    private String sha256ForUri(Uri uri) throws Exception {
        if (uri == null) {
            throw new IOException("missing downloaded APK");
        }
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            if (input == null) {
                throw new IOException("could not open downloaded APK");
            }
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return hex(digest.digest());
    }

    private String sha256ForFile(File file) throws Exception {
        if (file == null || !file.exists()) {
            throw new IOException("missing downloaded APK");
        }
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new java.io.FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return hex(digest.digest());
    }

    private String hex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format(Locale.US, "%02x", value & 0xff));
        }
        return builder.toString();
    }

    private List<String> orderedDownloadUrls(JSONObject update, String updateSourceUrl) {
        List<String> candidates = new ArrayList<>();
        JSONArray apkUrls = update.optJSONArray("apkUrls");
        addNonEmpty(candidates, apkUrls);
        JSONArray apkMirrors = update.optJSONArray("apkMirrors");
        addNonEmpty(candidates, apkMirrors);
        String singleUrl = update.optString("apkUrl", "").trim();
        if (!singleUrl.isEmpty()) {
            candidates.add(singleUrl);
        }

        String source = sourceHost(updateSourceUrl);
        List<String> ordered = new ArrayList<>();
        if (source.contains("github")) {
            String mirror = firstUrlContaining(candidates, "github");
            if (!mirror.isEmpty()) {
                ordered.add(mirror);
            }
        }
        for (String candidate : candidates) {
            if (!ordered.contains(candidate)) {
                ordered.add(candidate);
            }
        }
        return ordered;
    }

    private void loadLatestDownloadUrlsFromPrefs() {
        if (!latestDownloadUrls.isEmpty()) {
            return;
        }
        String raw = prefs.getString("latest_download_urls", "");
        if (raw == null || raw.trim().isEmpty()) {
            String single = prefs.getString("latest_download_url", "");
            if (single != null && !single.trim().isEmpty()) {
                latestDownloadUrls.add(single.trim());
            }
            return;
        }
        try {
            JSONArray values = new JSONArray(raw);
            for (int i = 0; i < values.length(); i++) {
                String value = values.optString(i, "").trim();
                if (!value.isEmpty() && !latestDownloadUrls.contains(value)) {
                    latestDownloadUrls.add(value);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void retryOrShowUpdateDownloadFailed(DownloadManager manager) {
        String failedUrl = latestDownloadUrl == null ? "" : latestDownloadUrl.trim();
        if (manager != null && updateDownloadId >= 0) {
            try {
                manager.remove(updateDownloadId);
            } catch (Exception ignored) {
            }
        }
        updateDownloadId = -1L;
        updateDownloading = false;
        latestDownloadUrls.remove(failedUrl);
        if (!latestDownloadUrls.isEmpty()) {
            latestDownloadUrl = latestDownloadUrls.get(0);
            prefs.edit()
                    .putString("latest_download_url", latestDownloadUrl)
                    .putString("latest_download_urls", new JSONArray(latestDownloadUrls).toString())
                    .apply();
            statusText.setText(isEnglish() ? "Download mirror failed. Trying another source..." : "下载镜像失败，正在尝试其他来源...");
            startUpdateDownload();
            return;
        }
        if (updateBadgeButton != null && failedUrl != null && !failedUrl.isEmpty()) {
            updateBadgeButton.setVisibility(View.VISIBLE);
        }
        String detail = lastUpdateDownloadError == null || lastUpdateDownloadError.trim().isEmpty()
                ? ""
                : (isEnglish() ? " " + lastUpdateDownloadError.trim() : " " + lastUpdateDownloadError.trim());
        statusText.setText(isEnglish() ? "Update download failed." + detail + " Current version is unchanged." : "更新下载失败。" + detail + " 当前版本不受影响。");
    }

    private String friendlyDownloadError(Exception e) {
        if (e == null) {
            return "";
        }
        Throwable cause = e;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        if (cause instanceof UnknownHostException) {
            return isEnglish() ? "DNS failed." : "DNS 解析失败。";
        }
        if (cause instanceof SocketTimeoutException) {
            return isEnglish() ? "Network timed out." : "网络超时。";
        }
        if (cause instanceof ConnectException) {
            return isEnglish() ? "Server unreachable." : "服务器不可达。";
        }
        String message = cause.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return "";
        }
        return message.trim();
    }

    private void addNonEmpty(List<String> target, JSONArray values) {
        if (values == null) {
            return;
        }
        for (int i = 0; i < values.length(); i++) {
            String value = values.optString(i, "").trim();
            if (!value.isEmpty()) {
                target.add(value);
            }
        }
    }

    private String firstUrlContaining(List<String> values, String needle) {
        for (String value : values) {
            String host = sourceHost(value);
            if (host.contains(needle)) {
                return value;
            }
        }
        return "";
    }

    private String sourceHost(String value) {
        try {
            String host = Uri.parse(value).getHost();
            return host == null ? "" : host;
        } catch (Exception e) {
            return value;
        }
    }

    private String normalizedServerUrl() {
        return normalizeServerUrl(prefs.getString("server_url", DEFAULT_SERVER_URL));
    }

    private boolean shouldReplaceSavedServerUrl(String rawSavedServerUrl, String normalizedSavedServerUrl) {
        String raw = trimTrailingSlash(rawSavedServerUrl == null ? "" : rawSavedServerUrl.trim());
        if (raw.isEmpty() || isLegacyServerUrl(raw)) {
            return true;
        }
        String bundledDefault = trimTrailingSlash(DEFAULT_SERVER_URL);
        if (!CANONICAL_SERVER_URL.equalsIgnoreCase(bundledDefault) && CANONICAL_SERVER_URL.equalsIgnoreCase(raw)) {
            return true;
        }
        return !normalizedSavedServerUrl.equals(raw);
    }

    private boolean shouldClearAuthForServerReplacement(String rawSavedServerUrl, String normalizedSavedServerUrl) {
        String raw = trimTrailingSlash(rawSavedServerUrl == null ? "" : rawSavedServerUrl.trim());
        if (raw.isEmpty()) {
            return false;
        }
        return !normalizedSavedServerUrl.equals(raw);
    }

    private String normalizeServerUrl(String raw) {
        raw = raw == null ? "" : raw.trim();
        if (raw.endsWith("/")) {
            raw = raw.substring(0, raw.length() - 1);
        }
        String bundledDefault = trimTrailingSlash(DEFAULT_SERVER_URL);
        if (!CANONICAL_SERVER_URL.equalsIgnoreCase(bundledDefault) && CANONICAL_SERVER_URL.equalsIgnoreCase(raw)) {
            return bundledDefault;
        }
        if (isLegacyServerUrl(raw)) {
            raw = DEFAULT_SERVER_URL;
        }
        if (raw.isEmpty()) {
            raw = DEFAULT_SERVER_URL;
        }
        return raw;
    }

    private boolean isLegacyServerUrl(String raw) {
        if (raw == null) {
            return false;
        }
        String value = raw.trim();
        value = trimTrailingSlash(value);
        for (String legacy : LEGACY_SERVER_URLS) {
            if (legacy.equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    private String trimTrailingSlash(String value) {
        value = value == null ? "" : value.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private String normalizedToken() {
        return accountToken();
    }

    private JSONArray decryptRuns(JSONArray runs) {
        JSONArray decrypted = new JSONArray();
        for (int i = 0; i < runs.length(); i++) {
            JSONObject run = runs.optJSONObject(i);
            if (run == null) {
                continue;
            }
            decrypted.put(decryptRun(run));
        }
        return decrypted;
    }

    private JSONObject decryptRun(JSONObject run) {
        return decryptRun(run, null);
    }

    private JSONObject decryptRun(JSONObject run, JSONArray extraChunks) {
        JSONObject copy;
        JSONObject e2ee = run.optJSONObject("e2ee");
        // Decrypt whenever a ciphertext is present — don't gate on an exact
        // version int (a storage/normalization quirk can leave v=0/missing,
        // which previously skipped decryption and leaked the "Encrypted command"
        // placeholder while output still decrypted). AES-GCM validates anyway.
        if (e2ee == null || e2ee.optString("ciphertext", "").isEmpty()) {
            copy = run;
        } else {
            try {
                byte[] key = accountEncryptionKeyBytes();
                byte[] nonce = base64UrlDecode(e2ee.optString("nonce", ""));
                byte[] ciphertext = base64UrlDecode(e2ee.optString("ciphertext", ""));
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
                cipher.updateAAD(run.optString("id", "").getBytes(StandardCharsets.UTF_8));
                byte[] plaintext = cipher.doFinal(ciphertext);
                JSONObject fields = new JSONObject(new String(plaintext, StandardCharsets.UTF_8));
                copy = new JSONObject(run.toString());
                copy.put("commandText", fields.optString("commandText", copy.optString("commandText", "")));
                copy.put("cwd", fields.optString("cwd", copy.optString("cwd", "")));
                copy.put("cliVersion", fields.optString("cliVersion", copy.optString("cliVersion", "")));
                copy.put("os", fields.optString("os", copy.optString("os", "")));
                copy.put("hostname", fields.optString("hostname", copy.optString("hostname", "")));
                copy.put("stdoutTail", fields.optString("stdoutTail", ""));
                copy.put("stderrTail", fields.optString("stderrTail", ""));
                copy.put("outputTail", fields.optString("outputTail", ""));
                if (fields.has("command")) {
                    copy.put("command", fields.optJSONArray("command"));
                }
            } catch (Exception ignored) {
                try {
                    copy = new JSONObject(run.toString());
                    copy.put("commandText", "Encrypted run. Re-pair this app to decrypt.");
                    copy.put("stdoutTail", "");
                    copy.put("stderrTail", "");
                    copy.put("outputTail", "");
                } catch (Exception nested) {
                    return run;
                }
            }
        }
        try {
            String runId = copy.optString("id", "");
            StringBuilder merged = new StringBuilder(copy.optString("outputTail", ""));
            String stdout = copy.optString("stdoutTail", "");
            String stderr = copy.optString("stderrTail", "");
            JSONArray chunks = run.optJSONArray("outputChunks");
            if (chunks != null) {
                merged.append(decryptOutputChunks(runId, chunks));
            }
            if (extraChunks != null) {
                merged.append(decryptOutputChunks(runId, extraChunks));
            }
            if (merged.length() > 0) {
                copy.put("outputTail", merged.toString());
            } else if (!stdout.isEmpty() || !stderr.isEmpty()) {
                copy.put("outputTail", consoleOutput(copy));
            }
            return copy;
        } catch (Exception ignored) {
            return copy;
        }
    }

    private String decryptOutputChunks(String runId, JSONArray chunks) {
        if (chunks == null || chunks.length() == 0) {
            return "";
        }
        StringBuilder merged = new StringBuilder();
        for (int i = 0; i < chunks.length(); i++) {
            JSONObject chunk = chunks.optJSONObject(i);
            if (chunk == null) {
                continue;
            }
            String piece = decryptOutputChunk(runId, chunk);
            if (!piece.isEmpty()) {
                merged.append(piece);
            }
        }
        return merged.toString();
    }

    private String decryptOutputChunk(String runId, JSONObject chunk) {
        if (chunk == null || chunk.optInt("v", 0) != 1) {
            return "";
        }
        try {
            byte[] key = accountEncryptionKeyBytes();
            byte[] nonce = base64UrlDecode(chunk.optString("nonce", ""));
            byte[] ciphertext = base64UrlDecode(chunk.optString("ciphertext", ""));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
            cipher.updateAAD(runId.getBytes(StandardCharsets.UTF_8));
            byte[] plaintext = cipher.doFinal(ciphertext);
            JSONObject fields = new JSONObject(new String(plaintext, StandardCharsets.UTF_8));
            String output = fields.optString("outputTail", "");
            if (!output.isEmpty()) {
                return output;
            }
            String stdout = fields.optString("stdoutTail", "");
            String stderr = fields.optString("stderrTail", "");
            StringBuilder combined = new StringBuilder();
            if (!stdout.isEmpty()) {
                combined.append(stdout);
            }
            if (!stderr.isEmpty()) {
                if (combined.length() > 0) {
                    combined.append("\n");
                }
                combined.append(stderr);
            }
            return combined.toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private String encryptAccountKeyForPair(String publicKeyPem) throws Exception {
        byte[] key = accountEncryptionKeyBytes();
        String normalized = publicKeyPem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.decode(normalized, Base64.DEFAULT)));
        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        cipher.init(
                Cipher.ENCRYPT_MODE,
                publicKey,
                new OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT)
        );
        return base64UrlEncode(cipher.doFinal(key));
    }

    private byte[] accountEncryptionKeyBytes() {
        String saved = prefs.getString("encryption_key_b64", "");
        if (saved != null && !saved.isEmpty()) {
            try {
                byte[] decoded = base64UrlDecode(saved);
                if (decoded.length == 32) {
                    return decoded;
                }
            } catch (Exception ignored) {
            }
        }
        byte[] random = new byte[32];
        new SecureRandom().nextBytes(random);
        prefs.edit().putString("encryption_key_b64", base64UrlEncode(random)).apply();
        return random;
    }

    private String base64UrlEncode(byte[] value) {
        return Base64.encodeToString(value, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }

    private byte[] base64UrlDecode(String value) {
        return Base64.decode(value, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }

    private String accountToken() {
        String token = prefs.getString("token", "");
        if (token != null && !token.isEmpty()) {
            return token;
        }
        byte[] random = new byte[32];
        new SecureRandom().nextBytes(random);
        token = Base64.encodeToString(random, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
        prefs.edit().putString("token", token).apply();
        return token;
    }

    private List<String> normalizedUpdateUrls() {
        List<String> urls = new ArrayList<>();
        String raw = DEFAULT_UPDATE_URLS;
        for (String part : raw.split("[,\\s]+")) {
            String url = part.trim();
            if (!url.isEmpty()) {
                urls.add(url);
            }
        }
        // Always ensure a reliable public fallback so the app can self-update from GitHub
        // manifest even if primary server is unreachable or has stale data.
        String gh = "https://raw.githubusercontent.com/HaolemeApp/Haoleme/main/update.json";
        if (!urls.contains(gh)) {
            urls.add(gh);
        }
        return urls;
    }

    private int currentVersionCode() {
        try {
            if (Build.VERSION.SDK_INT >= 28) {
                return (int) getPackageManager().getPackageInfo(getPackageName(), 0).getLongVersionCode();
            }
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
        } catch (Exception e) {
            return 0;
        }
    }

    private String currentVersionName() {
        try {
            String name = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            return name == null ? String.valueOf(currentVersionCode()) : name;
        } catch (Exception e) {
            return String.valueOf(currentVersionCode());
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < 26) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Command runs",
                NotificationManager.IMPORTANCE_DEFAULT
        );
        channel.setDescription(isEnglish()
                ? "Notifications when Haoleme commands finish."
                : "好了么命令结束时发送通知。");
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        manager.createNotificationChannel(channel);
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 10);
        }
    }

    private String statusSuffix(JSONObject run) {
        int exitCode = run.optInt("exitCode", Integer.MIN_VALUE);
        String started = run.optString("startedAt", "");
        String ended = run.optString("endedAt", "");
        String time = ended.isEmpty() || "null".equals(ended) ? started : ended;
        if (exitCode == Integer.MIN_VALUE) {
            return " · " + time;
        }
        return " · exit " + exitCode + " · " + time;
    }

    private String statusExitSuffix(JSONObject run) {
        int exitCode = run.optInt("exitCode", Integer.MIN_VALUE);
        return exitCode == Integer.MIN_VALUE ? "" : " · exit " + exitCode;
    }

    private String statusLabel(String status) {
        if ("succeeded".equals(status)) {
            return isEnglish() ? "SUCCEEDED" : "成功";
        }
        if ("failed".equals(status)) {
            return isEnglish() ? "FAILED" : "失败";
        }
        if ("cancelled".equals(status)) {
            return isEnglish() ? "CANCELLED" : "已取消";
        }
        if ("created".equals(status) || "running".equals(status)) {
            return isEnglish() ? "RUNNING" : "运行中";
        }
        return status == null || status.isEmpty() ? (isEnglish() ? "UNKNOWN" : "未知") : status.toUpperCase(Locale.US);
    }

    private int statusBadgeColor(String status) {
        if ("succeeded".equals(status)) {
            return isDarkTheme() ? color("#123524") : color("#EAF7EF");
        }
        if ("failed".equals(status) || "cancelled".equals(status)) {
            return isDarkTheme() ? color("#3B1518") : color("#FDECEC");
        }
        return isDarkTheme() ? color("#122C3A") : color("#EEF2F7");
    }

    private int statusColor(String status) {
        if ("succeeded".equals(status)) {
            return color("#16794C");
        }
        if ("failed".equals(status) || "cancelled".equals(status)) {
            return color("#B42318");
        }
        return color("#176B87");
    }

    private String durationText(JSONObject run) {
        long started = parseTimestamp(run.optString("startedAt", ""));
        String endedRaw = run.optString("endedAt", "");
        long ended = endedRaw.isEmpty() || "null".equals(endedRaw)
                ? parseTimestamp(run.optString("updatedAt", ""))
                : parseTimestamp(endedRaw);
        if (started <= 0 || ended <= 0 || ended < started) {
            return isEnglish() ? "Duration unknown" : "时长未知";
        }
        long seconds = Math.max(0, (ended - started) / 1000L);
        if (seconds < 60) {
            return seconds + "s";
        }
        long minutes = seconds / 60;
        long remaining = seconds % 60;
        if (minutes < 60) {
            return minutes + "m " + remaining + "s";
        }
        long hours = minutes / 60;
        return hours + "h " + (minutes % 60) + "m";
    }

    private long parseTimestamp(String raw) {
        if (raw == null || raw.trim().isEmpty() || "null".equals(raw)) {
            return 0L;
        }
        String value = raw.trim();
        int dot = value.indexOf('.');
        if (dot > 0) {
            int zone = value.indexOf('Z', dot);
            if (zone < 0) {
                zone = value.indexOf('+', dot);
            }
            if (zone < 0) {
                zone = value.length();
            }
            value = value.substring(0, dot) + value.substring(zone);
        }
        String[] patterns = new String[]{
                "yyyy-MM-dd'T'HH:mm:ss.SSSX",
                "yyyy-MM-dd'T'HH:mm:ssX",
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                "yyyy-MM-dd'T'HH:mm:ss'Z'"
        };
        for (String pattern : patterns) {
            try {
                SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
                format.setTimeZone(TimeZone.getTimeZone("UTC"));
                Date parsed = format.parse(value);
                if (parsed != null) {
                    return parsed.getTime();
                }
            } catch (Exception ignored) {
            }
        }
        return 0L;
    }

    private String formatDeviceTimestamp(String raw) {
        if (raw == null || raw.trim().isEmpty() || "null".equals(raw)) {
            return "";
        }
        long millis = parseTimestamp(raw);
        if (millis <= 0L) {
            return "";
        }
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA);
        format.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
        return format.format(new Date(millis));
    }

    private String latestOutputLine(JSONObject run) {
        String output = run.optString("outputTail", "");
        if (output.isEmpty()) {
            output = run.optString("stderrTail", "");
        }
        if (output.isEmpty()) {
            output = run.optString("stdoutTail", "");
        }
        String[] lines = output.split("\\r?\\n");
        for (int i = lines.length - 1; i >= 0; i--) {
            String line = lines[i].trim();
            if (!line.isEmpty()) {
                return trim(line);
            }
        }
        return "";
    }

    private String trim(String value) {
        if (value.length() <= 240) {
            return value;
        }
        return value.substring(value.length() - 240);
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private int statusBarHeight() {
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId <= 0) {
            return 0;
        }
        return getResources().getDimensionPixelSize(resourceId);
    }

    private int navigationBarHeight() {
        int resourceId = getResources().getIdentifier("navigation_bar_height", "dimen", "android");
        if (resourceId <= 0) {
            return 0;
        }
        return getResources().getDimensionPixelSize(resourceId);
    }

    private int color(String value) {
        return Color.parseColor(value);
    }

    private int appBg() {
        // iOS-style grouped background: a neutral gray so white cards pop.
        return isDarkTheme() ? color("#0E0E10") : color("#F2F2F7");
    }

    private int surfaceBg() {
        return isDarkTheme() ? color("#202020") : Color.WHITE;
    }

    private int cardBg() {
        return isDarkTheme() ? color("#1C1C1E") : color("#FFFFFF");
    }

    private int cardStroke() {
        return isDarkTheme() ? color("#2C2C2E") : color("#E5E5EA");
    }

    private int iconChipBg() {
        return isDarkTheme() ? color("#2B2B31") : color("#F5F7FA");
    }

    private int iconChipStroke() {
        return isDarkTheme() ? color("#3A3A42") : color("#EBEEF4");
    }

    private int navDockBg() {
        return isDarkTheme() ? color("#232327") : color("#FCFCFD");
    }

    private int navDockStroke() {
        return isDarkTheme() ? color("#393942") : color("#E1E5EC");
    }

    private int textPrimary() {
        return isDarkTheme() ? color("#F4F4F5") : color("#1F2933");
    }

    private int textSecondary() {
        return isDarkTheme() ? color("#98989F") : color("#8E8E93");
    }

    private int chevronColor() {
        return isDarkTheme() ? color("#5A5A5E") : color("#C7C7CC");
    }

    private int tabSelectedBg() {
        return isDarkTheme() ? color("#F4F4F5") : color("#111827");
    }

    private int tabSelectedText() {
        return isDarkTheme() ? color("#111827") : Color.WHITE;
    }

    private int tabMutedText() {
        return isDarkTheme() ? color("#C4C4CC") : color("#5D6674");
    }

    private int updateAccent() {
        return isDarkTheme() ? color("#F87171") : color("#DC2626");
    }

    private int buttonBg() {
        return isDarkTheme() ? color("#2B2B2F") : color("#FFFFFF");
    }

    private int inputBg() {
        return isDarkTheme() ? color("#26262A") : Color.WHITE;
    }

    private int consoleBg() {
        return isDarkTheme() ? color("#0F1012") : color("#FFFFFF");
    }

    private int consoleText() {
        return isDarkTheme() ? color("#E8EAF0") : color("#1F2933");
    }

    private int consoleStroke() {
        return isDarkTheme() ? color("#2C2D33") : color("#DDE3EC");
    }

    private int surfaceStroke() {
        return isDarkTheme() ? color("#2C2C2E") : color("#E5E5EA");
    }

    private int gpuTrackColor() {
        return isDarkTheme() ? color("#9CA3AF") : color("#6B7280");
    }

    private void styleActionButton(Button button) {
        button.setTextColor(textPrimary());
        button.setBackground(roundedBg(buttonBg(), 10, surfaceStroke()));
        button.setMinHeight(dp(42));
        button.setPadding(dp(10), 0, dp(10), 0);
        button.setElevation(0);
    }

    private void styleInput(EditText input) {
        input.setTextColor(textPrimary());
        input.setHintTextColor(textSecondary());
        input.setBackground(roundedBg(inputBg(), 14, surfaceStroke()));
        input.setPadding(dp(12), 0, dp(12), 0);
    }

    private View statusDot(int fill) {
        View dot = new View(this);
        dot.setBackground(roundedBg(fill, 99, Color.TRANSPARENT));
        return dot;
    }

    private GradientDrawable roundedBg(int fill, int radiusDp) {
        return roundedBg(fill, radiusDp, surfaceStroke());
    }

    private GradientDrawable roundedBg(int fill, int radiusDp, int stroke) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(fill);
        bg.setCornerRadius(dp(radiusDp));
        if (Color.alpha(stroke) > 0) {
            bg.setStroke(Math.max(1, dp(1)), stroke);
        }
        return bg;
    }

    private void applyDialogStyle(AlertDialog dialog) {
        if (dialog == null) return;
        Window w = dialog.getWindow();
        if (w != null) {
            // Give the popup a nice rounded card look with our theme's surface color and subtle border for "质感"
            w.setBackgroundDrawable(roundedBg(cardBg(), 16, cardStroke()));
            // Slight dim for depth
            w.setDimAmount(0.5f);
        }

        // Ensure text is readable in dark mode (title, message, list items, buttons)
        dialog.setOnShowListener(dlg -> {
            int titleId = getResources().getIdentifier("alertTitle", "id", "android");
            if (titleId != 0) {
                TextView tv = dialog.findViewById(titleId);
                if (tv != null) tv.setTextColor(textPrimary());
            }
            int msgId = getResources().getIdentifier("message", "id", "android");
            if (msgId != 0) {
                TextView tv = dialog.findViewById(msgId);
                if (tv != null) tv.setTextColor(textPrimary());
            }
            // List items (for SingleChoice / setItems)
            ListView lv = dialog.getListView();
            if (lv != null) {
                for (int i = 0; i < lv.getChildCount(); i++) {
                    View child = lv.getChildAt(i);
                    if (child instanceof CheckedTextView) {
                        ((CheckedTextView) child).setTextColor(textPrimary());
                    } else if (child instanceof TextView) {
                        ((TextView) child).setTextColor(textPrimary());
                    }
                }
            }
            // Buttons
            Button pos = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
            if (pos != null) pos.setTextColor(textPrimary());
            Button neg = dialog.getButton(DialogInterface.BUTTON_NEGATIVE);
            if (neg != null) neg.setTextColor(textPrimary());
            Button neu = dialog.getButton(DialogInterface.BUTTON_NEUTRAL);
            if (neu != null) neu.setTextColor(textPrimary());
        });
    }

    private AlertDialog.Builder dialogBuilder() {
        int style = isDarkTheme() ? R.style.AppDialog_Dark : R.style.AppDialog;
        return new AlertDialog.Builder(this, style);
    }

    private static class ComputerIconView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int strokeColor;

        ComputerIconView(Context context, int strokeColor) {
            super(context);
            this.strokeColor = strokeColor;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            float stroke = Math.max(2f, Math.min(w, h) * 0.09f);
            paint.setColor(strokeColor);
            paint.setStrokeWidth(stroke);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);

            float left = stroke;
            float top = h * 0.14f;
            float right = w - stroke;
            float bottom = h * 0.68f;
            float radius = h * 0.08f;
            canvas.drawRoundRect(left, top, right, bottom, radius, radius, paint);

            float centerX = w / 2f;
            canvas.drawLine(centerX, bottom, centerX, h * 0.83f, paint);
            canvas.drawLine(w * 0.27f, h * 0.88f, w * 0.73f, h * 0.88f, paint);
        }
    }

    private static class QrIconView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int fillColor;

        QrIconView(Context context, int fillColor) {
            super(context);
            this.fillColor = fillColor;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float size = Math.min(getWidth(), getHeight());
            float unit = size / 7f;
            float left = (getWidth() - size) / 2f;
            float top = (getHeight() - size) / 2f;
            paint.setColor(fillColor);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);

            drawFinder(canvas, left, top, unit);
            drawFinder(canvas, left + unit * 4f, top, unit);
            drawFinder(canvas, left, top + unit * 4f, unit);

            drawModule(canvas, left, top, unit, 4, 4);
            drawModule(canvas, left, top, unit, 6, 4);
            drawModule(canvas, left, top, unit, 3, 5);
            drawModule(canvas, left, top, unit, 5, 5);
            drawModule(canvas, left, top, unit, 4, 6);
            drawModule(canvas, left, top, unit, 6, 6);
        }

        private void drawFinder(Canvas canvas, float left, float top, float unit) {
            float radius = unit * 0.18f;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(2f, unit * 0.45f));
            paint.setColor(fillColor);
            canvas.drawRoundRect(left + unit * 0.25f, top + unit * 0.25f, left + unit * 2.75f, top + unit * 2.75f, radius, radius, paint);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawRoundRect(left + unit * 1.05f, top + unit * 1.05f, left + unit * 1.95f, top + unit * 1.95f, radius, radius, paint);
        }

        private void drawModule(Canvas canvas, float left, float top, float unit, int x, int y) {
            float inset = unit * 0.12f;
            paint.setStyle(Paint.Style.FILL);
            canvas.drawRoundRect(
                    left + unit * x + inset,
                    top + unit * y + inset,
                    left + unit * (x + 1) - inset,
                    top + unit * (y + 1) - inset,
                    unit * 0.12f,
                    unit * 0.12f,
                    paint
            );
        }
    }

    private static class HaolemeHttpException extends Exception {
        final int statusCode;
        final String body;

        HaolemeHttpException(int statusCode, String body) {
            super("HTTP " + statusCode);
            this.statusCode = statusCode;
            this.body = body == null ? "" : body;
        }

        String errorCode() {
            try {
                return new JSONObject(body).optString("code", "");
            } catch (Exception ignored) {
                return "";
            }
        }

        String errorMessage() {
            try {
                JSONObject payload = new JSONObject(body);
                String error = payload.optString("error", "").trim();
                if (!error.isEmpty()) {
                    return error;
                }
                return payload.optString("message", "").trim();
            } catch (Exception ignored) {
                return "";
            }
        }
    }

    private static class ThemeIconView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int fillColor;

        ThemeIconView(Context context, int fillColor) {
            super(context);
            this.fillColor = fillColor;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float size = Math.min(getWidth(), getHeight());
            float stroke = Math.max(2.2f, size * 0.09f);
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float radius = size * 0.32f;

            paint.setColor(fillColor);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(stroke);
            canvas.drawCircle(cx, cy, radius, paint);

            paint.setStyle(Paint.Style.FILL);
            canvas.save();
            canvas.clipRect(0, 0, cx, getHeight());
            canvas.drawCircle(cx, cy, radius - stroke * 0.75f, paint);
            canvas.restore();
        }
    }

    private static class QuietHoursIconView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int fillColor;

        QuietHoursIconView(Context context, int fillColor) {
            super(context);
            this.fillColor = fillColor;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float size = Math.min(getWidth(), getHeight());
            float pad = size * 0.18f;
            float left = (getWidth() - size) / 2f + pad;
            float top = (getHeight() - size) / 2f + pad;
            float right = (getWidth() + size) / 2f - pad;
            float bottom = (getHeight() + size) / 2f - pad;
            float stroke = Math.max(2f, size * 0.08f);
            float cx = (left + right) / 2f;

            paint.setColor(fillColor);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(stroke);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);

            float bellTop = top + size * 0.08f;
            float bellBottom = bottom - size * 0.2f;
            RectF bell = new RectF(left + size * 0.12f, bellTop, right - size * 0.12f, bellBottom);
            canvas.drawArc(bell, 200f, 140f, false, paint);
            canvas.drawLine(left + size * 0.18f, bellBottom, right - size * 0.18f, bellBottom, paint);
            canvas.drawLine(cx, bellTop - size * 0.02f, cx, top, paint);
            canvas.drawLine(cx - size * 0.12f, bottom - size * 0.08f, cx + size * 0.12f, bottom - size * 0.08f, paint);

            canvas.drawLine(left + size * 0.08f, bottom - size * 0.04f, right - size * 0.08f, top + size * 0.12f, paint);
        }
    }

    private static class LanguageIconView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int fillColor;

        LanguageIconView(Context context, int fillColor) {
            super(context);
            this.fillColor = fillColor;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float size = Math.min(getWidth(), getHeight());
            float stroke = Math.max(2f, size * 0.08f);
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float radius = size * 0.3f;

            paint.setColor(fillColor);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(stroke);
            canvas.drawCircle(cx, cy, radius, paint);
            canvas.drawLine(cx - radius, cy, cx + radius, cy, paint);
            canvas.drawArc(new RectF(cx - radius, cy - radius, cx + radius, cy + radius), -70f, 200f, false, paint);

            paint.setStyle(Paint.Style.FILL);
            paint.setTextSize(size * 0.34f);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            Paint.FontMetrics metrics = paint.getFontMetrics();
            float textY = cy - (metrics.ascent + metrics.descent) / 2f;
            canvas.drawText("文", cx, textY, paint);
        }
    }

    private static class MaskIconView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int fillColor;

        MaskIconView(Context context, int fillColor) {
            super(context);
            this.fillColor = fillColor;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float size = Math.min(getWidth(), getHeight());
            float stroke = Math.max(2.2f, size * 0.085f);
            float left = (getWidth() - size) / 2f + size * 0.16f;
            float top = (getHeight() - size) / 2f + size * 0.3f;
            float right = left + size * 0.68f;
            float bottom = top + size * 0.4f;

            paint.setColor(fillColor);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(stroke);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            canvas.drawOval(new RectF(left, top, right, bottom), paint);

            paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(getWidth() / 2f, getHeight() / 2f, size * 0.085f, paint);

            paint.setStyle(Paint.Style.STROKE);
            canvas.drawLine(size * 0.22f, size * 0.8f, size * 0.78f, size * 0.2f, paint);
        }
    }

    private static class DiagnosticsIconView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int fillColor;

        DiagnosticsIconView(Context context, int fillColor) {
            super(context);
            this.fillColor = fillColor;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float size = Math.min(getWidth(), getHeight());
            float stroke = Math.max(2.2f, size * 0.085f);
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;

            paint.setColor(fillColor);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(stroke);
            paint.setStrokeCap(Paint.Cap.ROUND);
            canvas.drawCircle(cx, cy, size * 0.31f, paint);
            canvas.drawLine(cx, cy + size * 0.01f, cx, cy + size * 0.16f, paint);

            paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(cx, cy - size * 0.15f, size * 0.04f, paint);
        }
    }
}
