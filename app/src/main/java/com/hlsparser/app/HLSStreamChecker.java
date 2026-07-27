package com.hlsparser.app;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * HLSStreamChecker - checks if HLS streams are accessible.
 * Performs HTTP HEAD requests to verify stream availability.
 */
public class HLSStreamChecker {

    private static final String TAG = "HLSStreamChecker";
    private static final int TIMEOUT_MS = 10000;
    private static final int MAX_THREADS = 3;

    private ExecutorService executor;
    private AtomicBoolean cancelled = new AtomicBoolean(false);

    /**
     * Callback interface for check results.
     */
    public interface StreamCheckCallback {
        void onResult(HLSAnalyzer.HLSStream stream, int statusCode, long responseTime);
        void onError(HLSAnalyzer.HLSStream stream, String error);
        void onComplete();
    }

    public HLSStreamChecker() {
        executor = Executors.newFixedThreadPool(MAX_THREADS);
    }

    /**
     * Check a single stream.
     *
     * @param stream the HLS stream to check
     * @param callback result callback
     */
    public void checkStream(HLSAnalyzer.HLSStream stream, StreamCheckCallback callback) {
        executor.execute(() -> {
            if (cancelled.get()) return;

            long startTime = System.currentTimeMillis();
            HttpURLConnection connection = null;
            try {
                URL url = new URL(stream.getUrl());
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("HEAD");
                connection.setConnectTimeout(TIMEOUT_MS);
                connection.setReadTimeout(TIMEOUT_MS);
                connection.setRequestProperty("User-Agent",
                        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0.0.0");

                int statusCode = connection.getResponseCode();
                long responseTime = System.currentTimeMillis() - startTime;

                new Handler(Looper.getMainLooper()).post(() ->
                        callback.onResult(stream, statusCode, responseTime));

            } catch (IOException e) {
                long responseTime = System.currentTimeMillis() - startTime;
                new Handler(Looper.getMainLooper()).post(() ->
                        callback.onError(stream, e.getMessage()));
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    /**
     * Check multiple streams concurrently.
     *
     * @param streams list of streams to check
     * @param callback result callback (onComplete called when all done)
     */
    public void checkAllStreams(java.util.List<HLSAnalyzer.HLSStream> streams, StreamCheckCallback callback) {
        cancelled.set(false);
        final java.util.concurrent.atomic.AtomicInteger remaining =
                new java.util.concurrent.atomic.AtomicInteger(streams.size());

        for (HLSAnalyzer.HLSStream stream : streams) {
            if (cancelled.get()) break;

            checkStream(stream, new StreamCheckCallback() {
                @Override
                public void onResult(HLSAnalyzer.HLSStream s, int statusCode, long responseTime) {
                    callback.onResult(s, statusCode, responseTime);
                    if (remaining.decrementAndGet() == 0) {
                        callback.onComplete();
                    }
                }

                @Override
                public void onError(HLSAnalyzer.HLSStream s, String error) {
                    callback.onError(s, error);
                    if (remaining.decrementAndGet() == 0) {
                        callback.onComplete();
                    }
                }

                @Override
                public void onComplete() {
                    // Not used individually
                }
            });
        }
    }

    /**
     * Cancel all pending checks.
     */
    public void cancel() {
        cancelled.set(true);
        executor.shutdownNow();
        executor = Executors.newFixedThreadPool(MAX_THREADS);
    }

    /**
     * Shutdown the executor.
     */
    public void shutdown() {
        cancelled.set(true);
        executor.shutdown();
    }

    /**
     * Try to download a small portion of the stream to verify it's a valid HLS playlist.
     */
    public void verifyPlaylistContent(HLSAnalyzer.HLSStream stream, VerifyCallback callback) {
        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(stream.getUrl());
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(TIMEOUT_MS);
                connection.setReadTimeout(TIMEOUT_MS);
                connection.setRequestProperty("User-Agent",
                        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0.0.0");

                int statusCode = connection.getResponseCode();
                if (statusCode == 200) {
                    InputStream is = connection.getInputStream();
                    byte[] buffer = new byte[1024];
                    int read = is.read(buffer);
                    String content = new String(buffer, 0, read > 0 ? read : 0);
                    boolean isValid = content.startsWith("#EXTM3U");

                    new Handler(Looper.getMainLooper()).post(() ->
                            callback.onVerified(stream, statusCode, isValid, content.substring(0, Math.min(500, content.length()))));
                } else {
                    new Handler(Looper.getMainLooper()).post(() ->
                            callback.onVerified(stream, statusCode, false, ""));
                }
            } catch (IOException e) {
                new Handler(Looper.getMainLooper()).post(() ->
                        callback.onError(stream, e.getMessage()));
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    public interface VerifyCallback {
        void onVerified(HLSAnalyzer.HLSStream stream, int statusCode, boolean isValid, String preview);
        void onError(HLSAnalyzer.HLSStream stream, String error);
    }
}
