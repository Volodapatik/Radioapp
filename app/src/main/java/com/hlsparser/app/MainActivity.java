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
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MainActivity - HLS Parser with F12 DevTools-like network interception.
 * Intercepts all network requests through shouldInterceptRequest to detect
 * .m3u8 streams that load dynamically (e.g. when user clicks Play).
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "HLSParser";

    // UI Components
    private EditText inputUrl;
    private MaterialButton btnAnalyze;
    private MaterialButton btnMonitor;
    private MaterialButton btnGenerateM3U8;
    private MaterialButton btnCopyM3U8;
    private MaterialButton btnSaveM3U8;
    private ProgressBar progressBar;
    private TextView tvStatus;
    private TextView tvResultsTitle;
    private TextView tvWebViewPlaceholder;
    private TextView tvM3U8Title;
    private TextView tvM3U8Output;
    private ProgressBar progressWebView;
    private TextView tvRequestCount;
    private TextView tvM3u8Count;
    private MaterialCardView cardNetworkStats;
    private TextView tvMonitorStatus;
    private ImageView statusDot;

    private WebView webView;
    private RecyclerView recyclerResults;
    private ResultAdapter resultAdapter;

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

    // Handler for delayed analysis
    private Handler handler = new Handler(Looper.getMainLooper());

    // Preferences
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        initComponents();
        setupWebView();
        setupListeners();
        loadPreferences();
        requestPermissions();
    }

    private void initViews() {
        inputUrl = findViewById(R.id.inputUrl);
        btnAnalyze = findViewById(R.id.btnAnalyze);
        btnMonitor = findViewById(R.id.btnMonitor);
        btnGenerateM3U8 = findViewById(R.id.btnGenerateM3U8);
        btnCopyM3U8 = findViewById(R.id.btnCopyM3U8);
        btnSaveM3U8 = findViewById(R.id.btnSaveM3U8);
        progressBar = findViewById(R.id.progressBar);
        tvStatus = findViewById(R.id.tvStatus);
        tvResultsTitle = findViewById(R.id.tvResultsTitle);
        tvWebViewPlaceholder = findViewById(R.id.tvWebViewPlaceholder);
        tvM3U8Title = findViewById(R.id.tvM3U8Title);
        tvM3U8Output = findViewById(R.id.tvM3U8Output);
        progressWebView = findViewById(R.id.progressWebView);
        webView = findViewById(R.id.webView);
        recyclerResults = findViewById(R.id.recyclerResults);
        tvRequestCount = findViewById(R.id.tvRequestCount);
        tvM3u8Count = findViewById(R.id.tvM3u8Count);
        cardNetworkStats = findViewById(R.id.cardNetworkStats);
        tvMonitorStatus = findViewById(R.id.tvMonitorStatus);
        statusDot = findViewById(R.id.statusDot);
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

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setDatabaseEnabled(true);
        webView.getSettings().setAllowFileAccess(true);
        webView.getSettings().setAllowContentAccess(true);
        webView.getSettings().setLoadWithOverviewMode(true);
        webView.getSettings().setUseWideViewPort(true);
        webView.getSettings().setMediaPlaybackRequiresUserGesture(false);

        // Enable cookies
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        // Set User-Agent (Android Chrome)
        String userAgent = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36";
        webView.getSettings().setUserAgentString(userAgent);

        // Enable mixed content
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            webView.getSettings().setMixedContentMode(0); // MIXED_CONTENT_ALWAYS_ALLOW
        }

        // Add JavaScript interface
        webView.addJavascriptInterface(new WebAppInterface(), "Android");

        // WebViewClient - intercept requests and handle navigation
        webView.setWebViewClient(new WebViewClient() {

            /**
             * KEY METHOD: Intercept all network requests like F12 DevTools.
             * Every time the WebView requests a resource (JS, CSS, images, XHR, fetch),
             * this method is called BEFORE the request goes to the network.
             * We check if the URL contains .m3u8 and capture it.
             */
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                totalRequests++;

                if (isMonitoring) {
                    runOnUiThread(() -> {
                        tvRequestCount.setText(String.valueOf(totalRequests));
                    });

                    // Check for .m3u8 in URL
                    if (url.toLowerCase().contains(".m3u8")) {
                        Log.d(TAG, "INTERCEPTED .m3u8: " + url);
                        captureStream(url, "intercepted");
                    }

                    // Also check for common HLS patterns in URL path
                    if (url.toLowerCase().contains("manifest") && 
                        (url.toLowerCase().contains("mpd") || url.toLowerCase().contains("m3u8"))) {
                        Log.d(TAG, "INTERCEPTED manifest: " + url);
                        captureStream(url, "intercepted");
                    }
                }

                // Let the request proceed normally - we just observe
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
                progressWebView.setVisibility(View.VISIBLE);
                if (isMonitoring) {
                    totalRequests = 0;
                    interceptedUrls.clear();
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                progressWebView.setVisibility(View.GONE);
                tvWebViewPlaceholder.setVisibility(View.GONE);
                isPageLoaded = true;

                // Show monitoring button when page loads
                if (!isMonitoring && isPageLoaded) {
                    runOnUiThread(() -> {
                        btnMonitor.setVisibility(View.VISIBLE);
                    });
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

                // If monitoring is active, run JS detection too
                if (isMonitoring) {
                    injectDetectionScript();
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError errorResponse) {
                super.onReceivedError(view, request, errorResponse);
                Log.e(TAG, "WebView error: " + errorResponse.getDescription());
            }
        });

        // WebChromeClient - progress and console
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
                Log.d(TAG, "Console: " + consoleMessage.message());
                // Check console for .m3u8 URLs logged by injected scripts
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
        // Analyze button - loads page and starts monitoring
        btnAnalyze.setOnClickListener(v -> analyzePage());

        // Monitor button - toggle monitoring
        btnMonitor.setOnClickListener(v -> toggleMonitoring());

        // Generate M3U8 button
        btnGenerateM3U8.setOnClickListener(v -> generateCombinedM3U8());

        // Copy M3U8 button
        btnCopyM3U8.setOnClickListener(v -> {
            if (tvM3U8Output.getText().toString().length() > 0) {
                copyToClipboard(tvM3U8Output.getText().toString());
            }
        });

        // Save M3U8 button
        btnSaveM3U8.setOnClickListener(v -> saveGeneratedM3U8());

        // Enter key on URL input
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

    /**
     * Toggle network monitoring on/off.
     */
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
        cardNetworkStats.setVisibility(View.VISIBLE);
        tvRequestCount.setText("0");
        tvM3u8Count.setText("0");
        btnMonitor.setText(R.string.btn_stop_monitoring);
        tvMonitorStatus.setVisibility(View.VISIBLE);
        tvMonitorStatus.setText(R.string.status_monitoring);
        statusDot.setVisibility(View.VISIBLE);
        statusDot.setImageResource(R.drawable.status_active);

        tvStatus.setVisibility(View.VISIBLE);
        tvStatus.setText(getString(R.string.monitoring_active));
        tvStatus.setTextColor(ContextCompat.getColor(this, R.color.success));

        Log.d(TAG, "Monitoring started");
    }

    private void stopMonitoring() {
        isMonitoring = false;

        btnMonitor.setText(R.string.btn_start_monitoring);
        tvMonitorStatus.setText(R.string.monitoring_stopped);
        statusDot.setImageResource(R.drawable.status_stopped);

        tvStatus.setText(getString(R.string.done) + " — " + foundStreams.size() + " потоків знайдено");
        tvStatus.setTextColor(ContextCompat.getColor(this, R.color.text_primary));

        // Update results UI
        updateResultsUI();

        Log.d(TAG, "Monitoring stopped. Found: " + foundStreams.size() + " streams");
    }

    /**
     * Capture a .m3u8 URL from network interception.
     * This is the core "F12 DevTools" functionality.
     */
    private void captureStream(String url, String source) {
        if (url == null || url.isEmpty()) return;

        // Clean URL
        String cleanUrl = url.replace("\\u002F", "/")
                .replace("\\/", "/")
                .replace("\\\"", "\"")
                .replace("\"", "")
                .trim();

        if (!cleanUrl.contains(".m3u8")) return;

        // Check if we already captured this URL
        synchronized (interceptedUrls) {
            if (interceptedUrls.contains(cleanUrl)) return;
            interceptedUrls.add(cleanUrl);
        }

        // Add to found streams
        HLSAnalyzer.HLSStream stream = new HLSAnalyzer.HLSStream(cleanUrl);
        stream.setSource(source);

        foundStreams.add(stream);

        // Update UI
        runOnUiThread(() -> {
            tvM3u8Count.setText(String.valueOf(interceptedUrls.size()));

            // Show immediate toast for discovery
            Toast.makeText(this, "HLS знайдено: " + source, Toast.LENGTH_SHORT).show();

            // Update RecyclerView live
            resultAdapter.addStreams(new ArrayList<>());
            resultAdapter.setStreams(new ArrayList<>(foundStreams));
            tvResultsTitle.setVisibility(View.VISIBLE);
            tvResultsTitle.setText(getString(R.string.intercepted_streams) + " (" + foundStreams.size() + ")");

            if (foundStreams.size() > 1) {
                btnGenerateM3U8.setVisibility(View.VISIBLE);
            }

            // Auto-generate M3U8 if we have streams
            if (!foundStreams.isEmpty()) {
                autoGenerateM3U8();
            }
        });
    }

    /**
     * Inject JavaScript to detect .m3u8 URLs in the page.
     * Runs periodically during monitoring.
     */
    private void injectDetectionScript() {
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

    /**
     * Main analysis method.
     */
    private void analyzePage() {
        String url = inputUrl.getText().toString().trim();

        if (url.isEmpty()) {
            Toast.makeText(this, getString(R.string.invalid_url), Toast.LENGTH_SHORT).show();
            return;
        }

        // Add protocol if missing
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }

        // Validate URL format
        if (!url.contains(".") || url.contains(" ")) {
            Toast.makeText(this, getString(R.string.invalid_url), Toast.LENGTH_SHORT).show();
            return;
        }

        currentUrl = url;
        startAnalysis();
    }

    private void startAnalysis() {
        // Save URL
        prefs.edit().putString("lastUrl", currentUrl).apply();

        // Reset state
        foundStreams.clear();
        resultAdapter.setStreams(new ArrayList<>());
        pageHtmlContent = "";
        isPageLoaded = false;
        interceptedUrls.clear();
        totalRequests = 0;

        // Show loading
        progressBar.setVisibility(View.VISIBLE);
        tvStatus.setVisibility(View.VISIBLE);
        tvStatus.setText(getString(R.string.analyzing));
        tvStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        tvResultsTitle.setVisibility(View.GONE);
        btnGenerateM3U8.setVisibility(View.GONE);
        btnMonitor.setVisibility(View.GONE);
        tvM3U8Title.setVisibility(View.GONE);
        tvM3U8Output.setVisibility(View.GONE);
        btnCopyM3U8.setVisibility(View.GONE);
        btnSaveM3U8.setVisibility(View.GONE);
        tvWebViewPlaceholder.setVisibility(View.VISIBLE);
        cardNetworkStats.setVisibility(View.GONE);
        tvMonitorStatus.setVisibility(View.GONE);
        statusDot.setVisibility(View.GONE);

        // Load URL in WebView
        webView.setVisibility(View.VISIBLE);
        webView.loadUrl(currentUrl);

        // After page loads, auto-start monitoring
        handler.postDelayed(() -> {
            if (isPageLoaded) {
                startMonitoring();
                // Also do static HTML analysis
                performStaticAnalysis();
            }
        }, 3000); // Wait 3 seconds for page to load
    }

    /**
     * Analyze HTML content statically for .m3u8 URLs.
     */
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

        // Also inject JS detection
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

    // ===== JavaScript Injection Scripts =====

    private String getHtmlExtractionScript() {
        return "(function() {" +
                "var html = document.documentElement.outerHTML;" +
                "return html;" +
                "})();";
    }

    /**
     * JavaScript to scan the page for .m3u8 URLs.
     * Checks: attributes, scripts, Hls.js, Video.js, Shaka Player, Performance API.
     */
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
                "              console.log('HLS FOUND (attr): ' + matches[k]);" +
                "            }" +
                "          }" +
                "        }" +
                "      }" +
                "    }" +
                "  }" +
                "}" +

                // Scan all script tags content
                "var scripts = document.querySelectorAll('script');" +
                "for (var i = 0; i < scripts.length; i++) {" +
                "  var text = scripts[i].textContent || '';" +
                "  var matches = text.match(/https?:\\/\\/[^\"'\\\\\\s]+\\.m3u8[^\"'\\\\\\s]*/gi);" +
                "  if (matches) {" +
                "    for (var j = 0; j < matches.length; j++) {" +
                "      if (!seen[matches[j]]) {" +
                "        seen[matches[j]] = true;" +
                "        found.push(matches[j]);" +
                "        console.log('HLS FOUND (script): ' + matches[j]);" +
                "      }" +
                "    }" +
                "  }" +
                "}" +

                // Check for Hls.js
                "try {" +
                "  var videos = document.querySelectorAll('video');" +
                "  for (var i = 0; i < videos.length; i++) {" +
                "    var v = videos[i];" +
                "    if (v.hls && v.hls.url) {" +
                "      if (!seen[v.hls.url]) {" +
                "        seen[v.hls.url] = true;" +
                "        found.push(v.hls.url);" +
                "        console.log('HLS FOUND (hls.js): ' + v.hls.url);" +
                "      }" +
                "    }" +
                "    if (v.currentSrc && v.currentSrc.indexOf('.m3u8') > -1 && !seen[v.currentSrc]) {" +
                "      seen[v.currentSrc] = true;" +
                "      found.push(v.currentSrc);" +
                "      console.log('HLS FOUND (video src): ' + v.currentSrc);" +
                "    }" +
                "  }" +
                "} catch(e) {}" +

                // Check performance API for resource entries
                "try {" +
                "  var entries = performance.getEntriesByType('resource');" +
                "  for (var i = 0; i < entries.length; i++) {" +
                "    var name = entries[i].name;" +
                "    if (name && name.indexOf('.m3u8') > -1 && !seen[name]) {" +
                "      seen[name] = true;" +
                "      found.push(name);" +
                "      console.log('HLS FOUND (perf): ' + name);" +
                "    }" +
                "  }" +
                "} catch(e) {}" +

                // Check for XHR/fetch .m3u8 in window
                "try {" +
                "  if (window.XMLHttpRequest) {" +
                "    var origOpen = XMLHttpRequest.prototype.open;" +
                "    XMLHttpRequest.prototype.open = function(method, url) {" +
                "      if (url && url.indexOf('.m3u8') > -1) {" +
                "        console.log('HLS FOUND (xhr): ' + url);" +
                "      }" +
                "      return origOpen.apply(this, arguments);" +
                "    };" +
                "  }" +
                "} catch(e) {}" +

                // Check for fetch
                "try {" +
                "  if (window.fetch) {" +
                "    var origFetch = window.fetch;" +
                "    window.fetch = function(url) {" +
                "      var urlStr = typeof url === 'string' ? url : (url.url || url.toString());" +
                "      if (urlStr && urlStr.indexOf('.m3u8') > -1) {" +
                "        console.log('HLS FOUND (fetch): ' + urlStr);" +
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
        if (webView != null) {
            webView.onResume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (webView != null) {
            webView.onPause();
        }
        if (isMonitoring) {
            stopMonitoring();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (webView != null) {
            webView.destroy();
        }
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
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
