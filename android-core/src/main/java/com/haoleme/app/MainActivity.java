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
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
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
import java.util.concurrent.RejectedExecutionException;

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
    private static final long POLL_MS = 6000L;
    private static final long LIST_ACTIVE_POLL_MS = 3500L;
    private static final long CONSOLE_RUNNING_POLL_MS = 1000L;
    private static final long BACKGROUND_OUTPUT_SYNC_COOLDOWN_MS = 15000L;
    private static final int HTTP_CONNECT_TIMEOUT_MS = 8000;
    private static final int HTTP_READ_TIMEOUT_MS = 12000;
    private static final int HTTP_LIST_READ_TIMEOUT_MS = 6500;
    private static final int MAX_BACKGROUND_OUTPUT_SYNC = 1;
    private static final String CACHE_RUNS = "cached_runs_json";
    private static final String CACHE_RUNS_AT = "cached_runs_at";
    private static final String CACHE_RUNS_PREFIX = "cached_runs_json_";
    private static final String CACHE_RUNS_AT_PREFIX = "cached_runs_at_";
    private static final String CACHE_DEVICES = "cached_devices_json";
    private static final String CACHE_RUN_PREFIX = "cached_run_";
    private static final String CPU_HISTORY_PREFIX = "cpu_history_";
    private static final String PREF_STATUS_FILTER = "status_filter";
    private static final String PREF_ARCHIVED_RUNS = "archived_run_ids";
    private static final String PREF_PINNED_RUNS = "pinned_run_ids";
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
    private static final String PREF_REVOKED_DEVICE_IDS = "revoked_device_ids";
    private static final String PREF_PENDING_RUN_DELETES = "pending_run_delete_ids";
    private static final String PREF_LANGUAGE_MODE = "language_mode";
    private static final String PREF_APP_CLIENT_ID = "app_client_id";
    private static final String TAG_RUN_PREFIX = "run:";
    private static final String TAG_RUN_DOT = "run_dot";
    private static final String TAG_RUN_COMMAND = "run_command";
    private static final String TAG_RUN_STATUS = "run_status";
    private static final String TAG_RUN_META = "run_meta";
    private static final String TAG_RUN_OUTPUT = "run_output";
    private static final int CONSOLE_RENDER_INITIAL_CHARS = 60000;
    private static final int CONSOLE_RENDER_STEP_CHARS = 60000;
    private static final int CPU_HISTORY_MAX_POINTS = 36;
    private static final String THEME_LIGHT = "light";
    private static final String THEME_DARK = "dark";
    private static final String LANG_ZH = "zh";
    private static final String LANG_EN = "en";
    private static final String DEFAULT_SERVER_URL = BuildConfig.HAOLEME_DEFAULT_SERVER_URL;
    private static final String CANONICAL_SERVER_URL = "https://api.haoleme.cloud";
    private static final String DEFAULT_UPDATE_URLS = BuildConfig.HAOLEME_UPDATE_URLS;
    private static final String[] LEGACY_SERVER_URLS = new String[]{
            "http://api.haoleme.cloud"
    };

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newFixedThreadPool(5);
    private final ExecutorService updateExecutor = Executors.newSingleThreadExecutor();
    private volatile boolean destroyed;
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
    private TextView deviceHeartbeatText;
    private FrameLayout monitorDeck;
    private int monitorPageIndex = 0;
    private int gpuMetricIndex = 0;
    private static final int GPU_METRIC_COUNT = 3;
    private android.view.GestureDetector gpuGestureDetector;
    private android.view.GestureDetector monitorGestureDetector;
    private HorizontalScrollView devicesScrollView;
    private LinearLayout devicesContainer;
    private LinearLayout runsContainer;
    private ScrollView homeRunsScrollView;
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
    private volatile boolean pendingRunDeleteSyncing = false;
    private final Object refreshStateLock = new Object();
    private boolean runsRefreshInFlight = false;
    private boolean runsRefreshQueued = false;
    private boolean runsRefreshQueuedManual = false;
    private boolean devicesRefreshInFlight = false;
    private boolean devicesRefreshQueued = false;
    private boolean devicesRefreshQueuedManual = false;
    private boolean runDetailRefreshInFlight = false;
    private boolean runDetailRefreshQueued = false;
    private String runDetailRefreshQueuedId = "";
    private boolean runDetailRefreshQueuedShowLoading = false;
    private volatile boolean backgroundOutputSyncInFlight = false;
    private volatile long lastBackgroundOutputSyncAt = 0L;

    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                if (selectedRunId == null) {
                    refreshHome(false);
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
            syncPendingRunDeletesAsync(false);
            refreshHome(false);
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
        if (selectedRunId == null && "home".equals(currentTab) && hasPairedDevice()) {
            syncPendingRunDeletesAsync(false);
            refreshHome(false);
        }
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
        destroyed = true;
        stopScannerCamera();
        handler.removeCallbacksAndMessages(null);
        executor.shutdownNow();
        updateExecutor.shutdownNow();
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY);
        super.onDestroy();
    }

    private void submitBackground(Runnable task) {
        if (destroyed || executor.isShutdown()) {
            return;
        }
        try {
            executor.submit(task);
        } catch (RejectedExecutionException ignored) {
            // A lifecycle callback can race with onDestroy while the pool shuts down.
        }
    }

    private void submitUpdateBackground(Runnable task) {
        if (destroyed || updateExecutor.isShutdown()) {
            return;
        }
        try {
            updateExecutor.submit(task);
        } catch (RejectedExecutionException ignored) {
            // Update checks can finish while the activity is being replaced.
        }
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
        deviceHeartbeatText = null;
        devicesScrollView = null;
        devicesContainer = null;
        runsContainer = null;
        renameDeviceButton = null;
        revokeDeviceButton = null;
        clearDeviceRunsButton = null;
        homeRunsScrollView = null;

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
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
        );
        header.addView(headerText, headerTextParams);

        updateBadgeButton = null;

        root.addView(header, matchWrap());

        statusText = new TextView(this);
        statusText.setText(t("connecting"));
        statusText.setTextSize(11);
        statusText.setTextColor(textSecondary());
        statusText.setGravity(Gravity.CENTER);
        statusText.setPadding(0, dp(1), 0, dp(4));
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
        content.addView(homeOverviewCard(), matchWrap());

        devicesScrollView = new HorizontalScrollView(this);
        devicesScrollView.setHorizontalScrollBarEnabled(false);
        devicesScrollView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        devicesContainer = new LinearLayout(this);
        devicesContainer.setOrientation(LinearLayout.HORIZONTAL);
        devicesContainer.setGravity(Gravity.CENTER_VERTICAL);
        devicesContainer.setPadding(0, 0, dp(8), 0);
        devicesScrollView.addView(devicesContainer, new HorizontalScrollView.LayoutParams(
                HorizontalScrollView.LayoutParams.WRAP_CONTENT,
                HorizontalScrollView.LayoutParams.WRAP_CONTENT
        ));
        LinearLayout.LayoutParams stripParams = matchWrap();
        stripParams.setMargins(0, dp(10), 0, dp(6));
        content.addView(devicesScrollView, stripParams);

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER_VERTICAL);

        TextView statusFilterButton = filterPill("●", t("status"), statusFilterLabel(selectedStatusFilter));
        statusFilterButton.setOnClickListener(v -> showStatusFilterDialog());
        controls.addView(statusFilterButton, new LinearLayout.LayoutParams(0, dp(40), 1));

        TextView projectFilterButton = filterPill("⌘", t("project"), projectFilterLabel(selectedProjectFilter));
        projectFilterButton.setOnClickListener(v -> showProjectFilterDialog());
        LinearLayout.LayoutParams pParams = new LinearLayout.LayoutParams(0, dp(40), 1);
        pParams.setMargins(dp(10), 0, 0, 0);
        controls.addView(projectFilterButton, pParams);

        LinearLayout.LayoutParams controlsParams = matchWrap();
        controlsParams.setMargins(0, dp(2), 0, dp(10));
        content.addView(controls, controlsParams);

        // The single run list (filtered by selected device + project + status).
        ScrollView scrollView = new ScrollView(this);
        scrollView.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        scrollView.setVerticalScrollBarEnabled(false);
        homeRunsScrollView = scrollView;
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

    private View homeOverviewCard() {
        monitorDeck = new FrameLayout(this);
        monitorDeck.setClipChildren(false);
        monitorDeck.setClipToPadding(false);
        attachMonitorVerticalSwipe(monitorDeck);
        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(0, dp(4), 0, 0);
        params.height = dp(132);
        monitorDeck.setLayoutParams(params);
        updateMonitorDeck(false);
        return monitorDeck;
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
        c.addView(settingsHeroCard(), matchWrap());

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

    private View settingsHeroCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(16), dp(15), dp(16), dp(15));
        card.setBackground(roundedBg(settingsHeroBg(), 24, settingsHeroStroke()));
        card.setClipToOutline(true);

        FrameLayout iconShell = new FrameLayout(this);
        iconShell.setBackground(roundedBg(isDarkTheme() ? color("#111113") : Color.WHITE, 18, settingsHeroStroke()));
        ImageView icon = new ImageView(this);
        icon.setImageResource(isDarkTheme() ? R.drawable.haoleme_icon_dark : R.drawable.haoleme_icon_light);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iconShell.addView(icon, new FrameLayout.LayoutParams(dp(46), dp(46), Gravity.CENTER));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(58), dp(58));
        card.addView(iconShell, iconParams);

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        copyParams.setMargins(dp(13), 0, dp(8), 0);
        card.addView(copy, copyParams);

        TextView title = new TextView(this);
        title.setText(appDisplayName());
        title.setTextColor(textPrimary());
        title.setTextSize(21);
        title.setTypeface(null, Typeface.BOLD);
        title.setSingleLine(true);
        copy.addView(title, matchWrap());

        TextView subtitle = new TextView(this);
        subtitle.setText(settingsHeroSubtitle());
        subtitle.setTextColor(textSecondary());
        subtitle.setTextSize(12);
        subtitle.setPadding(0, dp(3), 0, 0);
        copy.addView(subtitle, matchWrap());

        TextView badge = new TextView(this);
        badge.setText(settingsHeroBadge());
        badge.setTextColor(settingsHeroBadgeText());
        badge.setTextSize(11);
        badge.setTypeface(null, Typeface.BOLD);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(9), dp(5), dp(9), dp(5));
        badge.setBackground(roundedBg(settingsHeroBadgeBg(), 99, Color.TRANSPARENT));
        card.addView(badge, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(0, dp(2), 0, dp(12));
        card.setLayoutParams(params);
        return card;
    }

    private String settingsHeroSubtitle() {
        int online = onlineDeviceCount();
        int total = deviceNames.size();
        if (total <= 0 && hasPairedDevice()) {
            total = 1;
        }
        String devices = isEnglish()
                ? online + " online · " + total + " devices"
                : online + " 在线 · " + total + " 台设备";
        return devices + " · v" + currentVersionName();
    }

    private String settingsHeroBadge() {
        if (hasAvailableUpdate()) {
            return isEnglish() ? "UPDATE" : "可更新";
        }
        if (hasPairedDevice()) {
            return isEnglish() ? "SYNC" : "同步中";
        }
        return isEnglish() ? "PAIR" : "去配对";
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
        view.setTextSize(13);
        view.setTypeface(null, Typeface.BOLD);
        view.setAllCaps(false);
        view.setLetterSpacing(0f);
        view.setTextColor(textSecondary());
        view.setPadding(dp(4), dp(15), 0, dp(8));
        return view;
    }

    private LinearLayout pairCodeCard(LinearLayout pairControls) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(14));
        card.setBackground(roundedBg(cardBg(), 18, cardStroke()));
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
        chip.setBackground(roundedBg(settingsIconChipBg(iconColor), 99, Color.TRANSPARENT));

        if ("github".equals(icon)) {
            ImageView image = new ImageView(this);
            image.setImageResource(R.drawable.github_mark);
            image.setColorFilter(iconColor);
            image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            FrameLayout.LayoutParams imageParams = new FrameLayout.LayoutParams(dp(23), dp(23), Gravity.CENTER);
            chip.addView(image, imageParams);
            return chip;
        }
        if ("qr".equals(icon)) {
            QrIconView qr = new QrIconView(this, iconColor);
            chip.addView(qr, new FrameLayout.LayoutParams(dp(25), dp(25), Gravity.CENTER));
            return chip;
        }
        if ("theme_icon".equals(icon)) {
            ThemeIconView theme = new ThemeIconView(this, iconColor);
            chip.addView(theme, new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));
            return chip;
        }
        if ("quiet_icon".equals(icon)) {
            QuietHoursIconView quiet = new QuietHoursIconView(this, iconColor);
            chip.addView(quiet, new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));
            return chip;
        }
        if ("mask_icon".equals(icon)) {
            MaskIconView mask = new MaskIconView(this, iconColor);
            chip.addView(mask, new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));
            return chip;
        }
        if ("language_icon".equals(icon)) {
            LanguageIconView language = new LanguageIconView(this, iconColor);
            chip.addView(language, new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));
            return chip;
        }
        if ("diagnostics_icon".equals(icon)) {
            DiagnosticsIconView diagnostics = new DiagnosticsIconView(this, iconColor);
            chip.addView(diagnostics, new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));
            return chip;
        }

        TextView iconView = new TextView(this);
        iconView.setText(icon);
        float textSize = icon.length() > 1 ? 17f : 22f;
        int box = dp(26);
        if ("♥".equals(icon)) {
            textSize = 19f;
            box = dp(24);
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
        row.setMinimumHeight(dp(60));
        row.setPadding(dp(13), dp(9), dp(12), dp(9));
        row.setBackground(rowPressBg());
        row.setElevation(0);
        row.setClickable(listener != null);
        if (listener != null) {
            row.setOnClickListener(listener);
        }

        row.addView(settingsIconView(icon, iconColor), new LinearLayout.LayoutParams(dp(38), dp(38)));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextSize(14.5f);
        titleView.setTypeface(null, Typeface.BOLD);
        titleView.setTextColor(textPrimary());
        titleView.setSingleLine(false);
        labels.addView(titleView, matchWrap());
        if (subtitle != null && !subtitle.trim().isEmpty()) {
            TextView subtitleView = new TextView(this);
            subtitleView.setText(subtitle.trim());
            subtitleView.setTextSize(11.5f);
            subtitleView.setTextColor(textSecondary());
            subtitleView.setPadding(0, dp(2), 0, 0);
            labels.addView(subtitleView, matchWrap());
        }
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        labelParams.setMargins(dp(11), 0, dp(8), 0);
        row.addView(labels, labelParams);

        if (value != null && !value.trim().isEmpty()) {
            TextView valueView = new TextView(this);
            valueView.setTag("settings_value");
            valueView.setText(value.trim());
            valueView.setTextSize(12);
            valueView.setTextColor(textSecondary());
            valueView.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
            valueView.setPadding(dp(8), dp(4), dp(8), dp(4));
            valueView.setBackground(roundedBg(settingsValueBg(), 99, Color.TRANSPARENT));
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
        group.setBackground(roundedBg(cardBg(), 18, cardStroke()));
        group.setClipToOutline(true);
        boolean first = true;
        for (View row : rows) {
            if (row == null) continue;
            if (!first) group.addView(settingsDividerView());
            group.addView(row);
            first = false;
        }
        LinearLayout.LayoutParams lp = matchWrap();
        lp.setMargins(0, 0, 0, dp(13));
        group.setLayoutParams(lp);
        return group;
    }

    private View settingsDividerView() {
        View d = new View(this);
        d.setBackgroundColor(settingsDivider());
        int h = Math.max(1, Math.round(getResources().getDisplayMetrics().density * 0.7f));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, h);
        lp.setMargins(dp(62), 0, dp(12), 0);
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
        submitUpdateBackground(() -> {
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
        submitUpdateBackground(() -> {
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
                    refreshHome(false);
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
                || PREF_PINNED_RUNS.equals(key)
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
        submitUpdateBackground(() -> {
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
                    || PREF_PENDING_RUN_DELETES.equals(key)
                    || PREF_PINNED_RUNS.equals(key)
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
        submitBackground(() -> {
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
            // NOTE: deliberately keep "encryption_key_b64" — the E2EE account key
            // must survive resets/server-URL changes/re-pairs. Discarding it mints a
            // new key, orphaning every run encrypted with the old one ("Encrypted
            // command" / no output) and fragmenting accounts. A clean re-pair then
            // re-shares this same stable key to the CLI so runs stay decryptable.
            if (isLocalCacheKey(key)
                    || "token".equals(key)
                    || "paired_device_id".equals(key)
                    || "paired_device_name".equals(key)
                    || "paired_account".equals(key)
                    || "paired_at".equals(key)
                    || "paired_server_url".equals(key)
                    || "space_id".equals(key)
                    || "space_joined_at".equals(key)
                    || "selected_device_id".equals(key)
                    || PREF_PENDING_RUN_DELETES.equals(key)) {
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
        submitBackground(() -> {
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
                rememberLocallyRevokedDevices(revokedIds);
                removeDevicesFromCache(revokedIds);
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

    private String commandTextForDisplay(JSONObject run, String fallback) {
        String value = run == null ? "" : run.optString("commandText", "");
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        if (!"Encrypted command".equals(value.trim())) {
            return value;
        }
        String id = run.optString("id", "").trim();
        JSONObject cached = id.isEmpty() ? null : loadCachedRunDetailJson(id);
        if (cached != null) {
            String cachedCommand = cached.optString("commandText", "").trim();
            if (!cachedCommand.isEmpty() && !"Encrypted command".equals(cachedCommand)) {
                return cachedCommand;
            }
        }
        return isEnglish() ? "Syncing encrypted command..." : "正在同步命令...";
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

    private TextView filterPill(String icon, String label, String value) {
        TextView button = new TextView(this);
        String text = icon + "  " + label + " · " + value + "  ˅";
        SpannableString span = new SpannableString(text);
        span.setSpan(new RelativeSizeSpan(1.12f), 0, icon.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        span.setSpan(new StyleSpan(Typeface.BOLD), 0, Math.min(text.length(), icon.length() + 2 + label.length()), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        button.setText(span);
        button.setTextSize(13);
        button.setGravity(Gravity.CENTER);
        button.setSingleLine(true);
        button.setEllipsize(android.text.TextUtils.TruncateAt.END);
        button.setTypeface(null, Typeface.BOLD);
        button.setTextColor(textPrimary());
        button.setPadding(dp(12), 0, dp(12), 0);
        button.setBackground(roundedBg(filterPillBg(), 99, filterPillStroke()));
        button.setClickable(true);
        button.setElevation(0);
        return button;
    }

    private TextView circleIconButton(String icon) {
        TextView button = new TextView(this);
        button.setText(icon);
        button.setGravity(Gravity.CENTER);
        button.setTypeface(null, Typeface.BOLD);
        button.setTextColor(textPrimary());
        button.setBackground(roundedBg(circleButtonBg(), 99, circleButtonStroke()));
        button.setClickable(true);
        button.setElevation(0);
        return button;
    }

    private TextView refreshIconButton() {
        TextView button = new TextView(this);
        button.setText("⟳");
        button.setTextSize(22);
        button.setGravity(Gravity.CENTER);
        button.setTypeface(null, Typeface.BOLD);
        button.setTextColor(color("#111827"));
        button.setBackground(roundedBg(Color.WHITE, 99, color("#EEF0F4")));
        button.setClickable(true);
        button.setElevation(dp(2));
        return button;
    }

    private TextView swipeActionButton(String icon, String label, int bg, int fg) {
        TextView button = new TextView(this);
        button.setText(icon + "\n" + label);
        button.setTextSize(12);
        button.setGravity(Gravity.CENTER);
        button.setTypeface(null, Typeface.BOLD);
        button.setTextColor(fg);
        button.setIncludeFontPadding(false);
        button.setLineSpacing(dp(3), 1.0f);
        button.setBackground(roundedBg(bg, 18, Color.TRANSPARENT));
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
                    refreshHome(false);
                }
            }
        });
        return button;
    }

    private void refreshRuns() {
        refreshRuns(false);
    }

    private void refreshHome(boolean manual) {
        if (destroyed) {
            return;
        }
        if (manual) {
            if (statusText != null) {
                statusText.setText(isEnglish() ? "Refreshing..." : "正在刷新...");
            }
            if (hasCachedRuns()) {
                mergeDevicesFromCachedRuns();
                loadCachedDevices();
                loadCachedRuns();
            }
            syncPendingRunDeletesAsync(false);
        }
        refreshDevices(manual);
        refreshRuns(manual);
    }

    private void refreshRuns(boolean manual) {
        if (destroyed) {
            return;
        }
        if (!beginRunsRefresh(manual)) {
            if (manual && hasCachedRuns()) {
                mergeDevicesFromCachedRuns();
                loadCachedDevices();
                loadCachedRuns();
            }
            return;
        }
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
        submitBackground(() -> {
            try {
                String body = httpGet(requestUrl, HTTP_LIST_READ_TIMEOUT_MS);
                final JSONArray runs = applyPendingRunDeletes(attachCachedConsolePreviews(decryptRuns(new JSONObject(body).getJSONArray("runs"))));
                handler.post(() -> {
                    String current = (selectedDeviceId == null || "all".equals(selectedDeviceId)) ? "all" : selectedDeviceId;
                    if (!targetDevice.equals(current)) {
                        // stale refresh for a previous device selection, ignore
                        return;
                    }
                    renderRuns(runs, false);
                });
                submitBackground(() -> saveRunsCache(runs));
                scheduleMissingLocalOutputsSync(runs);
                submitBackground(this::syncPendingRunDeletesBlocking);
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
            } finally {
                finishRunsRefresh();
            }
        });
    }

    private void refreshDevices() {
        refreshDevices(false);
    }

    private void refreshDevices(boolean manual) {
        if (destroyed) {
            return;
        }
        if (!beginDevicesRefresh(manual)) {
            if (manual) {
                mergeDevicesFromCachedRuns();
                if ("home".equals(currentTab)) {
                    loadCachedDevices();
                }
            }
            return;
        }
        final String requestUrl = normalizedServerUrl() + "/api/devices";
        submitBackground(() -> {
            try {
                String body = httpGet(requestUrl, HTTP_LIST_READ_TIMEOUT_MS);
                JSONArray devices = mergeCloudDevicesWithCache(new JSONObject(body).getJSONArray("devices"));
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
            } finally {
                finishDevicesRefresh();
            }
        });
    }

    private boolean beginRunsRefresh(boolean manual) {
        synchronized (refreshStateLock) {
            if (runsRefreshInFlight) {
                runsRefreshQueued = true;
                runsRefreshQueuedManual = runsRefreshQueuedManual || manual;
                return false;
            }
            runsRefreshInFlight = true;
            return true;
        }
    }

    private void finishRunsRefresh() {
        boolean queued;
        boolean manual;
        synchronized (refreshStateLock) {
            runsRefreshInFlight = false;
            queued = runsRefreshQueued;
            manual = runsRefreshQueuedManual;
            runsRefreshQueued = false;
            runsRefreshQueuedManual = false;
        }
        if (queued && !destroyed) {
            handler.post(() -> refreshRuns(manual));
        }
    }

    private boolean beginDevicesRefresh(boolean manual) {
        synchronized (refreshStateLock) {
            if (devicesRefreshInFlight) {
                devicesRefreshQueued = true;
                devicesRefreshQueuedManual = devicesRefreshQueuedManual || manual;
                return false;
            }
            devicesRefreshInFlight = true;
            return true;
        }
    }

    private void finishDevicesRefresh() {
        boolean queued;
        boolean manual;
        synchronized (refreshStateLock) {
            devicesRefreshInFlight = false;
            queued = devicesRefreshQueued;
            manual = devicesRefreshQueuedManual;
            devicesRefreshQueued = false;
            devicesRefreshQueuedManual = false;
        }
        if (queued && !destroyed) {
            handler.post(() -> refreshDevices(manual));
        }
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
            forgetLocallyRevokedDevice(id);
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

    private JSONArray mergeCloudDevicesWithCache(JSONArray cloudDevices) {
        Map<String, JSONObject> byId = new HashMap<>();
        try {
            String cached = prefs.getString(CACHE_DEVICES, "");
            if (cached != null && !cached.isEmpty()) {
                JSONArray cachedDevices = new JSONArray(cached);
                for (int i = 0; i < cachedDevices.length(); i++) {
                    JSONObject device = cachedDevices.optJSONObject(i);
                    String id = device == null ? "" : device.optString("id", "").trim();
                    if (!id.isEmpty() && !isLocallyRevokedDevice(id) && device.optString("revokedAt", "").trim().isEmpty()) {
                        byId.put(id, new JSONObject(device.toString()));
                    }
                }
            }
            for (int i = 0; i < cloudDevices.length(); i++) {
                JSONObject device = cloudDevices.optJSONObject(i);
                String id = device == null ? "" : device.optString("id", "").trim();
                if (id.isEmpty() || isLocallyRevokedDevice(id) || !device.optString("revokedAt", "").trim().isEmpty()) {
                    continue;
                }
                JSONObject existing = byId.get(id);
                JSONObject merged = new JSONObject(device.toString());
                if (merged.optString("name", "").trim().isEmpty() && existing != null) {
                    merged.put("name", existing.optString("name", id));
                }
                if (merged.optString("createdAt", "").trim().isEmpty() && existing != null) {
                    merged.put("createdAt", existing.optString("createdAt", ""));
                }
                byId.put(id, merged);
            }
        } catch (Exception ignored) {
            JSONArray fallback = cloudDevices == null ? new JSONArray() : cloudDevices;
            prefs.edit().putString(CACHE_DEVICES, fallback.toString()).apply();
            return fallback;
        }
        JSONArray merged = new JSONArray();
        for (JSONObject device : byId.values()) {
            merged.put(device);
        }
        prefs.edit().putString(CACHE_DEVICES, merged.toString()).apply();
        return merged;
    }

    private void removeDevicesFromCache(Set<String> deviceIds) {
        if (deviceIds == null || deviceIds.isEmpty()) {
            return;
        }
        try {
            String cached = prefs.getString(CACHE_DEVICES, "");
            if (cached == null || cached.isEmpty()) {
                return;
            }
            JSONArray devices = new JSONArray(cached);
            JSONArray kept = new JSONArray();
            for (int i = 0; i < devices.length(); i++) {
                JSONObject device = devices.optJSONObject(i);
                String id = device == null ? "" : device.optString("id", "").trim();
                if (id.isEmpty() || deviceIds.contains(id)) {
                    continue;
                }
                kept.put(device);
            }
            prefs.edit().putString(CACHE_DEVICES, kept.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    private void rememberLocallyRevokedDevices(Set<String> deviceIds) {
        if (deviceIds == null || deviceIds.isEmpty()) {
            return;
        }
        Set<String> stored = new HashSet<>(prefs.getStringSet(PREF_REVOKED_DEVICE_IDS, new HashSet<>()));
        for (String id : deviceIds) {
            if (id != null && !id.trim().isEmpty()) {
                stored.add(id.trim());
            }
        }
        prefs.edit().putStringSet(PREF_REVOKED_DEVICE_IDS, stored).apply();
    }

    private void forgetLocallyRevokedDevice(String deviceId) {
        if (deviceId == null || deviceId.trim().isEmpty()) {
            return;
        }
        Set<String> stored = new HashSet<>(prefs.getStringSet(PREF_REVOKED_DEVICE_IDS, new HashSet<>()));
        if (stored.remove(deviceId.trim())) {
            prefs.edit().putStringSet(PREF_REVOKED_DEVICE_IDS, stored).apply();
        }
    }

    private boolean isLocallyRevokedDevice(String deviceId) {
        if (deviceId == null || deviceId.trim().isEmpty()) {
            return false;
        }
        return prefs.getStringSet(PREF_REVOKED_DEVICE_IDS, new HashSet<>()).contains(deviceId.trim());
    }

    private void renderDevices(JSONArray devices) {
        int scrollX = devicesScrollView == null ? 0 : devicesScrollView.getScrollX();
        recordCpuHistoryFromDevices(devices);
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

    private void recordCpuHistoryFromDevices(JSONArray devices) {
        if (devices == null || prefs == null) {
            return;
        }
        SharedPreferences.Editor editor = prefs.edit();
        boolean changed = false;
        for (int i = 0; i < devices.length(); i++) {
            JSONObject device = devices.optJSONObject(i);
            if (device == null) {
                continue;
            }
            String id = device.optString("id", "").trim();
            JSONObject cpu = device.optJSONObject("cpu");
            if (id.isEmpty() || cpu == null || !cpu.has("utilization")) {
                continue;
            }
            int util = Math.max(0, Math.min(100, cpu.optInt("utilization", -1)));
            if (util < 0) {
                continue;
            }
            long ts = parseTimestamp(device.optString("cpuUpdatedAt", ""));
            if (ts <= 0L) {
                ts = parseTimestamp(device.optString("lastSeenAt", ""));
            }
            if (ts <= 0L) {
                ts = System.currentTimeMillis();
            }
            String key = cpuHistoryKey(id);
            JSONArray history = loadCpuHistoryArray(id);
            JSONObject last = history.length() > 0 ? history.optJSONObject(history.length() - 1) : null;
            if (last != null && last.optLong("t", 0L) == ts && last.optInt("u", -1) == util) {
                continue;
            }
            JSONArray next = new JSONArray();
            int start = Math.max(0, history.length() - CPU_HISTORY_MAX_POINTS + 1);
            for (int h = start; h < history.length(); h++) {
                JSONObject point = history.optJSONObject(h);
                if (point != null && point.optLong("t", 0L) != ts) {
                    next.put(point);
                }
            }
            JSONObject point = new JSONObject();
            try {
                point.put("t", ts);
                point.put("u", util);
            } catch (Exception ignored) {
            }
            next.put(point);
            editor.putString(key, next.toString());
            changed = true;
        }
        if (changed) {
            editor.apply();
        }
    }

    private String cpuHistoryKey(String deviceId) {
        return CPU_HISTORY_PREFIX + cachePart(deviceId);
    }

    private JSONArray loadCpuHistoryArray(String deviceId) {
        String raw = prefs.getString(cpuHistoryKey(deviceId), "");
        if (raw == null || raw.isEmpty()) {
            return new JSONArray();
        }
        try {
            return new JSONArray(raw);
        } catch (Exception ignored) {
            return new JSONArray();
        }
    }

    private int[] cpuHistoryValues(String deviceId, int latestValue) {
        JSONArray history = loadCpuHistoryArray(deviceId);
        List<Integer> values = new ArrayList<>();
        for (int i = 0; i < history.length(); i++) {
            JSONObject point = history.optJSONObject(i);
            if (point == null) {
                continue;
            }
            int value = point.optInt("u", -1);
            if (value >= 0) {
                values.add(Math.max(0, Math.min(100, value)));
            }
        }
        if (values.isEmpty() && latestValue >= 0) {
            values.add(Math.max(0, Math.min(100, latestValue)));
        }
        int[] out = new int[values.size()];
        for (int i = 0; i < values.size(); i++) {
            out[i] = values.get(i);
        }
        return out;
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
        button.setGravity(Gravity.CENTER);
        button.setMinimumHeight(dp(42));
        button.setPadding(dp(12), 0, dp(14), 0);
        button.setBackground(roundedBg(selected ? deviceChipSelectedBg() : deviceChipBg(), 99, selected ? Color.TRANSPARENT : deviceChipStroke()));
        button.setClickable(true);
        button.setElevation(0);

        LinearLayout labelRow = new LinearLayout(this);
        labelRow.setOrientation(LinearLayout.HORIZONTAL);
        labelRow.setGravity(Gravity.CENTER);

        if (!"all".equals(id)) {
            View dot = statusDot(online ? color("#16A34A") : mutedDotColor());
            LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(7), dp(7));
            dotParams.setMargins(0, 0, dp(7), 0);
            labelRow.addView(dot, dotParams);
        }

        ComputerIconView icon = new ComputerIconView(this, selected ? deviceChipSelectedText() : textSecondary());
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(18), dp(16));
        iconParams.setMargins(0, 0, dp(7), 0);
        labelRow.addView(icon, iconParams);

        TextView name = new TextView(this);
        name.setText(label == null || label.trim().isEmpty() ? ("all".equals(id) ? "All" : "Device") : label.trim());
        name.setSingleLine(true);
        name.setTextSize(14);
        name.setTypeface(null, Typeface.BOLD);
        name.setTextColor(selected ? deviceChipSelectedText() : tabMutedText());
        name.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        labelRow.addView(name, nameParams);
        button.addView(labelRow, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

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
                dp(44)
        );
        params.setMargins(0, 0, dp(8), 0);
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
        updateMonitorDeck(false);
        if (selectedDeviceId == null || "all".equals(selectedDeviceId)) {
            if (deviceSummaryText != null) {
                deviceSummaryText.setText(isEnglish() ? "All active devices" : "全部活跃设备");
            }
            if (deviceHeartbeatText != null) {
                int online = onlineDeviceCount();
                int total = deviceNames.isEmpty() ? cachedDevicesArray().length() : deviceNames.size();
                deviceHeartbeatText.setText(isEnglish()
                        ? online + " online · " + total + " devices"
                        : online + " 在线 · 共 " + total + " 台设备");
            }
            return;
        }
        String lastSeen = formatDeviceTimestamp(deviceLastSeen.get(selectedDeviceId));
        boolean online = Boolean.TRUE.equals(deviceOnline.get(selectedDeviceId));
        if (deviceSummaryText != null) {
            deviceSummaryText.setText(selectedDeviceName() + " · " + (online ? (isEnglish() ? "Online" : "在线") : (isEnglish() ? "Offline" : "离线")));
        }
        StringBuilder text = new StringBuilder();
        if (!lastSeen.isEmpty()) {
            text.append(isEnglish() ? "Heartbeat " : "心跳 ").append(lastSeen);
        } else {
            text.append(isEnglish() ? "Waiting for heartbeat" : "等待心跳");
        }
        if (selectedDeviceMetricAvailable()) {
            text.append(isEnglish() ? " · swipe for metrics" : " · 下滑看指标");
        }
        if (deviceHeartbeatText != null) {
            deviceHeartbeatText.setText(text.toString());
        }
    }

    private boolean selectedDeviceMetricAvailable() {
        if (selectedDeviceId == null || "all".equals(selectedDeviceId)) {
            return false;
        }
        JSONArray devices = cachedDevicesArray();
        for (int i = 0; i < devices.length(); i++) {
            JSONObject d = devices.optJSONObject(i);
            if (d != null && selectedDeviceId.equals(d.optString("id", ""))) {
                JSONArray g = d.optJSONArray("gpus");
                if (g != null && g.length() > 0) {
                    return true;
                }
                JSONObject cpu = d.optJSONObject("cpu");
                return cpu != null && cpu.has("utilization");
            }
        }
        return false;
    }

    private void updateMonitorDeck(boolean animate) {
        updateMonitorDeck(animate, 0);
    }

    private void updateMonitorDeck(boolean animate, int direction) {
        if (monitorDeck == null) {
            return;
        }
        List<String> pages = monitorPages();
        if (pages.isEmpty()) {
            pages.add("device");
        }
        if (monitorPageIndex < 0) {
            monitorPageIndex = 0;
        }
        if (monitorPageIndex >= pages.size()) {
            monitorPageIndex = pages.size() - 1;
        }
        View page = buildMonitorPage(pages.get(monitorPageIndex), pages);
        if (!animate || monitorDeck.getChildCount() == 0) {
            monitorDeck.removeAllViews();
            monitorDeck.addView(page, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
            ));
            return;
        }
        View old = monitorDeck.getChildAt(0);
        int offset = direction >= 0 ? dp(34) : -dp(34);
        page.setAlpha(0f);
        page.setTranslationY(offset);
        monitorDeck.addView(page, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        page.animate().alpha(1f).translationY(0f).setDuration(180).start();
        old.animate()
                .alpha(0f)
                .translationY(-offset)
                .setDuration(160)
                .withEndAction(() -> monitorDeck.removeView(old))
                .start();
    }

    private List<String> monitorPages() {
        List<String> pages = new ArrayList<>();
        pages.add("device");
        if (selectedDeviceId == null || "all".equals(selectedDeviceId)) {
            return pages;
        }
        JSONObject selectedDevice = selectedDeviceSnapshot();
        if (!selectedDeviceGpus(selectedDevice).isEmpty()) {
            pages.add("gpu");
        }
        JSONObject cpu = selectedDevice == null ? null : selectedDevice.optJSONObject("cpu");
        if (cpu != null && cpu.has("utilization")) {
            pages.add("cpu");
        }
        return pages;
    }

    private JSONObject selectedDeviceSnapshot() {
        if (selectedDeviceId == null || "all".equals(selectedDeviceId)) {
            return null;
        }
        JSONArray devices = cachedDevicesArray();
        for (int i = 0; i < devices.length(); i++) {
            JSONObject device = devices.optJSONObject(i);
            if (device != null && selectedDeviceId.equals(device.optString("id", ""))) {
                return device;
            }
        }
        return null;
    }

    private List<JSONObject> selectedDeviceGpus(JSONObject selectedDevice) {
        List<JSONObject> deviceGpus = new ArrayList<>();
        JSONArray gpus = selectedDevice == null ? null : selectedDevice.optJSONArray("gpus");
        if (gpus != null) {
            for (int g = 0; g < gpus.length(); g++) {
                JSONObject gpu = gpus.optJSONObject(g);
                if (gpu != null) {
                    deviceGpus.add(gpu);
                }
            }
        }
        return deviceGpus;
    }

    private View buildMonitorPage(String page, List<String> pages) {
        if ("gpu".equals(page)) {
            return buildGpuMonitorPage(pages);
        }
        if ("cpu".equals(page)) {
            return buildCpuMonitorPage(pages);
        }
        return buildDeviceMonitorPage(pages);
    }

    private LinearLayout monitorCardBase(String eyebrowText, String titleText, String subtitleText, List<String> pages) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(14), dp(12));
        card.setBackground(roundedBg(homeHeroBg(), 22, homeHeroStroke()));
        attachMonitorVerticalSwipe(card);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setGravity(Gravity.CENTER_VERTICAL);

        TextView eyebrow = new TextView(this);
        eyebrow.setText(eyebrowText);
        eyebrow.setTextSize(10);
        eyebrow.setLetterSpacing(0.06f);
        eyebrow.setTypeface(null, Typeface.BOLD);
        eyebrow.setTextColor(textSecondary());
        left.addView(eyebrow, matchWrap());

        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextSize(18);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(textPrimary());
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setPadding(0, dp(4), 0, 0);
        left.addView(title, matchWrap());

        TextView subtitle = new TextView(this);
        subtitle.setText(subtitleText);
        subtitle.setTextSize(11);
        subtitle.setTextColor(textSecondary());
        subtitle.setSingleLine(true);
        subtitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        subtitle.setPadding(0, dp(4), 0, 0);
        left.addView(subtitle, matchWrap());

        head.addView(left, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.CENTER);
        TextView refresh = refreshIconButton();
        refresh.setOnClickListener(v -> refreshHome(true));
        right.addView(refresh, new LinearLayout.LayoutParams(dp(44), dp(44)));
        TextView dots = new TextView(this);
        dots.setText(monitorPageDots(pages));
        dots.setTextSize(9f);
        dots.setGravity(Gravity.CENTER);
        dots.setTextColor(textSecondary());
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        dotParams.setMargins(0, dp(2), 0, 0);
        right.addView(dots, dotParams);
        LinearLayout.LayoutParams rightParams = new LinearLayout.LayoutParams(dp(50), LinearLayout.LayoutParams.WRAP_CONTENT);
        rightParams.setMargins(dp(10), 0, 0, 0);
        head.addView(right, rightParams);
        card.addView(head, matchWrap());
        return card;
    }

    private String monitorPageDots(List<String> pages) {
        StringBuilder out = new StringBuilder();
        int count = pages == null ? 1 : Math.max(1, pages.size());
        for (int i = 0; i < count; i++) {
            out.append(i == monitorPageIndex ? "●" : "○");
            if (i < count - 1) {
                out.append(' ');
            }
        }
        return out.toString();
    }

    private View buildDeviceMonitorPage(List<String> pages) {
        String title;
        String subtitle;
        if (selectedDeviceId == null || "all".equals(selectedDeviceId)) {
            int online = onlineDeviceCount();
            int total = deviceNames.isEmpty() ? cachedDevicesArray().length() : deviceNames.size();
            title = isEnglish() ? "All active devices" : "全部活跃设备";
            subtitle = isEnglish()
                    ? online + " online · " + total + " devices"
                    : online + " 在线 · 共 " + total + " 台设备";
        } else {
            String lastSeen = formatDeviceTimestamp(deviceLastSeen.get(selectedDeviceId));
            boolean online = Boolean.TRUE.equals(deviceOnline.get(selectedDeviceId));
            title = selectedDeviceName() + " · " + (online ? (isEnglish() ? "Online" : "在线") : (isEnglish() ? "Offline" : "离线"));
            if (!lastSeen.isEmpty()) {
                subtitle = (isEnglish() ? "Heartbeat " : "心跳 ") + lastSeen;
            } else {
                subtitle = isEnglish() ? "Waiting for heartbeat" : "等待心跳";
            }
            if (pages.size() > 1) {
                subtitle += isEnglish() ? " · swipe down" : " · 下滑看指标";
            }
        }
        LinearLayout card = monitorCardBase(isEnglish() ? "COMMAND MONITOR" : "命令运行监控", title, subtitle, pages);
        deviceSummaryText = (TextView) ((ViewGroup) ((ViewGroup) card.getChildAt(0)).getChildAt(0)).getChildAt(1);
        deviceHeartbeatText = (TextView) ((ViewGroup) ((ViewGroup) card.getChildAt(0)).getChildAt(0)).getChildAt(2);
        return card;
    }

    private View buildGpuMonitorPage(List<String> pages) {
        JSONObject selectedDevice = selectedDeviceSnapshot();
        List<JSONObject> deviceGpus = selectedDeviceGpus(selectedDevice);
        LinearLayout card = monitorCardBase(
                isEnglish() ? "GPU STATUS" : "GPU 状态",
                gpuMetricName(),
                selectedDeviceName() + (isEnglish() ? " · swipe sideways for metric" : " · 左右滑切换指标"),
                pages
        );
        attachGpuSwipe(card);
        if (gpuMetricIndex < 0 || gpuMetricIndex >= GPU_METRIC_COUNT) {
            gpuMetricIndex = 0;
        }
        if (!deviceGpus.isEmpty()) {
            LinearLayout body = new LinearLayout(this);
            body.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams bodyParams = matchWrap();
            bodyParams.setMargins(0, dp(7), 0, 0);
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
                        View filler = new View(this);
                        filler.setLayoutParams(new LinearLayout.LayoutParams(0, dp(20), 1));
                        row.addView(filler);
                        continue;
                    }
                    row.addView(gpuMetricItem(deviceGpus.get(gidx), gidx));
                }
                body.addView(row, matchWrap());
            }
            card.addView(body, bodyParams);
        }
        return card;
    }

    private View gpuMetricItem(JSONObject gpu, int fallbackIndex) {
        int idx = gpu.optInt("index", fallbackIndex);
        int util = Math.max(0, Math.min(100, gpu.optInt("utilization", 0)));
        int memUsed = Math.max(0, gpu.optInt("memoryUsed", 0));
        int memTotal = Math.max(0, gpu.optInt("memoryTotal", 0));
        int temp = Math.max(0, gpu.optInt("temperature", 0));
        int memPct = memTotal > 0 ? Math.max(0, Math.min(100, Math.round(memUsed * 100f / memTotal))) : 0;

        int progress;
        String valueText;
        int barColor;
        if (gpuMetricIndex == 1) {
            progress = memPct;
            valueText = memPct + "%";
            barColor = gpuBarColor(memPct, false);
        } else if (gpuMetricIndex == 2) {
            progress = Math.min(100, temp);
            valueText = temp + "°";
            barColor = gpuBarColor(temp, true);
        } else {
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
        item.addView(label, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setProgress(progress);
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(dp(40), dp(6));
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
        return item;
    }

    private View buildCpuMonitorPage(List<String> pages) {
        JSONObject selectedDevice = selectedDeviceSnapshot();
        JSONObject cpu = selectedDevice == null ? null : selectedDevice.optJSONObject("cpu");
        int util = cpu == null ? 0 : Math.max(0, Math.min(100, cpu.optInt("utilization", 0)));
        LinearLayout card = monitorCardBase(
                isEnglish() ? "CPU HISTORY" : "CPU 利用率",
                util + "%",
                selectedDeviceName() + (isEnglish() ? " · recent samples" : " · 最近采样"),
                pages
        );
        if (cpu != null) {
            LinearLayout.LayoutParams cpuParams = matchWrap();
            cpuParams.setMargins(0, dp(8), 0, 0);
            card.addView(buildCpuMetricPanel(cpu), cpuParams);
        }
        return card;
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

    private View buildCpuMetricPanel(JSONObject cpu) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(10), dp(8), dp(10), dp(10));
        box.setBackground(roundedBg(outputPreviewBg(), 14, outputPreviewStroke()));

        int util = Math.max(0, Math.min(100, cpu.optInt("utilization", 0)));
        int cores = Math.max(0, cpu.optInt("cores", 0));
        double load1 = cpu.optDouble("load1", -1);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText(isEnglish() ? "CPU usage" : "CPU 利用率");
        title.setTextSize(10f);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(textPrimary());
        head.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView value = new TextView(this);
        StringBuilder valueText = new StringBuilder(util + "%");
        if (cores > 0) {
            valueText.append(" · ").append(cores).append(isEnglish() ? " cores" : " 核");
        }
        if (load1 >= 0) {
            valueText.append(" · L1 ").append(String.format(Locale.US, "%.2f", load1));
        }
        value.setText(valueText.toString());
        value.setTextSize(9f);
        value.setTextColor(textSecondary());
        head.addView(value, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        box.addView(head, matchWrap());

        CpuChartView chart = new CpuChartView(this, cpuHistoryValues(selectedDeviceId, util), cpuChartLineColor(), cpuChartFillColor(), gpuTrackColor());
        LinearLayout.LayoutParams chartParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(46));
        chartParams.setMargins(0, dp(6), 0, 0);
        box.addView(chart, chartParams);
        return box;
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

    private void attachMonitorVerticalSwipe(View target) {
        monitorGestureDetector = new android.view.GestureDetector(this, new android.view.GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(android.view.MotionEvent e) {
                return true;
            }

            @Override
            public boolean onFling(android.view.MotionEvent e1, android.view.MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null) {
                    return false;
                }
                float dx = e2.getX() - e1.getX();
                float dy = e2.getY() - e1.getY();
                if (Math.abs(dy) > Math.abs(dx) && Math.abs(dy) > dp(36)) {
                    return switchMonitorPage(dy > 0 ? 1 : -1);
                }
                return false;
            }
        });
        target.setClickable(true);
        target.setOnTouchListener((v, ev) -> {
            if (ev.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                v.getParent().requestDisallowInterceptTouchEvent(true);
            } else if (ev.getAction() == android.view.MotionEvent.ACTION_UP
                    || ev.getAction() == android.view.MotionEvent.ACTION_CANCEL) {
                v.getParent().requestDisallowInterceptTouchEvent(false);
            }
            boolean handled = monitorGestureDetector != null && monitorGestureDetector.onTouchEvent(ev);
            if (handled && ev.getAction() == android.view.MotionEvent.ACTION_UP) {
                v.performClick();
            }
            return handled || ev.getAction() == android.view.MotionEvent.ACTION_DOWN;
        });
    }

    private boolean switchMonitorPage(int delta) {
        List<String> pages = monitorPages();
        if (pages.size() <= 1) {
            return false;
        }
        int old = monitorPageIndex;
        int next = Math.max(0, Math.min(pages.size() - 1, old + delta));
        if (next == old) {
            return false;
        }
        monitorPageIndex = next;
        updateMonitorDeck(true, delta);
        return true;
    }

    private void attachGpuSwipe(View target) {
        gpuGestureDetector = new android.view.GestureDetector(this, new android.view.GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(android.view.MotionEvent e) {
                return true;
            }

            @Override
            public boolean onFling(android.view.MotionEvent e1, android.view.MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null) {
                    return false;
                }
                float dx = e2.getX() - e1.getX();
                float dy = e2.getY() - e1.getY();
                if (Math.abs(dy) > Math.abs(dx) && Math.abs(dy) > dp(36)) {
                    return switchMonitorPage(dy > 0 ? 1 : -1);
                }
                if (Math.abs(dx) > Math.abs(dy) && Math.abs(dx) > dp(36)) {
                    if (dx < 0) {
                        gpuMetricIndex = (gpuMetricIndex + 1) % GPU_METRIC_COUNT;
                    } else {
                        gpuMetricIndex = (gpuMetricIndex - 1 + GPU_METRIC_COUNT) % GPU_METRIC_COUNT;
                    }
                    updateMonitorDeck(false);
                    return true;
                }
                return false;
            }

            @Override
            public boolean onSingleTapUp(android.view.MotionEvent e) {
                gpuMetricIndex = (gpuMetricIndex + 1) % GPU_METRIC_COUNT;
                updateMonitorDeck(false);
                return true;
            }
        });
        target.setClickable(true);
        target.setOnTouchListener((v, ev) -> {
            if (ev.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                v.getParent().requestDisallowInterceptTouchEvent(true);
            } else if (ev.getAction() == android.view.MotionEvent.ACTION_UP
                    || ev.getAction() == android.view.MotionEvent.ACTION_CANCEL) {
                v.getParent().requestDisallowInterceptTouchEvent(false);
            }
            boolean handled = gpuGestureDetector.onTouchEvent(ev);
            if (ev.getAction() == android.view.MotionEvent.ACTION_UP) {
                v.performClick();
            }
            return handled || ev.getAction() == android.view.MotionEvent.ACTION_DOWN;
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
        submitBackground(() -> {
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
        submitBackground(() -> {
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
        submitBackground(() -> {
            try {
                httpRequest(normalizedServerUrl() + "/api/devices/" + Uri.encode(deviceId), "DELETE");
                handler.post(() -> {
                    selectedDeviceId = "all";
                    SharedPreferences.Editor editor = prefs.edit().putString("selected_device_id", selectedDeviceId);
                    if (deviceId.equals(prefs.getString("paired_device_id", ""))) {
                        editor.remove("paired_device_id").remove("paired_device_name");
                    }
                    editor.apply();
                    Set<String> revokedIds = new HashSet<>();
                    revokedIds.add(deviceId);
                    rememberLocallyRevokedDevices(revokedIds);
                    removeDevicesFromCache(revokedIds);
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
            if (run != null && isFailedLikeStatus(run.optString("status", ""))) {
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
        String runsSig = runsLayoutSignature(visibleRuns);
        if (runsContainer != null && runsContainer.getChildCount() > 0 && runsSig.equals(lastRunsSig)) {
            if (updateRunCardsInPlace(visibleRuns)) {
                if (!fromCache) {
                    firstLoad = false;
                }
                return;
            }
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

    private String runsLayoutSignature(JSONArray runs) {
        StringBuilder sb = new StringBuilder();
        Set<String> pinned = pinnedRunIds();
        for (int i = 0; i < runs.length(); i++) {
            JSONObject r = runs.optJSONObject(i);
            if (r == null) {
                continue;
            }
            sb.append(r.optString("id", "")).append('|')
              .append(pinned.contains(r.optString("id", "")) ? 'P' : '-').append(';');
        }
        return sb.toString();
    }

    private boolean updateRunCardsInPlace(JSONArray runs) {
        if (runsContainer == null || runs == null || runsContainer.getChildCount() != runs.length()) {
            return false;
        }
        for (int i = 0; i < runs.length(); i++) {
            JSONObject run = runs.optJSONObject(i);
            View child = runsContainer.getChildAt(i);
            String id = run == null ? "" : run.optString("id", "");
            if (id.isEmpty() || child == null || !runViewTag(id).equals(child.getTag())) {
                return false;
            }
        }
        for (int i = 0; i < runs.length(); i++) {
            JSONObject run = runs.optJSONObject(i);
            View child = runsContainer.getChildAt(i);
            updateRunCardInPlace(child, run);
        }
        return true;
    }

    private void updateRunCardInPlace(View root, JSONObject run) {
        if (root == null || run == null) {
            return;
        }
        TextView command = taggedTextView(root, TAG_RUN_COMMAND);
        if (command != null) {
            command.setText(displayText(commandTextForDisplay(run, isEnglish() ? "(unknown command)" : "（未知命令）")));
        }
        TextView status = taggedTextView(root, TAG_RUN_STATUS);
        String statusValue = run.optString("status", "unknown");
        if (status != null) {
            status.setText(statusLabel(statusValue));
            status.setTextColor(statusColor(statusValue));
            status.setBackground(roundedBg(statusBadgeColor(statusValue), 7, Color.TRANSPARENT));
        }
        View dot = taggedView(root, TAG_RUN_DOT);
        if (dot != null) {
            dot.setBackground(roundedBg(statusColor(statusValue), 99, Color.TRANSPARENT));
        }
        TextView meta = taggedTextView(root, TAG_RUN_META);
        if (meta != null) {
            meta.setText(runMetaText(run));
        }
        TextView output = taggedTextView(root, TAG_RUN_OUTPUT);
        if (output != null) {
            String latest = latestOutputLine(run);
            boolean hasOutput = !latest.isEmpty();
            output.setText(hasOutput ? displayText(latest) : (isEnglish() ? "(no output)" : "（暂无输出）"));
            output.setTextColor(hasOutput ? textPrimary() : textSecondary());
        }
    }

    private TextView taggedTextView(View root, String tag) {
        View view = taggedView(root, tag);
        return view instanceof TextView ? (TextView) view : null;
    }

    private View taggedView(View root, String tag) {
        if (root == null) {
            return null;
        }
        Object current = root.getTag();
        if (tag.equals(current)) {
            return root;
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = taggedView(group.getChildAt(i), tag);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private String runViewTag(String runId) {
        return TAG_RUN_PREFIX + (runId == null ? "" : runId);
    }

    private JSONArray filterRuns(JSONArray runs) {
        boolean archivedView = "archived".equals(selectedStatusFilter);
        boolean allDevices = selectedDeviceId == null || "all".equals(selectedDeviceId);
        Set<String> archived = archivedRunIds();
        Set<String> pendingDeletes = pendingRunDeleteIds();
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
            if (!id.isEmpty() && pendingDeletes.contains(id)) {
                continue;
            }
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
        return orderPinnedRuns(filtered);
    }

    private JSONArray orderPinnedRuns(JSONArray runs) {
        List<String> pinnedOrder = pinnedRunIdList();
        if (pinnedOrder.isEmpty()) {
            return runs;
        }
        Map<String, JSONObject> pinnedById = new HashMap<>();
        JSONArray ordered = new JSONArray();
        JSONArray rest = new JSONArray();
        for (int i = 0; i < runs.length(); i++) {
            JSONObject run = runs.optJSONObject(i);
            if (run == null) {
                continue;
            }
            String id = run.optString("id", "");
            if (!id.isEmpty() && pinnedOrder.contains(id)) {
                pinnedById.put(id, run);
            } else {
                rest.put(run);
            }
        }
        for (String id : pinnedOrder) {
            JSONObject run = pinnedById.get(id);
            if (run != null) {
                ordered.put(run);
            }
        }
        for (int i = 0; i < rest.length(); i++) {
            ordered.put(rest.optJSONObject(i));
        }
        return ordered;
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
        if ("failed".equals(filter)) {
            return isFailedLikeStatus(status);
        }
        return filter != null && filter.equals(status);
    }

    private boolean isFailedLikeStatus(String status) {
        return "failed".equals(status) || "cancelled".equals(status);
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
        if (cached == null || cached.isEmpty()) {
            if (runsContainer != null) {
                renderRuns(new JSONArray(), true);
            }
            return;
        }
        if (runsContainer == null) {
            return;
        }
        try {
            renderRuns(new JSONArray(cached), true);
            long savedAt = prefs.getLong(cacheAtKey, 0L);
            if (savedAt > 0L && statusText != null) {
                statusText.setText(isEnglish() ? "Saved results. Tap refresh for latest." : "正在显示保存结果。点刷新获取最新内容。");
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
        runs = applyPendingRunDeletes(runs);
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

    private JSONArray attachCachedConsolePreviews(JSONArray runs) {
        if (runs == null) {
            return new JSONArray();
        }
        for (int i = 0; i < runs.length(); i++) {
            JSONObject run = runs.optJSONObject(i);
            if (run == null) {
                continue;
            }
            attachCachedConsolePreview(run);
        }
        return runs;
    }

    private void attachCachedConsolePreview(JSONObject run) {
        String id = run == null ? "" : run.optString("id", "").trim();
        if (id.isEmpty() || hasConsoleOutput(run)) {
            return;
        }
        JSONObject cached = loadCachedRunDetailJson(id);
        if (cached == null) {
            return;
        }
        String output = cachedConsoleOutput(cached);
        if (output.isEmpty() || isNoOutputPlaceholder(output)) {
            return;
        }
        try {
            run.put("outputTail", output);
            if (cached.has("outputLength")) {
                run.put("outputLength", Math.max(run.optInt("outputLength", 0), cached.optInt("outputLength", 0)));
            }
            int localChunks = cachedLocalOutputChunkCount(cached);
            if (localChunks > 0) {
                run.put("localOutputChunkCount", localChunks);
            }
        } catch (Exception ignored) {
        }
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
                    if (!id.isEmpty() && !isLocallyRevokedDevice(id)) {
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
                if (id.isEmpty() || isLocallyRevokedDevice(id)) {
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
        boolean pinned = isRunPinned(runId);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(15), dp(13), dp(15), dp(13));
        card.setBackground(roundedBg(pinned ? pinnedRunCardBg() : cardBg(), 20, pinned ? pinnedRunCardStroke() : cardStroke()));
        card.setElevation(0);
        if (Build.VERSION.SDK_INT >= 21) {
            card.setTranslationZ(0);
        }
        card.setClickable(true);
        card.setOnClickListener(v -> openRunDetail(runId));

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                Math.max(dp(280), getResources().getDisplayMetrics().widthPixels - dp(36)),
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, 0, dp(10), 0);
        card.setLayoutParams(cardParams);

        String status = run.optString("status", "unknown");
        LinearLayout topLine = new LinearLayout(this);
        topLine.setOrientation(LinearLayout.HORIZONTAL);
        topLine.setGravity(Gravity.CENTER_VERTICAL);

        View dot = statusDot(statusColor(status));
        dot.setTag(TAG_RUN_DOT);
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(9), dp(9));
        dotParams.setMargins(0, 0, dp(8), 0);
        topLine.addView(dot, dotParams);

        TextView command = new TextView(this);
        command.setTag(TAG_RUN_COMMAND);
        command.setText(displayText(commandTextForDisplay(run, isEnglish() ? "(unknown command)" : "（未知命令）")));
        command.setTextSize(14);
        command.setTextColor(textPrimary());
        command.setTypeface(null, Typeface.BOLD);
        command.setSingleLine(true);
        command.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams commandParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        commandParams.setMargins(dp(8), 0, dp(8), 0);
        topLine.addView(command, commandParams);

        TextView label = new TextView(this);
        label.setTag(TAG_RUN_STATUS);
        label.setText(statusLabel(status));
        label.setTextSize(11);
        label.setTypeface(null, Typeface.BOLD);
        label.setTextColor(statusColor(status));
        label.setPadding(dp(6), dp(2), dp(6), dp(2));
        label.setBackground(roundedBg(statusBadgeColor(status), 7, Color.TRANSPARENT));
        if (pinned) {
            TextView pinnedBadge = new TextView(this);
            pinnedBadge.setText(isEnglish() ? "↑ PINNED" : "↑ 置顶");
            pinnedBadge.setTextSize(10);
            pinnedBadge.setTypeface(null, Typeface.BOLD);
            pinnedBadge.setTextColor(pinnedRunText());
            pinnedBadge.setPadding(dp(7), dp(2), dp(7), dp(2));
            pinnedBadge.setBackground(roundedBg(pinnedRunBadgeBg(), 99, Color.TRANSPARENT));
            LinearLayout.LayoutParams pinnedParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            pinnedParams.setMargins(0, 0, dp(6), 0);
            topLine.addView(pinnedBadge, pinnedParams);
        }
        topLine.addView(label, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        card.addView(topLine, matchWrap());

        TextView meta = new TextView(this);
        meta.setTag(TAG_RUN_META);
        meta.setText(runMetaText(run));
        meta.setTextSize(11);
        meta.setTextColor(textSecondary());
        meta.setPadding(0, dp(3), 0, 0);
        card.addView(meta, matchWrap());

        String latest = latestOutputLine(run);
        boolean hasOutput = !latest.isEmpty();
        TextView output = new TextView(this);
        output.setTag(TAG_RUN_OUTPUT);
        output.setText(hasOutput ? displayText(latest) : (isEnglish() ? "(no output)" : "（暂无输出）"));
        output.setTextSize(11);
        output.setTextColor(hasOutput ? textPrimary() : textSecondary());
        output.setTypeface(android.graphics.Typeface.MONOSPACE);
        output.setSingleLine(true);
        output.setPadding(dp(10), dp(8), dp(10), dp(8));
        output.setBackground(roundedBg(outputPreviewBg(), 12, outputPreviewStroke()));
        LinearLayout.LayoutParams outputParams = matchWrap();
        outputParams.setMargins(0, dp(8), 0, 0);
        card.addView(output, outputParams);

        return swipeableRunCard(card, runId);
    }

    private String runMetaText(JSONObject run) {
        String deviceName = run.optString("deviceName", "");
        String projectName = run.optString("project", "").trim();
        String shownDevice = deviceName.isEmpty() ? appDisplayName() + " CLI" : deviceName;
        String projectPrefix = projectName.isEmpty() ? "" : projectName + " · ";
        return durationText(run) + " · " + projectPrefix + shownDevice + statusExitSuffix(run);
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
        command.setText(run == null ? (isEnglish() ? "(unknown run)" : "（未知运行）") : displayText(commandTextForDisplay(run, isEnglish() ? "(unknown command)" : "（未知命令）")));
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

    private View swipeableRunCard(View card, String runId) {
        final int leftActionWidth = dp(88);
        final int rightActionsWidth = dp(152);
        HorizontalScrollView scroller = new HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroller.setFillViewport(false);
        scroller.setBackgroundColor(Color.TRANSPARENT);
        scroller.setTag(runViewTag(runId));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout leftActions = new LinearLayout(this);
        leftActions.setOrientation(LinearLayout.HORIZONTAL);
        leftActions.setGravity(Gravity.CENTER);
        leftActions.setPadding(0, 0, dp(8), 0);
        boolean pinned = isRunPinned(runId);
        TextView pin = swipeActionButton(pinned ? "↓" : "↑", pinned ? (isEnglish() ? "Unpin" : "取消") : (isEnglish() ? "Pin" : "置顶"), pinActionBg(), pinActionText());
        pin.setOnClickListener(v -> {
            setRunPinned(runId, !pinned);
            if (statusText != null) {
                statusText.setText(pinned
                        ? (isEnglish() ? "Run unpinned." : "已取消置顶。")
                        : (isEnglish() ? "Run pinned." : "已置顶。"));
            }
            scroller.postDelayed(() -> scroller.smoothScrollTo(leftActionWidth, 0), 80);
        });
        leftActions.addView(pin, new LinearLayout.LayoutParams(dp(76), dp(86)));
        row.addView(leftActions, new LinearLayout.LayoutParams(leftActionWidth, LinearLayout.LayoutParams.WRAP_CONTENT));
        row.addView(card);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER);
        actions.setPadding(0, 0, 0, 0);

        boolean archived = isRunArchived(runId);
        TextView archive = swipeActionButton(archived ? "↩" : "◇", archived ? (isEnglish() ? "Restore" : "恢复") : (isEnglish() ? "Archive" : "归档"), archiveActionBg(), archiveActionText());
        archive.setOnClickListener(v -> {
            setRunArchived(runId, !archived);
            if (statusText != null) {
                statusText.setText(archived
                        ? (isEnglish() ? "Run restored." : "已恢复。")
                        : (isEnglish() ? "Run archived." : "已归档。"));
            }
            scroller.postDelayed(() -> scroller.smoothScrollTo(leftActionWidth, 0), 80);
        });
        LinearLayout.LayoutParams archiveParams = new LinearLayout.LayoutParams(dp(72), dp(86));
        archiveParams.setMargins(dp(8), 0, 0, 0);
        actions.addView(archive, archiveParams);

        TextView delete = swipeActionButton("⌫", t("delete"), deleteActionBg(), Color.WHITE);
        delete.setOnClickListener(v -> {
            scroller.postDelayed(() -> scroller.smoothScrollTo(leftActionWidth, 0), 80);
            deleteRun(runId);
        });
        LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(dp(72), dp(86));
        deleteParams.setMargins(dp(8), 0, 0, 0);
        actions.addView(delete, deleteParams);

        row.addView(actions, new LinearLayout.LayoutParams(rightActionsWidth, LinearLayout.LayoutParams.WRAP_CONTENT));
        scroller.addView(row, new HorizontalScrollView.LayoutParams(
                HorizontalScrollView.LayoutParams.WRAP_CONTENT,
                HorizontalScrollView.LayoutParams.WRAP_CONTENT
        ));
        scroller.post(() -> scroller.scrollTo(leftActionWidth, 0));
        final int[] gestureStartScrollX = new int[]{leftActionWidth};
        scroller.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                gestureStartScrollX[0] = scroller.getScrollX();
            }
            if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                scroller.postDelayed(() -> {
                    int scrollX = scroller.getScrollX();
                    int leftOpen = leftActionWidth - dp(38);
                    int rightOpen = leftActionWidth + dp(92);
                    int target = leftActionWidth;
                    boolean startedLeftOpen = gestureStartScrollX[0] < leftActionWidth / 2;
                    boolean startedRightOpen = gestureStartScrollX[0] > leftActionWidth + rightActionsWidth / 2;
                    if (startedLeftOpen && scrollX > leftActionWidth) {
                        target = leftActionWidth;
                    } else if (startedRightOpen && scrollX < leftActionWidth) {
                        target = leftActionWidth;
                    } else if (scrollX < leftOpen) {
                        target = 0;
                    } else if (scrollX > rightOpen) {
                        target = leftActionWidth + rightActionsWidth;
                    }
                    scroller.smoothScrollTo(target, 0);
                }, 40);
            }
            return false;
        });
        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(0, 0, 0, dp(11));
        scroller.setLayoutParams(params);
        return scroller;
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
        addInfoRow(box, isEnglish() ? "Command" : "命令", commandTextForDisplay(run, ""));
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
        sb.append(isEnglish() ? "Command: " : "命令：").append(commandTextForDisplay(run, "")).append('\n');
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
            java.text.SimpleDateFormat out = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA);
            out.setTimeZone(java.util.TimeZone.getTimeZone("Asia/Shanghai"));
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

    private Set<String> pinnedRunIds() {
        return new HashSet<>(pinnedRunIdList());
    }

    private List<String> pinnedRunIdList() {
        List<String> list = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        String raw = prefs.getString(PREF_PINNED_RUNS, "");
        if (raw != null && !raw.isEmpty()) {
            try {
                JSONArray a = new JSONArray(raw);
                for (int i = 0; i < a.length(); i++) {
                    String s = a.optString(i, "");
                    if (!s.isEmpty() && !seen.contains(s)) {
                        list.add(s);
                        seen.add(s);
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return list;
    }

    private boolean isRunPinned(String id) {
        return id != null && !id.isEmpty() && pinnedRunIds().contains(id);
    }

    private void setRunPinned(String id, boolean pinned) {
        if (id == null || id.isEmpty()) {
            return;
        }
        List<String> list = pinnedRunIdList();
        list.remove(id);
        if (pinned) {
            list.add(0, id);
        }
        JSONArray a = new JSONArray();
        for (String s : list) {
            a.put(s);
        }
        prefs.edit().putString(PREF_PINNED_RUNS, a.toString()).apply();
        lastRunsSig = "";
        loadCachedRuns();
        refreshRuns();
    }

    private void removePinnedRun(String id) {
        if (id == null || id.isEmpty()) {
            return;
        }
        List<String> list = pinnedRunIdList();
        if (!list.remove(id)) {
            return;
        }
        JSONArray a = new JSONArray();
        for (String s : list) {
            a.put(s);
        }
        prefs.edit().putString(PREF_PINNED_RUNS, a.toString()).apply();
        lastRunsSig = "";
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
        root.setPadding(dp(16), statusBarHeight() + dp(12), dp(16), navigationBarHeight() + dp(14));
        root.setBackgroundColor(appBg());

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView backButton = circleIconButton("‹");
        backButton.setTextSize(28);
        backButton.setOnClickListener(v -> returnToList());
        top.addView(backButton, new LinearLayout.LayoutParams(dp(44), dp(44)));

        TextView title = new TextView(this);
        title.setText(t("console"));
        title.setTextSize(22);
        title.setTextColor(textPrimary());
        title.setTypeface(null, Typeface.BOLD);
        title.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        titleParams.setMargins(dp(12), 0, dp(8), 0);
        top.addView(title, titleParams);

        TextView searchToggle = circleIconButton("⌕");
        searchToggle.setTextSize(20);
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
        top.addView(searchToggle, new LinearLayout.LayoutParams(dp(44), dp(44)));

        TextView copyButton = circleIconButton("⧉");
        copyButton.setTextSize(18);
        copyButton.setOnClickListener(v -> copyConsoleOutput());
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(dp(44), dp(44));
        copyParams.setMargins(dp(8), 0, 0, 0);
        top.addView(copyButton, copyParams);

        TextView infoButton = circleIconButton("ⓘ");
        infoButton.setTextSize(18);
        infoButton.setOnClickListener(v -> showRunInfoDialog());
        LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(dp(44), dp(44));
        infoParams.setMargins(dp(8), 0, 0, 0);
        top.addView(infoButton, infoParams);

        root.addView(top, matchWrap());

        LinearLayout commandCard = new LinearLayout(this);
        commandCard.setOrientation(LinearLayout.VERTICAL);
        commandCard.setPadding(dp(14), dp(12), dp(14), dp(12));
        commandCard.setBackground(roundedBg(cardBg(), 20, cardStroke()));
        LinearLayout.LayoutParams commandCardParams = matchWrap();
        commandCardParams.setMargins(0, dp(12), 0, dp(10));

        TextView commandLabel = new TextView(this);
        commandLabel.setText(isEnglish() ? "COMMAND" : "运行命令");
        commandLabel.setTextSize(10);
        commandLabel.setLetterSpacing(0.06f);
        commandLabel.setTypeface(null, Typeface.BOLD);
        commandLabel.setTextColor(textSecondary());
        commandCard.addView(commandLabel, matchWrap());

        detailCommand = new TextView(this);
        detailCommand.setText(isEnglish() ? "Loading command..." : "正在加载命令...");
        detailCommand.setTextSize(15);
        detailCommand.setTextColor(textPrimary());
        detailCommand.setTypeface(null, Typeface.BOLD);
        detailCommand.setPadding(0, dp(6), 0, 0);
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
        commandCard.addView(detailCommand, matchWrap());

        detailMeta = new TextView(this);
        detailMeta.setText("");
        detailMeta.setTextSize(11);
        detailMeta.setTypeface(null, Typeface.BOLD);
        detailMeta.setTextColor(textSecondary());
        detailMeta.setPadding(dp(9), dp(4), dp(9), dp(4));
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        metaParams.setMargins(0, dp(9), 0, 0);
        commandCard.addView(detailMeta, metaParams);

        consoleInterruptButton = actionButton(actionLabel("■", t("interrupt"), 1.12f));
        consoleInterruptButton.setTextColor(color("#B42318"));
        consoleInterruptButton.setOnClickListener(v -> confirmInterruptRun());
        LinearLayout.LayoutParams interruptParams = matchWrap();
        interruptParams.setMargins(0, dp(10), 0, 0);
        consoleInterruptButton.setVisibility(View.GONE);
        commandCard.addView(consoleInterruptButton, interruptParams);
        root.addView(commandCard, commandCardParams);

        consoleSearchInput = new EditText(this);
        consoleSearchInput.setSingleLine(true);
        consoleSearchInput.setTextSize(14);
        consoleSearchInput.setHint(isEnglish() ? "⌕  Search console output" : "⌕  搜索控制台输出");
        consoleSearchInput.setInputType(InputType.TYPE_CLASS_TEXT);
        consoleSearchInput.setVisibility(View.GONE);
        styleConsoleSearchInput(consoleSearchInput);
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
        LinearLayout.LayoutParams searchParams = matchWrap();
        searchParams.setMargins(0, 0, 0, dp(10));
        root.addView(consoleSearchInput, searchParams);

        LinearLayout terminalFrame = new LinearLayout(this);
        terminalFrame.setOrientation(LinearLayout.VERTICAL);
        terminalFrame.setPadding(0, 0, 0, 0);
        terminalFrame.setBackground(roundedBg(consoleBg(), 20, consoleStroke()));

        LinearLayout terminalHeader = new LinearLayout(this);
        terminalHeader.setOrientation(LinearLayout.HORIZONTAL);
        terminalHeader.setGravity(Gravity.CENTER_VERTICAL);
        terminalHeader.setPadding(dp(14), dp(10), dp(10), dp(8));
        terminalHeader.setBackground(roundedBg(terminalHeaderBg(), 20, Color.TRANSPARENT));
        terminalHeader.addView(terminalDots(), new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        statusText = new TextView(this);
        statusText.setText(isEnglish() ? "Loading console..." : "正在加载控制台...");
        statusText.setTextSize(11);
        statusText.setTextColor(consoleMutedText());
        statusText.setSingleLine(true);
        statusText.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        statusParams.setMargins(dp(10), 0, dp(8), 0);
        terminalHeader.addView(statusText, statusParams);

        consoleAutoScrollButton = filterPill("↓", "Auto", t("auto_on"));
        consoleAutoScrollButton.setTextSize(11);
        consoleAutoScrollButton.setOnClickListener(v -> {
            consoleAutoScroll = !consoleAutoScroll;
            updateConsoleAutoScrollButton();
            if (consoleAutoScroll && consoleVerticalScroll != null) {
                consoleVerticalScroll.post(() -> consoleVerticalScroll.fullScroll(View.FOCUS_DOWN));
            }
        });
        terminalHeader.addView(consoleAutoScrollButton, new LinearLayout.LayoutParams(dp(104), dp(34)));
        terminalFrame.addView(terminalHeader, matchWrap());

        consoleVerticalScroll = new ScrollView(this);
        consoleVerticalScroll.setFillViewport(false);
        consoleVerticalScroll.setBackgroundColor(Color.TRANSPARENT);
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
        detailConsole.setPadding(dp(14), dp(14), dp(14), dp(16));
        detailConsole.setTypeface(android.graphics.Typeface.MONOSPACE);
        detailConsole.setLineSpacing(dp(2), 1.0f);
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
        terminalFrame.addView(consoleVerticalScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1
        ));
        root.addView(terminalFrame, new LinearLayout.LayoutParams(
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
            consoleAutoScrollButton.setText(actionLabel("↓", consoleAutoScroll ? t("auto_on") : t("auto_off"), 1.05f));
        }
    }

    private LinearLayout terminalDots() {
        LinearLayout dots = new LinearLayout(this);
        dots.setOrientation(LinearLayout.HORIZONTAL);
        dots.setGravity(Gravity.CENTER_VERTICAL);
        int[] colors = new int[]{color("#EF4444"), color("#F59E0B"), color("#22C55E")};
        for (int fill : colors) {
            View dot = statusDot(fill);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(8), dp(8));
            params.setMargins(0, 0, dp(6), 0);
            dots.addView(dot, params);
        }
        return dots;
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
        String output = currentConsoleOutput == null || currentConsoleOutput.isEmpty() ? (isEnglish() ? "No output yet." : "还没有输出。") : displayText(renderTerminalText(currentConsoleOutput));
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
        submitBackground(() -> {
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
        if (!beginRunDetailRefresh(id, showLoading)) {
            return;
        }
        if (showLoading && statusText != null) {
            statusText.setText(isEnglish() ? "Loading console..." : "正在加载控制台...");
        }
        submitBackground(() -> {
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
                final JSONObject run = decryptRun(payload.getJSONObject("run"), incremental ? null : payload.optJSONArray("outputChunks"));
                if (!incremental) {
                    prefs.edit().putString(CACHE_RUN_PREFIX + id, localRunSnapshot(run, consoleOutput(run)).toString()).apply();
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
            } finally {
                finishRunDetailRefresh();
            }
        });
    }

    private boolean beginRunDetailRefresh(String id, boolean showLoading) {
        synchronized (refreshStateLock) {
            if (runDetailRefreshInFlight) {
                runDetailRefreshQueued = true;
                runDetailRefreshQueuedId = id == null ? "" : id;
                runDetailRefreshQueuedShowLoading = runDetailRefreshQueuedShowLoading || showLoading;
                return false;
            }
            runDetailRefreshInFlight = true;
            return true;
        }
    }

    private void finishRunDetailRefresh() {
        boolean queued;
        String id;
        boolean showLoading;
        synchronized (refreshStateLock) {
            runDetailRefreshInFlight = false;
            queued = runDetailRefreshQueued;
            id = runDetailRefreshQueuedId;
            showLoading = runDetailRefreshQueuedShowLoading;
            runDetailRefreshQueued = false;
            runDetailRefreshQueuedId = "";
            runDetailRefreshQueuedShowLoading = false;
        }
        if (queued && id != null && !id.isEmpty() && id.equals(selectedRunId)) {
            handler.post(() -> refreshRunDetail(id, showLoading));
        }
    }

    private void loadCachedRunDetail(String id) {
        JSONObject cached = loadCachedRunDetailJson(id);
        if (cached == null) {
            return;
        }
        try {
            updateRunDetail(cached, true);
        } catch (Exception ignored) {
        }
    }

    private JSONObject loadCachedRunDetailJson(String id) {
        if (id == null || id.trim().isEmpty()) {
            return null;
        }
        String cached = prefs.getString(CACHE_RUN_PREFIX + id, "");
        if (cached == null || cached.isEmpty()) {
            return null;
        }
        try {
            return new JSONObject(cached);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void scheduleMissingLocalOutputsSync(JSONArray runs) {
        if (runs == null || accountToken().isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (backgroundOutputSyncInFlight || now - lastBackgroundOutputSyncAt < BACKGROUND_OUTPUT_SYNC_COOLDOWN_MS) {
            return;
        }
        backgroundOutputSyncInFlight = true;
        lastBackgroundOutputSyncAt = now;
        submitBackground(() -> {
            try {
                syncMissingLocalOutputs(runs);
            } finally {
                backgroundOutputSyncInFlight = false;
            }
        });
    }

    private void syncMissingLocalOutputs(JSONArray runs) {
        if (runs == null || accountToken().isEmpty()) {
            return;
        }
        int synced = 0;
        for (int i = 0; i < runs.length() && synced < MAX_BACKGROUND_OUTPUT_SYNC; i++) {
            JSONObject run = runs.optJSONObject(i);
            if (run == null || !needsLocalOutputSync(run)) {
                continue;
            }
            syncRunOutputForCache(run);
            synced++;
        }
    }

    private boolean needsLocalOutputSync(JSONObject run) {
        String id = run.optString("id", "").trim();
        if (id.isEmpty()) {
            return false;
        }
        String status = run.optString("status", "");
        boolean active = "running".equals(status) || "created".equals(status);
        JSONObject cached = loadCachedRunDetailJson(id);
        int remoteChunks = run.optInt("outputChunkCount", -1);
        int remoteLength = run.optInt("outputLength", 0);
        int localChunks = cachedLocalOutputChunkCount(cached);
        int localLength = cachedOutputLength(cached);
        boolean hasLocalOutput = cached != null && !cachedConsoleOutput(cached).isEmpty() && !isNoOutputPlaceholder(cachedConsoleOutput(cached));

        if (remoteChunks > localChunks) {
            return active || hasLocalOutput || localChunks > 0 || remoteChunks <= 20;
        }
        if (remoteChunks < 0 && remoteLength > localLength) {
            return active || hasLocalOutput;
        }
        return false;
    }

    private void syncRunOutputForCache(JSONObject listRun) {
        String id = listRun.optString("id", "").trim();
        if (id.isEmpty()) {
            return;
        }
        try {
            JSONObject cached = loadCachedRunDetailJson(id);
            int localChunks = cachedLocalOutputChunkCount(cached);
            int localLength = cachedOutputLength(cached);
            StringBuilder url = new StringBuilder(normalizedServerUrl()).append("/api/runs/").append(id);
            if (localChunks > 0) {
                url.append("?outputSince=").append(localChunks);
            } else if (localLength > 0) {
                url.append("?outputLength=").append(localLength);
            }
            JSONObject payload = new JSONObject(httpGet(url.toString(), HTTP_LIST_READ_TIMEOUT_MS));
            boolean incremental = payload.optBoolean("incremental", false);
            JSONObject run = decryptRun(payload.getJSONObject("run"), incremental ? null : payload.optJSONArray("outputChunks"));
            if (incremental) {
                String output = cachedConsoleOutput(cached);
                int remoteLength = payload.optInt("outputLength", run.optInt("outputLength", output.length()));
                boolean hasLocalOutput = !isNoOutputPlaceholder(output);
                boolean alreadyApplied = hasLocalOutput && localLength > 0 && remoteLength > 0 && remoteLength <= localLength;
                String append = payload.optString("outputAppend", "");
                if (!alreadyApplied && !append.isEmpty()) {
                    output += append;
                }
                JSONArray chunks = payload.optJSONArray("outputChunks");
                if (chunks != null && chunks.length() > 0) {
                    if (alreadyApplied) {
                        localChunks = Math.max(localChunks, run.optInt("outputChunkCount", localChunks));
                    } else {
                        output += decryptOutputChunks(id, chunks);
                        localChunks += chunks.length();
                    }
                }
                if (localChunks <= 0) {
                    localChunks = run.optInt("outputChunkCount", 0);
                }
                int length = Math.max(Math.max(output.length(), localLength), remoteLength);
                prefs.edit().putString(CACHE_RUN_PREFIX + id, localRunSnapshot(run, output, localChunks, length).toString()).apply();
            } else {
                prefs.edit().putString(CACHE_RUN_PREFIX + id, localRunSnapshot(run, consoleOutput(run)).toString()).apply();
            }
        } catch (Exception e) {
            Log.w(TAG, "local output sync failed for " + id, e);
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
        detailMeta.setBackground(roundedBg(statusBadgeColor(status), 99, Color.TRANSPARENT));
        updateConsoleInterruptButton("running".equals(status) || "created".equals(status));

        int remoteLength = payload.optInt("outputLength", run.optInt("outputLength", consoleOutputSyncedLength));
        boolean hasCurrentOutput = !isNoOutputPlaceholder(currentConsoleOutput);
        boolean alreadyApplied = hasCurrentOutput
                && consoleOutputSyncedLength > 0
                && remoteLength > 0
                && remoteLength <= consoleOutputSyncedLength;

        String appendText = payload.optString("outputAppend", "");
        if (!alreadyApplied && !appendText.isEmpty()) {
            currentConsoleOutput = (currentConsoleOutput == null ? "" : currentConsoleOutput) + appendText;
            consoleOutputSyncedLength = currentConsoleOutput.length();
        }
        JSONArray chunks = payload.optJSONArray("outputChunks");
        if (chunks != null && chunks.length() > 0) {
            consoleIncrementalUsesChunks = true;
            if (alreadyApplied) {
                outputChunkSyncedCount = Math.max(outputChunkSyncedCount, run.optInt("outputChunkCount", outputChunkSyncedCount));
            } else {
                currentConsoleOutput = (currentConsoleOutput == null ? "" : currentConsoleOutput) + decryptOutputChunks(run.optString("id", ""), chunks);
                outputChunkSyncedCount += chunks.length();
                consoleOutputSyncedLength = currentConsoleOutput.length();
            }
        }
        if (payload.has("outputLength")) {
            consoleOutputSyncedLength = Math.max(consoleOutputSyncedLength, remoteLength);
        }
        persistCurrentRunDetail(run);

        renderConsoleText();
        maybeNotify(run);
        if (("running".equals(status) || "created".equals(status)) && consoleAutoScroll && consoleVerticalScroll != null) {
            consoleVerticalScroll.post(() -> consoleVerticalScroll.fullScroll(View.FOCUS_DOWN));
        }
        if (statusText != null && !isConsoleRenderClipped() && (consoleSearchInput == null || consoleSearchInput.getText().toString().trim().isEmpty())) {
            statusText.setText(isEnglish() ? "Console updated." : "控制台已更新。");
        }
    }

    private void persistCurrentRunDetail(JSONObject run) {
        String id = run == null ? "" : run.optString("id", "").trim();
        if (id.isEmpty()) {
            return;
        }
        try {
            JSONObject base = currentRunDetail == null ? new JSONObject(run.toString()) : new JSONObject(currentRunDetail.toString());
            prefs.edit()
                    .putString(CACHE_RUN_PREFIX + id, localRunSnapshot(base, currentConsoleOutput, outputChunkSyncedCount, consoleOutputSyncedLength).toString())
                    .apply();
        } catch (Exception ignored) {
        }
    }

    private JSONObject localRunSnapshot(JSONObject run, String output) {
        JSONArray chunks = run == null ? null : run.optJSONArray("outputChunks");
        int chunkCount = chunks == null ? cachedLocalOutputChunkCount(run) : chunks.length();
        int outputLength = Math.max(run == null ? 0 : run.optInt("outputLength", 0), output == null ? 0 : output.length());
        return localRunSnapshot(run, output, chunkCount, outputLength);
    }

    private JSONObject localRunSnapshot(JSONObject run, String output, int chunkCount, int outputLength) {
        JSONObject snapshot;
        try {
            snapshot = run == null ? new JSONObject() : new JSONObject(run.toString());
            String safeOutput = output == null || isNoOutputPlaceholder(output) ? "" : limitConsoleOutput(output);
            snapshot.remove("outputChunks");
            snapshot.remove("e2ee");
            snapshot.put("outputTail", safeOutput);
            snapshot.put("stdoutTail", "");
            snapshot.put("stderrTail", "");
            snapshot.put("localOutputChunkCount", Math.max(0, chunkCount));
            snapshot.put("outputChunkCount", Math.max(snapshot.optInt("outputChunkCount", 0), Math.max(0, chunkCount)));
            snapshot.put("outputLength", Math.max(0, outputLength));
        } catch (Exception ignored) {
            snapshot = new JSONObject();
        }
        return snapshot;
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
        detailCommand.setText(displayText(commandTextForDisplay(run, isEnglish() ? "(unknown command)" : "（未知命令）")));
        String projectName = run.optString("project", "").trim();
        String projectSuffix = projectName.isEmpty() ? "" : " · " + projectName;
        detailMeta.setText(status.toUpperCase(Locale.US) + projectSuffix + statusSuffix(run));
        detailMeta.setTextColor(statusColor(status));
        detailMeta.setBackground(roundedBg(statusBadgeColor(status), 99, Color.TRANSPARENT));
        updateConsoleInterruptButton("running".equals(status) || "created".equals(status));
        currentConsoleOutput = consoleOutput(run);
        JSONArray chunks = run.optJSONArray("outputChunks");
        outputChunkSyncedCount = chunks == null ? cachedLocalOutputChunkCount(run) : chunks.length();
        consoleIncrementalUsesChunks = outputChunkSyncedCount > 0 || run.optInt("outputChunkCount", -1) >= 0;
        consoleOutputSyncedLength = Math.max(currentConsoleOutput == null ? 0 : currentConsoleOutput.length(), run.optInt("outputLength", 0));
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
        String searchable = renderTerminalText(consoleWindowRaw());
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

    private String renderTerminalText(String value) {
        return TerminalTextRenderer.render(value);
    }

    private String displayConsoleOutput() {
        String output = renderTerminalText(consoleWindowRaw());
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

    private boolean hasConsoleOutput(JSONObject run) {
        return run != null
                && (!run.optString("outputTail", "").isEmpty()
                || !run.optString("stdoutTail", "").isEmpty()
                || !run.optString("stderrTail", "").isEmpty());
    }

    private String cachedConsoleOutput(JSONObject cached) {
        if (cached == null) {
            return "";
        }
        String output = cached.optString("outputTail", "");
        if (!output.isEmpty()) {
            return output;
        }
        String stdout = cached.optString("stdoutTail", "");
        String stderr = cached.optString("stderrTail", "");
        if (stdout.isEmpty()) {
            return stderr;
        }
        if (stderr.isEmpty()) {
            return stdout;
        }
        return stdout + "\n" + stderr;
    }

    private int cachedLocalOutputChunkCount(JSONObject cached) {
        if (cached == null) {
            return 0;
        }
        if (cached.has("localOutputChunkCount")) {
            return Math.max(0, cached.optInt("localOutputChunkCount", 0));
        }
        JSONArray chunks = cached.optJSONArray("outputChunks");
        if (chunks != null) {
            return chunks.length();
        }
        return Math.max(0, cached.optInt("outputChunkCount", 0));
    }

    private int cachedOutputLength(JSONObject cached) {
        if (cached == null) {
            return 0;
        }
        int length = cached.optInt("outputLength", 0);
        if (length > 0) {
            return length;
        }
        return cachedConsoleOutput(cached).length();
    }

    private boolean isNoOutputPlaceholder(String output) {
        if (output == null) {
            return true;
        }
        String value = output.trim();
        return value.isEmpty() || "No output yet.".equals(value) || "还没有输出。".equals(value);
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
        rememberPendingRunDelete(id);
        removePinnedRun(id);
        knownStatuses.remove(id);
        removeRunFromCaches(id);
        if (id.equals(selectedRunId)) {
            selectedRunId = null;
            selectedRunStatus = "";
        }
        lastRunsSig = "";
        loadCachedRuns();
        statusText.setText(isEnglish()
                ? "Deleted locally. Cloud delete will sync when online."
                : "已先从本机删除。联网后会继续删除云端。");
        submitBackground(() -> {
            boolean deleted = deleteRunFromCloud(id);
            if (deleted) {
                forgetPendingRunDelete(id);
                handler.post(() -> statusText.setText(isEnglish() ? "Deleted from cloud." : "云端已删除。"));
            } else {
                handler.post(() -> statusText.setText(isEnglish()
                        ? "Deleted locally. Cloud delete is pending."
                        : "已从本机删除，云端删除待同步。"));
            }
        });
    }

    private Set<String> pendingRunDeleteIds() {
        Set<String> ids = new HashSet<>();
        if (prefs == null) {
            return ids;
        }
        String raw = prefs.getString(PREF_PENDING_RUN_DELETES, "[]");
        try {
            JSONArray array = new JSONArray(raw == null || raw.trim().isEmpty() ? "[]" : raw);
            for (int i = 0; i < array.length(); i++) {
                String id = array.optString(i, "").trim();
                if (!id.isEmpty()) {
                    ids.add(id);
                }
            }
        } catch (Exception ignored) {
        }
        return ids;
    }

    private void savePendingRunDeleteIds(Set<String> ids) {
        if (prefs == null) {
            return;
        }
        JSONArray array = new JSONArray();
        List<String> sorted = new ArrayList<>(ids);
        Collections.sort(sorted);
        for (String id : sorted) {
            if (id != null && !id.trim().isEmpty()) {
                array.put(id.trim());
            }
        }
        prefs.edit().putString(PREF_PENDING_RUN_DELETES, array.toString()).apply();
    }

    private void rememberPendingRunDelete(String id) {
        if (id == null || id.trim().isEmpty()) {
            return;
        }
        Set<String> ids = pendingRunDeleteIds();
        ids.add(id.trim());
        savePendingRunDeleteIds(ids);
    }

    private void forgetPendingRunDelete(String id) {
        if (id == null || id.trim().isEmpty()) {
            return;
        }
        Set<String> ids = pendingRunDeleteIds();
        if (ids.remove(id.trim())) {
            savePendingRunDeleteIds(ids);
        }
    }

    private JSONArray applyPendingRunDeletes(JSONArray runs) {
        Set<String> ids = pendingRunDeleteIds();
        if (ids.isEmpty() || runs == null) {
            return runs == null ? new JSONArray() : runs;
        }
        JSONArray kept = new JSONArray();
        for (int i = 0; i < runs.length(); i++) {
            JSONObject run = runs.optJSONObject(i);
            String id = run == null ? "" : run.optString("id", "");
            if (!id.isEmpty() && ids.contains(id)) {
                continue;
            }
            kept.put(runs.opt(i));
        }
        return kept;
    }

    private void syncPendingRunDeletesAsync(boolean showResult) {
        if (pendingRunDeleteIds().isEmpty()) {
            return;
        }
        submitBackground(() -> {
            int synced = syncPendingRunDeletesBlocking();
            if (showResult && synced > 0) {
                handler.post(() -> statusText.setText(isEnglish()
                        ? "Synced " + synced + " pending cloud delete(s)."
                        : "已同步 " + synced + " 条待删除云端记录。"));
            }
        });
    }

    private int syncPendingRunDeletesBlocking() {
        if (pendingRunDeleteSyncing || prefs == null || pendingRunDeleteIds().isEmpty()) {
            return 0;
        }
        pendingRunDeleteSyncing = true;
        int synced = 0;
        try {
            List<String> ids = new ArrayList<>(pendingRunDeleteIds());
            for (String id : ids) {
                if (deleteRunFromCloud(id)) {
                    forgetPendingRunDelete(id);
                    synced++;
                }
            }
        } finally {
            pendingRunDeleteSyncing = false;
        }
        return synced;
    }

    private boolean deleteRunFromCloud(String id) {
        if (id == null || id.trim().isEmpty()) {
            return true;
        }
        try {
            httpRequest(normalizedServerUrl() + "/api/runs/" + Uri.encode(id.trim()), "DELETE");
            return true;
        } catch (HaolemeHttpException e) {
            return e.statusCode == 404;
        } catch (Exception ignored) {
            return false;
        }
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

        String command = displayText(commandTextForDisplay(run, "Command"));
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
        submitBackground(() -> {
            try {
                PairInfoResult pairInfo = fetchPairInfoWithFallback(targetServer, normalizedCode);
                JSONObject info = pairInfo.info;
                String confirmedServer = pairInfo.server;

                // Step 2: build the confirm payload (encrypt the account key to
                // the CLI's public key).
                JSONObject payload = new JSONObject();
                payload.put("code", normalizedCode);
                payload.put("appVersionCode", currentVersionCode());
                payload.put("appVersionName", currentVersionName());
                payload.put("platform", "android");
                String pairDeviceId = info == null ? "" : info.optString("deviceId", "").trim();
                String reusableDeviceId = reusablePairDeviceId(pairDeviceId);
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
                String responseText = httpPostJson(confirmedServer + "/api/pair/confirm", payload.toString());
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
                String finalServer = confirmedServer;
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
                            .putString("paired_server_url", finalServer)
                            .putString("server_url", finalServer)
                            .apply();
                    if (!finalDeviceId.isEmpty()) {
                        selectedDeviceId = finalDeviceId;
                        prefs.edit().putString("selected_device_id", selectedDeviceId).apply();
                        cachePairedDevice(finalDeviceId, finalDeviceName, finalPairedAt);
                        currentTab = "devices";
                        buildUi();
                    }
                    statusText.setText(isEnglish() ? "Paired with " + finalDeviceName + ". Refreshing..." : "已配对 " + finalDeviceName + "，正在刷新...");
                    refreshHome(false);
                    maybePromptRenameLongPairedDevice(finalDeviceId, finalDeviceName);
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

    private void maybePromptRenameLongPairedDevice(String deviceId, String deviceName) {
        if (deviceId == null || deviceId.trim().isEmpty() || !isLongDeviceName(deviceName)) {
            return;
        }
        String finalDeviceId = deviceId.trim();
        String finalDeviceName = deviceName == null ? "" : deviceName.trim();
        handler.postDelayed(() -> {
            if (isFinishing()) {
                return;
            }
            statusText.setText(isEnglish()
                    ? "This device name is long. Give it a shorter name."
                    : "这个设备名有点长，建议改成更短的名字。");
            showRenameDeviceDialog(finalDeviceId, finalDeviceName);
        }, 500L);
    }

    private boolean isLongDeviceName(String deviceName) {
        String name = deviceName == null ? "" : deviceName.trim();
        if (name.length() > 18) {
            return true;
        }
        String lower = name.toLowerCase(Locale.US);
        return name.length() > 12 && (lower.contains(".local") || lower.contains(".lan") || lower.contains(".internal"));
    }

    private PairInfoResult fetchPairInfoWithFallback(String initialServer, String normalizedCode) throws Exception {
        String primary = normalizeServerUrl(initialServer);
        try {
            return fetchPairInfoWithRetry(primary, normalizedCode);
        } catch (HaolemeHttpException first) {
            String fallback = normalizeServerUrl(DEFAULT_SERVER_URL);
            if (isPairCodeMissing(first) && !fallback.equals(primary)) {
                handler.post(() -> statusText.setText(isEnglish()
                        ? "Pair code not found on saved server. Trying Haoleme Cloud..."
                        : "当前服务器没有找到配对码，正在切换到好了么云端..."));
                try {
                    return fetchPairInfoWithRetry(fallback, normalizedCode);
                } catch (HaolemeHttpException second) {
                    if (!isPairCodeMissing(second)) {
                        throw second;
                    }
                }
            }
            throw first;
        }
    }

    private PairInfoResult fetchPairInfoWithRetry(String server, String normalizedCode) throws Exception {
        int maxInfoAttempts = 3;
        for (int attempt = 1; attempt <= maxInfoAttempts; attempt++) {
            try {
                JSONObject infoPayload = new JSONObject();
                infoPayload.put("code", normalizedCode);
                String infoText = httpPostJson(server + "/api/pair/info", infoPayload.toString());
                JSONObject info = infoText.isEmpty() ? new JSONObject() : new JSONObject(infoText);
                return new PairInfoResult(server, info);
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
        throw new IOException("pair info unavailable");
    }

    private boolean isPairCodeMissing(HaolemeHttpException e) {
        return e != null && ("pair_code_expired".equals(e.errorCode()) || e.statusCode == 404);
    }

    private String reusablePairDeviceId(String pairDeviceId) {
        String pairId = pairDeviceId == null ? "" : pairDeviceId.trim();
        String deviceId = selectedDeviceId == null ? "" : selectedDeviceId.trim();
        if (!pairId.isEmpty() && pairId.equals(deviceId)) {
            return deviceId;
        }
        String pairedId = prefs.getString("paired_device_id", "");
        pairedId = pairedId == null ? "" : pairedId.trim();
        if (!pairId.isEmpty() && pairId.equals(pairedId)) {
            return pairedId;
        }
        return "";
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
        submitBackground(() -> {
            try {
                JSONObject payload = null;
                String usedUrl = "";
                int bestVersionCode = -1;
                Exception lastError = null;
                for (String updateUrl : urlsToTry) {
                    try {
                        String body = httpGetPublic(updateUrl);
                        JSONObject candidatePayload = new JSONObject(body);
                        JSONObject candidateAndroid = candidatePayload.optJSONObject("android");
                        if (candidateAndroid == null) {
                            candidateAndroid = candidatePayload;
                        }
                        int candidateCode = candidateAndroid.optInt("versionCode", 0);
                        if (payload == null || candidateCode > bestVersionCode) {
                            payload = candidatePayload;
                            usedUrl = updateUrl;
                            bestVersionCode = candidateCode;
                        }
                    } catch (Exception e) {
                        lastError = e;
                    }
                }
                if (payload == null) {
                    throw lastError == null ? new IllegalStateException("No update source worked") : lastError;
                }

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
        submitBackground(() -> {
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
        submitBackground(() -> {
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
            return " · " + formatIsoLocal(time);
        }
        return " · exit " + exitCode + " · " + formatIsoLocal(time);
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
        String[] lines = renderTerminalText(output).split("\\r?\\n");
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

    private int pinnedRunCardBg() {
        return isDarkTheme() ? color("#231F18") : color("#FFFDF7");
    }

    private int pinnedRunCardStroke() {
        return isDarkTheme() ? color("#7A5A1E") : color("#E7C66A");
    }

    private int pinnedRunBadgeBg() {
        return isDarkTheme() ? color("#3A2B12") : color("#FFF1C2");
    }

    private int pinnedRunText() {
        return isDarkTheme() ? color("#FACC6B") : color("#8A5A00");
    }

    private int settingsHeroBg() {
        return isDarkTheme() ? color("#1B1B20") : color("#FFFFFF");
    }

    private int settingsHeroStroke() {
        return isDarkTheme() ? color("#303039") : color("#E7EAF0");
    }

    private int settingsHeroBadgeBg() {
        if (hasAvailableUpdate()) {
            return isDarkTheme() ? color("#3B1518") : color("#FEE2E2");
        }
        if (hasPairedDevice()) {
            return isDarkTheme() ? color("#123524") : color("#DCFCE7");
        }
        return isDarkTheme() ? color("#18233A") : color("#EAF2FF");
    }

    private int settingsHeroBadgeText() {
        if (hasAvailableUpdate()) {
            return isDarkTheme() ? color("#FDA4AF") : color("#DC2626");
        }
        if (hasPairedDevice()) {
            return isDarkTheme() ? color("#86EFAC") : color("#16A34A");
        }
        return isDarkTheme() ? color("#93C5FD") : color("#2563EB");
    }

    private int iconChipBg() {
        return isDarkTheme() ? color("#2B2B31") : color("#F5F7FA");
    }

    private int settingsIconChipBg(int accent) {
        return blendColors(cardBg(), accent, isDarkTheme() ? 0.18f : 0.11f);
    }

    private int settingsValueBg() {
        return isDarkTheme() ? color("#27272D") : color("#F4F6FA");
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

    private int topTabMutedText() {
        return isDarkTheme() ? color("#B8B8BE") : color("#737987");
    }

    private int topTabAccent() {
        return color("#FF4D63");
    }

    private int mutedDotColor() {
        return isDarkTheme() ? color("#5F6068") : color("#C9CED8");
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

    private int homeHeroBg() {
        return isDarkTheme() ? color("#1C1C20") : color("#FFFFFF");
    }

    private int homeHeroStroke() {
        return isDarkTheme() ? color("#2D2D33") : color("#ECEEF3");
    }

    private int filterPillBg() {
        return isDarkTheme() ? color("#202126") : color("#FFFFFF");
    }

    private int filterPillStroke() {
        return isDarkTheme() ? color("#30313A") : color("#E8EAF0");
    }

    private int circleButtonBg() {
        return isDarkTheme() ? color("#26272D") : color("#F6F7FA");
    }

    private int circleButtonStroke() {
        return isDarkTheme() ? color("#353640") : color("#E7E9EF");
    }

    private int deviceChipBg() {
        return isDarkTheme() ? color("#18191D") : color("#F3F5F8");
    }

    private int deviceChipStroke() {
        return isDarkTheme() ? color("#2A2B31") : color("#E5E8EE");
    }

    private int deviceChipSelectedBg() {
        return isDarkTheme() ? color("#F5F5F6") : color("#16181D");
    }

    private int deviceChipSelectedText() {
        return isDarkTheme() ? color("#111217") : Color.WHITE;
    }

    private int archiveActionBg() {
        return isDarkTheme() ? color("#2B2F3A") : color("#EEF2F8");
    }

    private int archiveActionText() {
        return isDarkTheme() ? color("#E5E7EB") : color("#344054");
    }

    private int pinActionBg() {
        return isDarkTheme() ? color("#173A31") : color("#E8F7F0");
    }

    private int pinActionText() {
        return isDarkTheme() ? color("#A7F3D0") : color("#087443");
    }

    private int deleteActionBg() {
        return isDarkTheme() ? color("#B42318") : color("#EF4444");
    }

    private int inputBg() {
        return isDarkTheme() ? color("#26262A") : Color.WHITE;
    }

    private int consoleBg() {
        return isDarkTheme() ? color("#0F1012") : color("#FFFFFF");
    }

    private int outputPreviewBg() {
        return isDarkTheme() ? color("#141417") : color("#F8F8FA");
    }

    private int outputPreviewStroke() {
        return isDarkTheme() ? color("#24242A") : color("#ECECF1");
    }

    private int consoleText() {
        return isDarkTheme() ? color("#E8EAF0") : color("#1F2933");
    }

    private int consoleStroke() {
        return isDarkTheme() ? color("#2C2D33") : color("#DDE3EC");
    }

    private int terminalHeaderBg() {
        return isDarkTheme() ? color("#15161A") : color("#F5F7FA");
    }

    private int consoleMutedText() {
        return isDarkTheme() ? color("#9CA3AF") : color("#6B7280");
    }

    private int searchInputBg() {
        return isDarkTheme() ? color("#1D1E23") : color("#FFFFFF");
    }

    private int searchInputStroke() {
        return isDarkTheme() ? color("#30323A") : color("#E7EAF0");
    }

    private int surfaceStroke() {
        return isDarkTheme() ? color("#2C2C2E") : color("#E5E5EA");
    }

    private int gpuTrackColor() {
        return isDarkTheme() ? color("#9CA3AF") : color("#6B7280");
    }

    private int cpuChartLineColor() {
        return isDarkTheme() ? color("#60A5FA") : color("#2563EB");
    }

    private int cpuChartFillColor() {
        return isDarkTheme() ? color("#1E3A5F") : color("#DBEAFE");
    }

    private int blendColors(int base, int overlay, float amount) {
        float clamped = Math.max(0f, Math.min(1f, amount));
        int r = Math.round(Color.red(base) * (1f - clamped) + Color.red(overlay) * clamped);
        int g = Math.round(Color.green(base) * (1f - clamped) + Color.green(overlay) * clamped);
        int b = Math.round(Color.blue(base) * (1f - clamped) + Color.blue(overlay) * clamped);
        return Color.rgb(r, g, b);
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

    private void styleConsoleSearchInput(EditText input) {
        input.setTextColor(textPrimary());
        input.setHintTextColor(consoleMutedText());
        input.setBackground(roundedBg(searchInputBg(), 99, searchInputStroke()));
        input.setPadding(dp(16), 0, dp(16), 0);
        input.setMinHeight(dp(44));
        input.setSingleLine(true);
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

    private static class CpuChartView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int[] values;
        private final int lineColor;
        private final int fillColor;
        private final int gridColor;

        CpuChartView(Context context, int[] values, int lineColor, int fillColor, int gridColor) {
            super(context);
            this.values = values == null ? new int[0] : values;
            this.lineColor = lineColor;
            this.fillColor = fillColor;
            this.gridColor = gridColor;
            setWillNotDraw(false);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            if (w <= 0 || h <= 0) {
                return;
            }
            float padX = Math.max(8f, w * 0.025f);
            float padY = Math.max(6f, h * 0.14f);
            float left = padX;
            float right = w - padX;
            float top = padY;
            float bottom = h - padY;

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(fillColor);
            paint.setAlpha(80);
            canvas.drawRoundRect(left, top, right, bottom, 10f, 10f, paint);
            paint.setAlpha(255);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(1f, h * 0.025f));
            paint.setColor(gridColor);
            paint.setAlpha(95);
            canvas.drawLine(left, top + (bottom - top) * 0.5f, right, top + (bottom - top) * 0.5f, paint);
            paint.setAlpha(255);

            if (values.length == 0) {
                return;
            }
            paint.setColor(lineColor);
            paint.setStrokeWidth(Math.max(2.4f, h * 0.065f));
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            if (values.length == 1) {
                float y = valueY(values[0], top, bottom);
                canvas.drawLine(left, y, right, y, paint);
                canvas.drawCircle(right, y, Math.max(3f, h * 0.075f), paint);
                return;
            }
            float span = right - left;
            float prevX = left;
            float prevY = valueY(values[0], top, bottom);
            for (int i = 1; i < values.length; i++) {
                float x = left + span * i / (values.length - 1);
                float y = valueY(values[i], top, bottom);
                canvas.drawLine(prevX, prevY, x, y, paint);
                prevX = x;
                prevY = y;
            }
            canvas.drawCircle(prevX, prevY, Math.max(3f, h * 0.075f), paint);
        }

        private float valueY(int raw, float top, float bottom) {
            int value = Math.max(0, Math.min(100, raw));
            return bottom - (bottom - top) * value / 100f;
        }
    }

    private static class PairInfoResult {
        final String server;
        final JSONObject info;

        PairInfoResult(String server, JSONObject info) {
            this.server = server;
            this.info = info;
        }
    }

}
