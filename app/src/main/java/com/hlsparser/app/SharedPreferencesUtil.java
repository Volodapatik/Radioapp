package com.hlsparser.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * SharedPreferencesUtil - utility for managing app preferences,
 * including analysis history and favorites.
 */
public class SharedPreferencesUtil {

    private static final String PREFS_NAME = "HLSParserPrefs";
    private static final String KEY_LAST_URL = "lastUrl";
    private static final String KEY_LAST_RESULTS = "lastResults";
    private static final String KEY_HISTORY = "analysisHistory";
    private static final String KEY_FAVORITES = "favorites";
    private static final String KEY_DARK_THEME = "darkTheme";

    private SharedPreferences prefs;

    public SharedPreferencesUtil(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // ===== Last URL =====

    public void saveLastUrl(String url) {
        prefs.edit().putString(KEY_LAST_URL, url).apply();
    }

    public String getLastUrl() {
        return prefs.getString(KEY_LAST_URL, "");
    }

    // ===== Last Results =====

    public void saveLastResults(String results) {
        prefs.edit().putString(KEY_LAST_RESULTS, results).apply();
    }

    public String getLastResults() {
        return prefs.getString(KEY_LAST_RESULTS, "");
    }

    // ===== History =====

    /**
     * Save an analysis entry to history.
     * Each entry contains URL, timestamp, and number of streams found.
     */
    public void addToHistory(String url, int streamCount) {
        try {
            JSONArray history = getHistoryArray();
            JSONObject entry = new JSONObject();
            entry.put("url", url);
            entry.put("timestamp", System.currentTimeMillis());
            entry.put("streamCount", streamCount);
            history.put(entry);

            // Keep only last 50 entries
            if (history.length() > 50) {
                JSONArray trimmed = new JSONArray();
                for (int i = history.length() - 50; i < history.length(); i++) {
                    trimmed.put(history.get(i));
                }
                history = trimmed;
            }

            prefs.edit().putString(KEY_HISTORY, history.toString()).apply();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public JSONArray getHistoryArray() {
        String json = prefs.getString(KEY_HISTORY, "[]");
        try {
            return new JSONArray(json);
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    public List<String> getHistoryUrls() {
        List<String> urls = new ArrayList<>();
        try {
            JSONArray history = getHistoryArray();
            for (int i = 0; i < history.length(); i++) {
                JSONObject entry = history.getJSONObject(i);
                urls.add(entry.getString("url"));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return urls;
    }

    public void clearHistory() {
        prefs.edit().remove(KEY_HISTORY).apply();
    }

    // ===== Favorites =====

    /**
     * Add a stream URL to favorites.
     */
    public void addToFavorites(String url, String title) {
        try {
            JSONArray favorites = getFavoritesArray();
            JSONObject entry = new JSONObject();
            entry.put("url", url);
            entry.put("title", title != null ? title : url);
            entry.put("timestamp", System.currentTimeMillis());
            favorites.put(entry);

            prefs.edit().putString(KEY_FAVORITES, favorites.toString()).apply();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public JSONArray getFavoritesArray() {
        String json = prefs.getString(KEY_FAVORITES, "[]");
        try {
            return new JSONArray(json);
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    public List<JSONObject> getFavorites() {
        List<JSONObject> list = new ArrayList<>();
        try {
            JSONArray favorites = getFavoritesArray();
            for (int i = 0; i < favorites.length(); i++) {
                list.add(favorites.getJSONObject(i));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return list;
    }

    public void removeFromFavorites(String url) {
        try {
            JSONArray favorites = getFavoritesArray();
            JSONArray filtered = new JSONArray();
            for (int i = 0; i < favorites.length(); i++) {
                JSONObject entry = favorites.getJSONObject(i);
                if (!entry.getString("url").equals(url)) {
                    filtered.put(entry);
                }
            }
            prefs.edit().putString(KEY_FAVORITES, filtered.toString()).apply();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    // ===== Theme =====

    public void setDarkTheme(boolean enabled) {
        prefs.edit().putBoolean(KEY_DARK_THEME, enabled).apply();
    }

    public boolean isDarkTheme() {
        return prefs.getBoolean(KEY_DARK_THEME, false);
    }
}
