# HLS Parser - Android Application

HLS Parser is a native Android application written in Java. It provides a built-in web browser (WebView) that analyzes web pages to extract hidden or dynamically loaded HLS video streams (`.m3u8` files). The application can automatically parse JavaScript, DOM elements, and network requests to find playable streams, and even merge separate audio and video tracks into a standard M3U8 playlist.

## Features

- **Integrated WebView Browser**: Loads the target video page directly within the app.
- **Deep HTML/JS Analysis**: Scans `<source>` tags, script variables, and DOM attributes for `.m3u8` URLs.
- **JavaScript Injection**: Injects a custom script to inspect active players (like `Hls.js`, `Video.js`, `Shaka Player`) and monitor the Performance API for background network requests containing stream URLs.
- **Stream Management**: 
  - View all discovered streams.
  - Copy URLs to clipboard.
  - Save individual or combined streams to local storage.
  - Open streams directly in external players (e.g., VLC).
- **M3U8 Generator**: Automatically combines separate audio and video `.m3u8` streams into a single, compliant master playlist.
- **History and Favorites**: Keeps track of analyzed URLs and saved streams using `SharedPreferences`.
- **Dark Mode**: Supports system-level light and dark themes.

## Requirements

- Android 7.0 (API Level 24) or higher.
- Java 8 or higher.
- Android Studio for building the project.

## Project Structure

```
app/src/main/java/com/hlsparser/app/
├── MainActivity.java          # Core Activity, manages UI and WebView lifecycle
├── HLSAnalyzer.java           # Regex and DOM parsing logic for extracting URLs
├── HLSStreamChecker.java      # Asynchronous HTTP HEAD/GET requests to verify streams
├── M3U8Generator.java         # Utility to build master/combined M3U8 files
├── ResultAdapter.java         # RecyclerView adapter for displaying found streams
└── SharedPreferencesUtil.java # Data persistence for history and settings

app/src/main/res/layout/
├── activity_main.xml          # Main activity layout
└── item_result.xml            # Single stream item layout
```

## How it Works

1. **Input**: The user enters a URL in the `EditText` field.
2. **Navigation**: The app loads the URL into a `WebView` configured to handle JavaScript, DOM storage, cookies, and modern User-Agent strings.
3. **Extraction (Java Side)**: Once the `WebView` finishes loading, it passes the raw HTML back to the Java layer. `HLSAnalyzer.java` uses Regex patterns to scan the source code for typical `.m3u8` occurrences (e.g., `master.m3u8`, `index.m3u8`, inside `<source>` tags).
4. **Injection (JavaScript Side)**: Simultaneously, a JavaScript snippet (`getHLSDetectionScript()`) runs inside the `WebView`. It inspects global variables for active media players, scans the `performance.getEntriesByType('resource')` API for network requests, and checks DOM elements. Found URLs are passed back via a `@JavascriptInterface`.
5. **Deduplication**: Both Java and JavaScript results are combined, deduplicated, and classified (e.g., as audio, video, or master streams) by `HLSAnalyzer.java`.
6. **Output**: The streams are displayed in a `RecyclerView`. The user can tap to copy, save, or play the streams.

## Building the Project

1. Clone the repository or unzip the project files.
2. Open the project in Android Studio.
3. Allow Gradle to sync and download dependencies.
4. Click **Run** to deploy the app to an emulator or physical device.

## Future Enhancements

- Expose a REST API within the app to allow other devices to send URLs for remote parsing.
- Add support for DASH (`.mpd`) stream extraction.
- Implement an internal video player (e.g., ExoPlayer/Media3) to preview streams directly in the app.
