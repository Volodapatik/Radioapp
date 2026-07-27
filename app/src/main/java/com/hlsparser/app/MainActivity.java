package com.hlsparser.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import androidx.coordinatorlayout.widget.CoordinatorLayout;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MainActivity - HLS Parser with F12 DevTools-like network interception.
 * WebView is added dynamically below topBar for proper scrolling.
 * Results shown in a draggable BottomSheet.
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "HLSParser";

    // Top bar
    private EditText inputUrl;
    private MaterialButton btnAnalyze;
    private MaterialButton btnMonitor;
    private LinearLayout monitorBar;

    // Status
    private TextView tvStatus;
    private TextView tvFloatingStatus;
    private TextView tvMonitorStatus;
    private TextView tvRequestCount;
    private TextView tvM3u8Count;
    private ImageView statusDot;
    private MaterialCardView cardFloatingStatus;

    // WebView
    private WebView webView;
    private TextView tvWebViewPlaceholder;
    private ProgressBar progressWebView;

    // Bottom sheet
    private BottomSheetBehavior<FrameLayout> bottomSheetBehavior;
    private FrameLayout bottomSheetRoot;
    private TextView tvResultsTitle;
    private RecyclerView recyclerResults;
    private ResultAdapter resultAdapter;
    private MaterialButton btnGenerateM3U8;
    private TextView tvM3U8Title;
    private TextView tvM3U8Output;
    private MaterialButton btnCopyM3U8;
    private MaterialButton btnSaveM3U8;
    private LinearLayout m3u8Buttons;

    // Progress overlay
    private ProgressBar progressBar;

    // Core components
    private HLSAnalyzer hlsAnalyzer;
    private M3U8Generator m3u8Generator;

    // State
    private String currentUrl;
    private String pageHtmlContent = "";
    private boolean isPageLoaded = false;
    private boolean isMonitoring = false;
    private List<HLSAnalyzer.HLSStream> foundStreams = new ArrayList<>();
    private Set<String> interceptedUrls = new HashSet<>();
    private int totalRequests = 0;

    // Handler
    private Handler handler = new Handler(Looper.getMainLooper());

    // Preferences
    private SharedPreferences prefs;

    // Container for WebView
    private FrameLayout webViewContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Keep screen on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        initViews();
        createWebViewContainer();
        initComponents();
        setupBottomSheet();
        setupWebView();
        setupListeners();
        loadPreferences();
        requestPermissions();
    }

    /**
     * Create WebView container dynamically below topBar.
     * This ensures WebView occupies full remaining screen space
     * and scrolling works properly inside WebView.
     */
    private void createWebViewContainer() {
        CoordinatorLayout coordinator = findViewById(R.id.coordinatorLayout);
        if (coordinator == null) {
            coordinator = findViewById(android.R.id.content);
            // If not found, create the container programmatically
            ViewGroup root = (ViewGroup) findViewById(android.R.id.content);
            View topBar = findViewById(R.id.topBar);
            FrameLayout container = new FrameLayout(this);
            container.setLayoutParams(new CoordinatorLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));

            // We need to add WebView and bottomSheet under topBar
            // Remove bottomSheet from its current position and re-add
            View bottomSheet = findViewById(R.id.bottomSheetRoot);
            if (bottomSheet != null && bottomSheet.getParent() != null) {
                ((ViewGroup) bottomSheet.getParent()).removeView(bottomSheet);
            }

            // Add WebView placeholder
            tvWebViewPlaceholder = new TextView(this);
            tvWebViewPlaceholder.setText("Вставте URL і натисніть Аналізувати");
            tvWebViewPlaceholder.setTextSize(16);
            tvWebViewPlaceholder.setGravity(android.view.Gravity.CENTER);
            tvWebViewPlaceholder.setPadding(64, 64, 64, 64);
            tvWebViewPlaceholder.setTextColor(0xFF999999);
            tvWebViewPlaceholder.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            container.addView(tvWebViewPlaceholder);

            // Progress
            progressWebView = new ProgressBar(this);
            progressWebView.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    android.view.Gravity.CENTER));
            progressWebView.setVisibility(View.GONE);
            container.addView(progressWebView);

            // Add container to root AFTER topBar
            root.addView(container, 1);
            webViewContainer = container;

            // Re-add bottomSheet
            if (bottomSheet != null) {
                root.addView(bottomSheet);
            }
        } else {
            // Use existing layout structure
            webViewContainer = new FrameLayout(this);
            webViewContainer.setLayoutParams(new CoordinatorLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            // CoordinatorLayout children are added in order; topBar is first
            coordinator.addView(webViewContainer, 1);
        }
    }

    private void initViews() {
        // Top bar
        inputUrl = findViewById(R.id.inputUrl);
        btnAnalyze = findViewById(R.id.btnAnalyze);
        btnMonitor = findViewById(R.id.btnMonitor);
        monitorBar = findViewById(R.id.monitorBar);
        tvRequestCount = findViewById(R.id.tvRequestCount);
        tvM3u8Count = findViewById(R.id.tvM3u8Count);
        tvMonitorStatus = findViewById(R.id.tvMonitorStatus);
        statusDot = findViewById(R.id.statusDot);

        // Status
        tvStatus = findViewById(R.id.tvStatus);
        tvFloatingStatus = findViewById(R.id.tvFloatingStatus);
        cardFloatingStatus = findViewById(R.id.cardFloatingStatus);

        // Bottom sheet
        bottomSheetRoot = findViewById(R.id.bottomSheetRoot);
        tvResultsTitle = findViewById(R.id.tvResultsTitle);
        recyclerResults = findViewById(R.id.recyclerResults);
        btnGenerateM3U8 = findViewById(R.id.btnGenerateM3U8);
        tvM3U8Title = findViewById(R.id.tvM3U8Title);
        tvM3U8Output = findViewById(R.id.tvM3U8Output);
        btnCopyM3U8 = findViewById(R.id.btnCopyM3U8);
        btnSaveM3U8 = findViewById(R.id.btnSaveM3U8);
        m3u8Buttons = findViewById(R.id.m3u8Buttons);

        // Progress overlay
        progressBar = findViewById(R.id.progressBar);
    }

    private void initComponents() {
        hlsAnalyzer = new HLSAnalyzer();
        m3u8Generator = new M3U8Generator();

        resultAdapter = new ResultAdapter(this);
        resultAdapter.setListener(new ResultAdapter.OnStreamActionListener() {
            @Override
            public void onCopy(HLSAnalyzer.HLSStream stream) {
                copyToClipboard(stream.getUrl());
            }

            @Override
            public void onSave(HLSAnalyzer.HLSStream stream) {
                saveStreamToFile(stream);
            }

            @Override
            public void onPlay(HLSAnalyzer.HLSStream stream) {
                openStreamInPlayer(stream.getUrl());
            }
        });

        recyclerResults.setLayoutManager(new LinearLayoutManager(this));
        recyclerResults.setAdapter(resultAdapter);
    }

    private void setupBottomSheet() {
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheetRoot);
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
        bottomSheetBehavior.setPeekHeight(0);
        bottomSheetBehavior.setHideable(true);

        // IMPORTANT: prevent BottomSheet from closing on touch/click inside
        // Only close when user drags it down
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);

        // BottomSheet state listener - prevent auto-close
        bottomSheetBehavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
                // If user starts dragging down, let it collapse but don't hide completely
                if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                    // When hiding, show peek so user can pull it back up
                    handler.postDelayed(() -> {
                        if (bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_HIDDEN && foundStreams.size() > 0) {
                            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                        }
                    }, 500);
                }
            }

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {
                // Do nothing - just track
            }
        });

        // Drag handle click to toggle expanded/collapsed
        View handle = findViewById(R.id.bottomSheetHandle);
        if (handle != null) {
            handle.setOnClickListener(v -> {
                if (bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_COLLAPSED ||
                    bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_HIDDEN) {
                    bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                } else if (bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED) {
                    bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                }
            });
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        webView = new WebView(this);
        webView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        // Add WebView to container
        if (webViewContainer != null) {
            webViewContainer.addView(webView);
        }

        // Enable JavaScript
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setDatabaseEnabled(true);
        webView.getSettings().setAllowFileAccess(true);
        webView.getSettings().setAllowContentAccess(true);
        webView.getSettings().setMediaPlaybackRequiresUserGesture(false);
        webView.getSettings().setSupportMultipleWindows(true);
        webView.getSettings().setBuiltInZoomControls(true);
        webView.getSettings().setDisplayZoomControls(false);

        // DO NOT use loadWithOverviewMode and useWideViewPort — they break internal scrolling
        webView.getSettings().setLoadWithOverviewMode(false);
        webView.getSettings().setUseWideViewPort(false);

        // Enable cookies
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        // Set User-Agent
        String userAgent = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36";
        webView.getSettings().setUserAgentString(userAgent);

        // Enable mixed content
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            webView.getSettings().setMixedContentMode(0);
        }

        // JavaScript interface
        webView.addJavascriptInterface(new WebAppInterface(), "Android");

        // WebViewClient - F12 DevTools-like interception
        webView.setWebViewClient(new WebViewClient() {

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                totalRequests++;

                if (isMonitoring) {
                    runOnUiThread(() -> tvRequestCount.setText(String.valueOf(totalRequests)));

                    if (url.toLowerCase().contains(".m3u8")) {
                        Log.d(TAG, "INTERCEPTED .m3u8: " + url);
                        captureStream(url, "intercepted");
                    }
                    if (url.toLowerCase().contains("manifest") &&
                        (url.toLowerCase().contains("mpd") || url.toLowerCase().contains("m3u8"))) {
                        Log.d(TAG, "INTERCEPTED manifest: " + url);
                        captureStream(url, "intercepted");
                    }
                }

                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                currentUrl = url;
                view.loadUrl(url);
                return true;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                currentUrl = url;
                runOnUiThread(() -> progressWebView.setVisibility(View.VISIBLE));
                if (isMonitoring) {
                    totalRequests = 0;
                    interceptedUrls.clear();
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                runOnUiThread(() -> {
                    progressWebView.setVisibility(View.GONE);
                    if (tvWebViewPlaceholder != null) tvWebViewPlaceholder.setVisibility(View.GONE);
                    isPageLoaded = true;
                });

                // Show monitoring button
                if (!isMonitoring && isPageLoaded) {
                    runOnUiThread(() -> monitorBar.setVisibility(View.VISIBLE));
                }

                // Extract HTML for static analysis
                view.evaluateJavascript(getHtmlExtractionScript(), new ValueCallback<String>() {
                    @Override
                    public void onReceiveValue(String html) {
                        if (html != null && !html.equals("null")) {
                            pageHtmlContent = html.replace("\\\"", "\"")
                                    .replace("\\\\", "\\")
                                    .trim();
                        }
                    }
                });

                // Run JS detection if monitoring
                if (isMonitoring) {
                    handler.postDelayed(() -> injectDetectionScript(), 1000);
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError errorResponse) {
                super.onReceivedError(view, request, errorResponse);
                Log.e(TAG, "WebView error: " + errorResponse.getDescription());
            }
        });

        // WebChromeClient - console monitoring
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                super.onProgressChanged(view, newProgress);
                if (newProgress < 100) {
                    progressWebView.setVisibility(View.VISIBLE);
                } else {
                    progressWebView.setVisibility(View.GONE);
                }
            }

            @Override
            public boolean onConsoleMessage(android.webkit.ConsoleMessage consoleMessage) {
                String msg = consoleMessage.message();
                if (msg != null && msg.contains(".m3u8")) {
                    Pattern p = Pattern.compile("https?://[^\\s\"']+\\.m3u8[^\\s\"']*");
                    Matcher m = p.matcher(msg);
                    while (m.find()) {
                        String url = m.group();
                        Log.d(TAG, "Console .m3u8: " + url);
                        captureStream(url, "console");
                    }
                }
                return true;
            }
        });
    }

    private void setupListeners() {
        btnAnalyze.setOnClickListener(v -> analyzePage());
        btnMonitor.setOnClickListener(v -> toggleMonitoring());
        btnGenerateM3U8.setOnClickListener(v -> generateCombinedM3U8());
        btnCopyM3U8.setOnClickListener(v -> {
            if (tvM3U8Output.getText().toString().length() > 0) {
                copyToClipboard(tvM3U8Output.getText().toString());
            }
        });
        btnSaveM3U8.setOnClickListener(v -> saveGeneratedM3U8());

        inputUrl.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_GO) {
                analyzePage();
                return true;
            }
            return false;
        });
    }

    private void loadPreferences() {
        prefs = getSharedPreferences("HLSParserPrefs", Context.MODE_PRIVATE);
        String lastUrl = prefs.getString("lastUrl", "");
        if (!lastUrl.isEmpty()) {
            inputUrl.setText(lastUrl);
        }
    }

    private void requestPermissions() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 100);
            }
        }
    }

    // ===== MONITORING =====

    private void toggleMonitoring() {
        if (isMonitoring) {
            stopMonitoring();
        } else {
            startMonitoring();
        }
    }

    private void startMonitoring() {
        isMonitoring = true;
        interceptedUrls.clear();
        totalRequests = 0;

        // Show monitoring UI
        monitorBar.setVisibility(View.VISIBLE);
        tvRequestCount.setText("0");
        tvM3u8Count.setText("0");
        btnMonitor.setText(R.string.btn_stop_monitoring);
        tvMonitorStatus.setVisibility(View.VISIBLE);
        tvMonitorStatus.setText(R.string.status_monitoring);
        statusDot.setVisibility(View.VISIBLE);
        statusDot.setImageResource(R.drawable.status_active);

        // Floating status
        cardFloatingStatus.setVisibility(View.VISIBLE);
        tvFloatingStatus.setText(R.string.monitoring_active);

        // Inject JS detection immediately
        if (isPageLoaded) {
            handler.postDelayed(() -> injectDetectionScript(), 500);
        }

        Log.d(TAG, "Monitoring started");
    }

    private void stopMonitoring() {
        isMonitoring = false;

        btnMonitor.setText(R.string.btn_start_monitoring);
        tvMonitorStatus.setText(R.string.monitoring_stopped);
        statusDot.setImageResource(R.drawable.status_stopped);
        cardFloatingStatus.setVisibility(View.GONE);

        // Update results
        updateResultsUI();

        Log.d(TAG, "Monitoring stopped. Found: " + foundStreams.size() + " streams");
    }

    /**
     * Capture a .m3u8 URL from network interception.
     */
    private void captureStream(String url, String source) {
        if (url == null || url.isEmpty()) return;

        String cleanUrl = url.replace("\\u002F", "/")
                .replace("\\/", "/")
                .replace("\\\"", "\"")
                .replace("\"", "")
                .trim();

        if (!cleanUrl.contains(".m3u8")) return;

        synchronized (interceptedUrls) {
            if (interceptedUrls.contains(cleanUrl)) return;
            interceptedUrls.add(cleanUrl);
        }

        HLSAnalyzer.HLSStream stream = new HLSAnalyzer.HLSStream(cleanUrl);
        stream.setSource(source);
        foundStreams.add(stream);

        runOnUiThread(() -> {
            tvM3u8Count.setText(String.valueOf(interceptedUrls.size()));

            // Floating notification
            cardFloatingStatus.setVisibility(View.VISIBLE);
            tvFloatingStatus.setText("HLS: " + source);
            handler.postDelayed(() -> {
                if (!isMonitoring) {
                    cardFloatingStatus.setVisibility(View.GONE);
                }
            }, 3000);

            // Update RecyclerView
            resultAdapter.setStreams(new ArrayList<>(foundStreams));
            tvResultsTitle.setVisibility(View.VISIBLE);
            tvResultsTitle.setText(getString(R.string.intercepted_streams) + " (" + foundStreams.size() + ")");

            // Show bottom sheet - expand to show results
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);

            if (foundStreams.size() > 1) {
                btnGenerateM3U8.setVisibility(View.VISIBLE);
            }

            autoGenerateM3U8();
        });
    }

    private void injectDetectionScript() {
        if (webView == null) return;
        webView.evaluateJavascript(getHLSDetectionScript(), new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String result) {
                if (result != null && !result.equals("null") && result.length() > 2) {
                    try {
                        String cleaned = result.replace("\"", "").replace("\\", "");
                        String[] urls = cleaned.split(",");
                        for (String url : urls) {
                            url = url.trim();
                            if (url.contains(".m3u8") && url.startsWith("http")) {
                                captureStream(url, "javascript");
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing JS result: " + e.getMessage());
                    }
                }
            }
        });
    }

    private void analyzePage() {
        String url = inputUrl.getText().toString().trim();

        if (url.isEmpty()) {
            Toast.makeText(this, getString(R.string.invalid_url), Toast.LENGTH_SHORT).show();
            return;
        }

        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }

        if (!url.contains(".") || url.contains(" ")) {
            Toast.makeText(this, getString(R.string.invalid_url), Toast.LENGTH_SHORT).show();
            return;
        }

        currentUrl = url;
        startAnalysis();
    }

    private void startAnalysis() {
        prefs.edit().putString("lastUrl", currentUrl).apply();

        foundStreams.clear();
        resultAdapter.setStreams(new ArrayList<>());
        pageHtmlContent = "";
        isPageLoaded = false;
        interceptedUrls.clear();
        totalRequests = 0;

        // Reset UI
        progressBar.setVisibility(View.GONE);
        tvStatus.setVisibility(View.GONE);
        tvResultsTitle.setVisibility(View.GONE);
        btnGenerateM3U8.setVisibility(View.GONE);
        monitorBar.setVisibility(View.GONE);
        tvM3U8Title.setVisibility(View.GONE);
        findViewById(R.id.m3u8OutputContainer).setVisibility(View.GONE);
        btnCopyM3U8.setVisibility(View.GONE);
        btnSaveM3U8.setVisibility(View.GONE);
        m3u8Buttons.setVisibility(View.GONE);
        cardFloatingStatus.setVisibility(View.GONE);
        tvMonitorStatus.setVisibility(View.GONE);
        statusDot.setVisibility(View.GONE);
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);

        // Show WebView placeholder
        if (tvWebViewPlaceholder != null) tvWebViewPlaceholder.setVisibility(View.VISIBLE);
        progressWebView.setVisibility(View.VISIBLE);

        // Load URL in WebView
        webView.setVisibility(View.VISIBLE);
        webView.loadUrl(currentUrl);

        // Auto-start monitoring after page loads
        handler.postDelayed(() -> {
            if (isPageLoaded) {
                startMonitoring();
                performStaticAnalysis();
            }
        }, 3000);
    }

    private void performStaticAnalysis() {
        if (pageHtmlContent.isEmpty()) {
            webView.evaluateJavascript(getHtmlExtractionScript(), new ValueCallback<String>() {
                @Override
                public void onReceiveValue(String html) {
                    if (html != null && !html.equals("null")) {
                        pageHtmlContent = html.replace("\\\"", "\"")
                                .replace("\\\\", "\\")
                                .trim();
                        doStaticAnalysis();
                    }
                }
            });
            return;
        }
        doStaticAnalysis();
    }

    private void doStaticAnalysis() {
        Log.d(TAG, "Static analysis, HTML length: " + pageHtmlContent.length());

        List<HLSAnalyzer.HLSStream> staticResults = hlsAnalyzer.analyze(pageHtmlContent, currentUrl);
        for (HLSAnalyzer.HLSStream stream : staticResults) {
            captureStream(stream.getUrl(), "html");
        }

        injectDetectionScript();
    }

    private void updateResultsUI() {
        if (foundStreams.isEmpty()) {
            tvResultsTitle.setVisibility(View.VISIBLE);
            tvResultsTitle.setText(getString(R.string.no_results));
        } else {
            tvResultsTitle.setVisibility(View.VISIBLE);
            tvResultsTitle.setText(getString(R.string.intercepted_streams) + " (" + foundStreams.size() + ")");
            resultAdapter.setStreams(foundStreams);
            btnGenerateM3U8.setVisibility(View.VISIBLE);
            m3u8Buttons.setVisibility(View.VISIBLE);
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            autoGenerateM3U8();
        }
    }

    private void autoGenerateM3U8() {
        if (foundStreams.isEmpty()) return;

        HLSAnalyzer.HLSStream videoStream = null;
        HLSAnalyzer.HLSStream audioStream = null;

        for (HLSAnalyzer.HLSStream stream : foundStreams) {
            if (stream.getType().equals("video") && videoStream == null) {
                videoStream = stream;
            } else if (stream.getType().equals("audio") && audioStream == null) {
                audioStream = stream;
            }
        }

        String m3u8Content;
        if (videoStream != null && audioStream != null) {
            m3u8Content = m3u8Generator.generateCombinedPlaylist(videoStream.getUrl(), audioStream.getUrl());
        } else {
            m3u8Content = m3u8Generator.generateMasterPlaylist(foundStreams);
        }

        tvM3U8Title.setVisibility(View.VISIBLE);
        findViewById(R.id.m3u8OutputContainer).setVisibility(View.VISIBLE);
        tvM3U8Output.setText(m3u8Content);
        btnCopyM3U8.setVisibility(View.VISIBLE);
        btnSaveM3U8.setVisibility(View.VISIBLE);
        m3u8Buttons.setVisibility(View.VISIBLE);
    }

    private void generateCombinedM3U8() {
        autoGenerateM3U8();
    }

    private void saveStreamToFile(HLSAnalyzer.HLSStream stream) {
        String fileName = m3u8Generator.buildFileNameFromUrl(stream.getUrl());
        String content = "#EXTM3U\n#EXT-X-VERSION:3\n" + stream.getUrl() + "\n";

        File file = m3u8Generator.saveToFile(content, fileName, this);
        if (file != null) {
            Toast.makeText(this, getString(R.string.saved) + " → " + file.getAbsolutePath(),
                    Toast.LENGTH_LONG).show();
        } else {
            File internalFile = m3u8Generator.saveToInternalStorage(content, fileName, this);
            if (internalFile != null) {
                Toast.makeText(this, getString(R.string.saved) + " (внутрішнє сховище)",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private void saveGeneratedM3U8() {
        String content = tvM3U8Output.getText().toString();
        if (content.isEmpty()) return;

        File file = m3u8Generator.saveToFile(content, "combined_playlist", this);
        if (file != null) {
            Toast.makeText(this, getString(R.string.saved) + " → " + file.getAbsolutePath(),
                    Toast.LENGTH_LONG).show();
        } else {
            File internalFile = m3u8Generator.saveToInternalStorage(content, "combined_playlist", this);
            if (internalFile != null) {
                Toast.makeText(this, getString(R.string.saved) + " (внутрішнє сховище)",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private void openStreamInPlayer(String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(Uri.parse(url), "application/x-mpegURL");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try {
            startActivity(intent);
        } catch (Exception e) {
            intent.setPackage("org.videolan.vlc");
            try {
                startActivity(intent);
            } catch (Exception e2) {
                intent.setDataAndType(Uri.parse(url), "video/*");
                intent.setPackage(null);
                try {
                    startActivity(intent);
                } catch (Exception e3) {
                    Toast.makeText(this, "Встановіть HLS-сумісний плеєр (напр. VLC)", Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    private void copyToClipboard(String text) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("HLS Stream", text);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(this, getString(R.string.copied), Toast.LENGTH_SHORT).show();
    }

    // ===== JavaScript Bridge =====

    private class WebAppInterface {
        @JavascriptInterface
        public void onHLSFound(String url) {
            Log.d(TAG, "JS found HLS: " + url);
            runOnUiThread(() -> captureStream(url, "javascript_interface"));
        }

        @JavascriptInterface
        public void onPageLoaded() {
            runOnUiThread(() -> {
                isPageLoaded = true;
                if (isMonitoring) {
                    injectDetectionScript();
                }
            });
        }
    }

    // ===== JavaScript Detection Scripts =====

    private String getHtmlExtractionScript() {
        return "(function() { return document.documentElement.outerHTML; })();";
    }

    private String getHLSDetectionScript() {
        return "(function() {" +
                "var found = [];" +
                "var seen = {};" +

                // Scan all element attributes
                "var allElements = document.querySelectorAll('*');" +
                "for (var i = 0; i < allElements.length; i++) {" +
                "  var el = allElements[i];" +
                "  var attrs = el.attributes;" +
                "  if (attrs) {" +
                "    for (var j = 0; j < attrs.length; j++) {" +
                "      var val = attrs[j].value;" +
                "      if (typeof val === 'string' && val.indexOf('.m3u8') > -1) {" +
                "        var matches = val.match(/https?:\\/\\/[^\"'\\\\\\s]+\\.m3u8[^\"'\\\\\\s]*/gi);" +
                "        if (matches) {" +
                "          for (var k = 0; k < matches.length; k++) {" +
                "            if (!seen[matches[k]]) {" +
                "              seen[matches[k]] = true;" +
                "              found.push(matches[k]);" +
                "              console.log('HLS_FOUND:' + matches[k]);" +
                "            }" +
                "          }" +
                "        }" +
                "      }" +
                "    }" +
                "  }" +
                "}" +

                // Scan all script tags
                "var scripts = document.querySelectorAll('script');" +
                "for (var i = 0; i < scripts.length; i++) {" +
                "  var text = scripts[i].textContent || '';" +
                "  var matches = text.match(/https?:\\/\\/[^\"'\\\\\\s]+\\.m3u8[^\"'\\\\\\s]*/gi);" +
                "  if (matches) {" +
                "    for (var j = 0; j < matches.length; j++) {" +
                "      if (!seen[matches[j]]) {" +
                "        seen[matches[j]] = true;" +
                "        found.push(matches[j]);" +
                "        console.log('HLS_FOUND:' + matches[j]);" +
                "      }" +
                "    }" +
                "  }" +
                "}" +

                // Hls.js
                "try {" +
                "  var videos = document.querySelectorAll('video');" +
                "  for (var i = 0; i < videos.length; i++) {" +
                "    var v = videos[i];" +
                "    if (v.hls && v.hls.url && !seen[v.hls.url]) {" +
                "      seen[v.hls.url] = true;" +
                "      found.push(v.hls.url);" +
                "      console.log('HLS_FOUND:' + v.hls.url);" +
                "    }" +
                "    if (v.currentSrc && v.currentSrc.indexOf('.m3u8') > -1 && !seen[v.currentSrc]) {" +
                "      seen[v.currentSrc] = true;" +
                "      found.push(v.currentSrc);" +
                "      console.log('HLS_FOUND:' + v.currentSrc);" +
                "    }" +
                "  }" +
                "} catch(e) {}" +

                // Performance API
                "try {" +
                "  var entries = performance.getEntriesByType('resource');" +
                "  for (var i = 0; i < entries.length; i++) {" +
                "    var name = entries[i].name;" +
                "    if (name && name.indexOf('.m3u8') > -1 && !seen[name]) {" +
                "      seen[name] = true;" +
                "      found.push(name);" +
                "      console.log('HLS_FOUND:' + name);" +
                "    }" +
                "  }" +
                "} catch(e) {}" +

                // Monkey-patch XHR
                "try {" +
                "  if (window.XMLHttpRequest) {" +
                "    var origOpen = XMLHttpRequest.prototype.open;" +
                "    XMLHttpRequest.prototype.open = function(method, url) {" +
                "      if (url && url.indexOf('.m3u8') > -1) {" +
                "        console.log('HLS_FOUND:' + url);" +
                "      }" +
                "      return origOpen.apply(this, arguments);" +
                "    };" +
                "  }" +
                "} catch(e) {}" +

                // Monkey-patch fetch
                "try {" +
                "  if (window.fetch) {" +
                "    var origFetch = window.fetch;" +
                "    window.fetch = function(url) {" +
                "      var urlStr = typeof url === 'string' ? url : (url.url || url.toString());" +
                "      if (urlStr && urlStr.indexOf('.m3u8') > -1) {" +
                "        console.log('HLS_FOUND:' + urlStr);" +
                "      }" +
                "      return origFetch.apply(this, arguments);" +
                "    };" +
                "  }" +
                "} catch(e) {}" +

                "return found.join(',');" +
                "})();";
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) webView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (webView != null) webView.onPause();
        if (isMonitoring) stopMonitoring();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (webView != null) webView.destroy();
        handler.removeCallbacksAndMessages(null);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Дозвіл на збереження файлів надано", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED) {
            // Just collapse, don't hide - user can pull it back up
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        } else if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
