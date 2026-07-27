package com.hlsparser.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
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
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.ImageButton;
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

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * MainActivity - main activity for HLS Parser application.
 * Handles URL input, WebView setup, page analysis, and results display.
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "HLSParser";
    private static final int PERMISSION_REQUEST_CODE = 100;

    // UI Components
    private EditText inputUrl;
    private MaterialButton btnAnalyze;
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
    private List<HLSAnalyzer.HLSStream> foundStreams = new ArrayList<>();

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

        // Set User-Agent
        String userAgent = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";
        webView.getSettings().setUserAgentString(userAgent);

        // Enable mixed content
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            webView.getSettings().setMixedContentMode(0); // MIXED_CONTENT_ALWAYS_ALLOW
        }

        // Add JavaScript interface for page interaction
        webView.addJavascriptInterface(new WebAppInterface(), "Android");

        // WebViewClient - handle page navigation
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                // Open all URLs inside the WebView
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
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                progressWebView.setVisibility(View.GONE);
                tvWebViewPlaceholder.setVisibility(View.GONE);
                isPageLoaded = true;

                // Extract page HTML after it's fully loaded
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
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
                super.onReceivedError(view, request, errorResponse);
                Log.e(TAG, "WebView error: " + errorResponse.getReasonPhrase());
            }
        });

        // WebChromeClient - for progress and dialogs
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
                return true;
            }
        });
    }

    private void setupListeners() {
        // Analyze button
        btnAnalyze.setOnClickListener(v -> analyzePage());

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
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        PERMISSION_REQUEST_CODE);
            }
        }
    }

    /**
     * Main analysis method - validates URL and starts the process.
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

    /**
     * Start the analysis process.
     */
    private void startAnalysis() {
        // Save URL
        prefs.edit().putString("lastUrl", currentUrl).apply();

        // Reset state
        foundStreams.clear();
        resultAdapter.setStreams(new ArrayList<>());
        pageHtmlContent = "";
        isPageLoaded = false;

        // Show loading
        progressBar.setVisibility(View.VISIBLE);
        tvStatus.setVisibility(View.VISIBLE);
        tvStatus.setText(getString(R.string.analyzing));
        tvResultsTitle.setVisibility(View.GONE);
        btnGenerateM3U8.setVisibility(View.GONE);
        tvM3U8Title.setVisibility(View.GONE);
        tvM3U8Output.setVisibility(View.GONE);
        btnCopyM3U8.setVisibility(View.GONE);
        btnSaveM3U8.setVisibility(View.GONE);
        tvWebViewPlaceholder.setVisibility(View.VISIBLE);
        webView.setVisibility(View.GONE);

        // Load URL in WebView
        webView.setVisibility(View.VISIBLE);
        webView.loadUrl(currentUrl);
    }

    /**
     * After page is loaded, analyze the content.
     */
    private void performAnalysis() {
        if (!isPageLoaded || pageHtmlContent.isEmpty()) {
            // Try to get content again
            webView.evaluateJavascript(getHtmlExtractionScript(), new ValueCallback<String>() {
                @Override
                public void onReceiveValue(String html) {
                    if (html != null && !html.equals("null")) {
                        pageHtmlContent = html.replace("\\\"", "\"")
                                .replace("\\\\", "\\")
                                .trim();
                        analyzeContent();
                    } else {
                        finishAnalysis();
                    }
                }
            });
            return;
        }

        analyzeContent();
    }

    private void analyzeContent() {
        Log.d(TAG, "Analyzing page content, length: " + pageHtmlContent.length());

        // Analyze with HLS analyzer
        foundStreams = hlsAnalyzer.analyze(pageHtmlContent, currentUrl);

        // Also try to get content from JavaScript context
        webView.evaluateJavascript(getHLSDetectionScript(), new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String result) {
                if (result != null && !result.equals("null") && result.length() > 2) {
                    try {
                        // Parse the JSON array from JS
                        String cleaned = result.replace("\"", "").replace("\\", "");
                        String[] urls = cleaned.split(",");
                        for (String url : urls) {
                            url = url.trim();
                            if (url.contains(".m3u8") && url.startsWith("http")) {
                                // Check if already found
                                boolean exists = false;
                                for (HLSAnalyzer.HLSStream existing : foundStreams) {
                                    if (existing.getUrl().equals(url)) {
                                        exists = true;
                                        break;
                                    }
                                }
                                if (!exists) {
                                    HLSAnalyzer.HLSStream stream = new HLSAnalyzer.HLSStream(url);
                                    stream.setSource("javascript");
                                    foundStreams.add(stream);
                                }
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing JS result: " + e.getMessage());
                    }
                }
                finishAnalysis();
            }
        });
    }

    private void finishAnalysis() {
        // Update UI
        progressBar.setVisibility(View.GONE);
        tvStatus.setVisibility(View.GONE);

        if (foundStreams.isEmpty()) {
            tvResultsTitle.setVisibility(View.VISIBLE);
            tvResultsTitle.setText(getString(R.string.no_results));
        } else {
            tvResultsTitle.setVisibility(View.VISIBLE);
            tvResultsTitle.setText(getString(R.string.title_results) + " (" + foundStreams.size() + ")");
            resultAdapter.setStreams(foundStreams);

            // Show generate button if multiple streams found
            if (foundStreams.size() > 1) {
                btnGenerateM3U8.setVisibility(View.VISIBLE);
            }
        }

        tvStatus.setVisibility(View.VISIBLE);
        tvStatus.setText(getString(R.string.done) + " - " + foundStreams.size() + " потоків знайдено");

        // Save analysis
        saveToPreferences();
    }

    /**
     * Generate combined M3U8 from found streams.
     */
    private void generateCombinedM3U8() {
        if (foundStreams.isEmpty()) return;

        // Find audio and video streams
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
            // Generate combined audio+video playlist
            m3u8Content = m3u8Generator.generateCombinedPlaylist(videoStream.getUrl(), audioStream.getUrl());
        } else {
            // Generate master playlist from all streams
            m3u8Content = m3u8Generator.generateMasterPlaylist(foundStreams);
        }

        // Display
        tvM3U8Title.setVisibility(View.VISIBLE);
        tvM3U8Output.setVisibility(View.VISIBLE);
        tvM3U8Output.setText(m3u8Content);
        btnCopyM3U8.setVisibility(View.VISIBLE);
        btnSaveM3U8.setVisibility(View.VISIBLE);
    }

    /**
     * Save a single stream to file.
     */
    private void saveStreamToFile(HLSAnalyzer.HLSStream stream) {
        String fileName = m3u8Generator.buildFileNameFromUrl(stream.getUrl());
        String content = "#EXTM3U\n" +
                "#EXT-X-VERSION:3\n" +
                stream.getUrl() + "\n";

        File file = m3u8Generator.saveToFile(content, fileName, this);
        if (file != null) {
            Toast.makeText(this, getString(R.string.saved) + " → " + file.getAbsolutePath(),
                    Toast.LENGTH_LONG).show();
        } else {
            // Try internal storage
            File internalFile = m3u8Generator.saveToInternalStorage(content, fileName, this);
            if (internalFile != null) {
                Toast.makeText(this, getString(R.string.saved) + " (внутрішнє сховище)",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * Save generated M3U8 to file.
     */
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
            // Try VLC specifically
            intent.setPackage("org.videolan.vlc");
            try {
                startActivity(intent);
            } catch (Exception e2) {
                // Try generic video
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

    private void saveToPreferences() {
        // Save last analysis results
        StringBuilder sb = new StringBuilder();
        for (HLSAnalyzer.HLSStream stream : foundStreams) {
            sb.append(stream.getUrl()).append("|");
        }
        if (foundStreams.size() > 0) {
            prefs.edit().putString("lastResults", sb.toString()).apply();
        }
    }

    // ===== JavaScript Bridge =====

    private class WebAppInterface {
        @JavascriptInterface
        public void onHLSFound(String url) {
            Log.d(TAG, "JS found HLS: " + url);
            runOnUiThread(() -> {
                HLSAnalyzer.HLSStream stream = new HLSAnalyzer.HLSStream(url);
                stream.setSource("javascript_interface");
                foundStreams.add(stream);
            });
        }

        @JavascriptInterface
        public void onPageLoaded() {
            runOnUiThread(() -> {
                isPageLoaded = true;
                performAnalysis();
            });
        }
    }

    // ===== JavaScript Injection Scripts =====

    /**
     * Get the HTML source of the current page.
     */
    private String getHtmlExtractionScript() {
        return "(function() {" +
                "var html = document.documentElement.outerHTML;" +
                "return html;" +
                "})();";
    }

    /**
     * JavaScript to scan the page for .m3u8 URLs.
     */
    private String getHLSDetectionScript() {
        return "(function() {" +
                "var found = [];" +
                "var seen = {};" +

                // Scan all elements
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
                "      }" +
                "    }" +
                "  }" +
                "}" +

                // Check for Hls.js or Video.js instances
                "if (window.Hls) {" +
                "  try {" +
                "    if (window.hls && window.hls.url) {" +
                "      if (!seen[window.hls.url]) {" +
                "        seen[window.hls.url] = true;" +
                "        found.push(window.hls.url);" +
                "      }" +
                "    }" +
                "  } catch(e) {}" +
                "}" +

                // Check for shaka player
                "if (window.shaka) {" +
                "  try {" +
                "    var players = document.querySelectorAll('video');" +
                "    for (var i = 0; i < players.length; i++) {" +
                "      var src = players[i].currentSrc;" +
                "      if (src && src.indexOf('.m3u8') > -1 && !seen[src]) {" +
                "        seen[src] = true;" +
                "        found.push(src);" +
                "      }" +
                "    }" +
                "  } catch(e) {}" +
                "}" +

                // Check network requests via performance API
                "try {" +
                "  var entries = performance.getEntriesByType('resource');" +
                "  for (var i = 0; i < entries.length; i++) {" +
                "    var name = entries[i].name;" +
                "    if (name && name.indexOf('.m3u8') > -1 && !seen[name]) {" +
                "      seen[name] = true;" +
                "      found.push(name);" +
                "    }" +
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
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (webView != null) {
            webView.destroy();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
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
