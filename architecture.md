# HLS Parser - Android Architecture

## Structure
```
app/
  src/main/
    java/com/hlsparser/app/
      MainActivity.java          - Main activity, UI logic
      HLSAnalyzer.java           - Core HLS stream detection
      M3U8Generator.java         - M3U8 file generation
      ResultAdapter.java         - RecyclerView adapter for results
    res/
      layout/
        activity_main.xml        - Main layout
        item_result.xml          - Result item layout
      values/
        strings.xml
        colors.xml
        themes.xml
      drawable/
        ic_copy.xml
        ic_save.xml
        ic_play.xml
    AndroidManifest.xml
  build.gradle
build.gradle
settings.gradle
```

## Components
1. MainActivity - handles URL input, WebView setup, results display
2. HLSAnalyzer - parses HTML/JS for .m3u8 URLs, handles different patterns
3. M3U8Generator - merges audio+video playlists into standard M3U8
4. ResultAdapter - displays found streams in a list

## Features
- URL input field
- Analyze button
- WebView (with JS, DOM storage, cookies)
- Results list (copy, save, play)
- M3U8 generation for combined streams
