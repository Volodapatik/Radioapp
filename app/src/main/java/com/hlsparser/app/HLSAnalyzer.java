package com.hlsparser.app;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HLSAnalyzer - core class for detecting HLS streams (.m3u8) from HTML/JS content.
 * Scans page source for .m3u8 URLs in various forms:
 * - Direct .m3u8 links in HTML
 * - .m3u8 URLs embedded in JavaScript
 * - Base64-encoded .m3u8 URLs
 * - Dynamic player configurations
 */
public class HLSAnalyzer {

    /**
     * Known HLS playlist file names to look for.
     */
    private static final String[] HLS_FILE_NAMES = {
            "master.m3u8",
            "index.m3u8",
            "index-v.m3u8",
            "index-a.m3u8",
            "playlist.m3u8",
            "video.m3u8",
            "audio.m3u8",
            "live.m3u8",
            "stream.m3u8",
            "main.m3u8"
    };

    /**
     * Regex patterns for finding .m3u8 URLs in page content.
     */
    private static final Pattern PATTERN_M3U8_URL = Pattern.compile(
            "(https?://[^\"'\\s]+?\\.m3u8(?:\\?[^\"'\\s]*)?)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PATTERN_M3U8_ENCODED = Pattern.compile(
            "(https?://[^\"'\\\\]+?\\.m3u8(?:\\\\[?][^\"'\\\\]*)?)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PATTERN_SOURCE_TAG = Pattern.compile(
            "<source[^>]*src=[\"']([^\"']*\\.m3u8[^\"']*)[\"']",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PATTERN_SRC_ATTRIBUTE = Pattern.compile(
            "src=[\"']([^\"']*\\.m3u8[^\"']*)[\"']",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PATTERN_HREF_ATTRIBUTE = Pattern.compile(
            "href=[\"']([^\"']*\\.m3u8[^\"']*)[\"']",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PATTERN_HLS_CONFIG = Pattern.compile(
            "(?:url|src|hlsUrl|hlsSource|streamUrl|videoUrl|mediaUrl)[\"'\\s:=]+[\"'](https?://[^\"']\\.m3u8[^\"']*)[\"']",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PATTERN_M3U8_IN_STRING = Pattern.compile(
            "[\"'](https?://[^\"']*?\\.m3u8[^\"']*?)[\"']",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PATTERN_BASE64_M3U8 = Pattern.compile(
            "(?:atob|decodeURI|unescape)[\\s\\(]+[\"']([A-Za-z0-9+/=]+)[\"']",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Represents a found HLS stream.
     */
    public static class HLSStream {
        private String url;
        private String type; // "video", "audio", "master", "unknown"
        private String source; // where it was found
        private String resolution;

        public HLSStream(String url) {
            this.url = url;
            this.type = "unknown";
            this.source = "page";
            this.resolution = "";
            classify();
        }

        private void classify() {
            String lowerUrl = url.toLowerCase();

            if (lowerUrl.contains("master")) {
                this.type = "master";
            } else if (lowerUrl.contains("index-a") || lowerUrl.contains("audio")) {
                this.type = "audio";
            } else if (lowerUrl.contains("index-v") || lowerUrl.contains("video")) {
                this.type = "video";
            } else if (lowerUrl.contains("index") || lowerUrl.contains("playlist")) {
                this.type = "playlist";
            } else if (lowerUrl.contains("live") || lowerUrl.contains("stream")) {
                this.type = "live";
            } else {
                this.type = "stream";
            }

            // Try to extract resolution from URL
            Matcher resMatcher = Pattern.compile("(\\d{3,4}x\\d{3,4})").matcher(url);
            if (resMatcher.find()) {
                this.resolution = resMatcher.group(1);
            } else {
                resMatcher = Pattern.compile("/(\\d{3,4}p)/").matcher(url);
                if (resMatcher.find()) {
                    this.resolution = resMatcher.group(1);
                }
            }
        }

        public String getUrl() { return url; }
        public String getType() { return type; }
        public String getSource() { return source; }
        public String getResolution() { return resolution; }

        public void setSource(String source) { this.source = source; }
    }

    /**
     * Analyze HTML/JS content for HLS streams.
     *
     * @param pageContent the full page HTML source
     * @param pageUrl the original page URL (for resolving relative URLs)
     * @return list of found HLS streams
     */
    public List<HLSStream> analyze(String pageContent, String pageUrl) {
        Set<String> foundUrls = new HashSet<>();
        List<HLSStream> results = new ArrayList<>();

        if (pageContent == null || pageContent.isEmpty()) {
            return results;
        }

        // 1. Find direct .m3u8 URLs
        findMatches(PATTERN_M3U8_URL, pageContent, foundUrls);

        // 2. Find encoded .m3u8 URLs (with escaped characters)
        findMatches(PATTERN_M3U8_ENCODED, pageContent, foundUrls);

        // 3. Find <source> tags with .m3u8
        findMatches(PATTERN_SOURCE_TAG, pageContent, foundUrls);

        // 4. Find src attributes with .m3u8
        findMatches(PATTERN_SRC_ATTRIBUTE, pageContent, foundUrls);

        // 5. Find href attributes with .m3u8
        findMatches(PATTERN_HREF_ATTRIBUTE, pageContent, foundUrls);

        // 6. Find HLS config patterns
        findMatches(PATTERN_HLS_CONFIG, pageContent, foundUrls);

        // 7. Find .m3u8 in string literals
        findMatches(PATTERN_M3U8_IN_STRING, pageContent, foundUrls);

        // Convert all found URLs to HLSStream objects
        for (String url : foundUrls) {
            String cleanUrl = cleanUrl(url);
            String resolvedUrl = resolveUrl(cleanUrl, pageUrl);

            if (isValidM3U8Url(resolvedUrl)) {
                HLSStream stream = new HLSStream(resolvedUrl);
                stream.setSource("page_content");
                results.add(stream);
            }
        }

        // Also check for known file patterns relative to page URL
        results.addAll(findKnownFiles(pageUrl));

        return results;
    }

    /**
     * Find all pattern matches and add captured groups to the set.
     */
    private void findMatches(Pattern pattern, String content, Set<String> urls) {
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            if (matcher.groupCount() > 0) {
                String url = matcher.group(1);
                if (url != null) {
                    urls.add(url);
                }
            } else {
                urls.add(matcher.group(0));
            }
        }
    }

    /**
     * Clean URL by removing escape characters and extra quotes.
     */
    private String cleanUrl(String url) {
        if (url == null) return "";
        return url.replace("\\", "")
                .replace("\"", "")
                .replace("'", "")
                .trim();
    }

    /**
     * Resolve relative URLs to absolute URLs.
     */
    private String resolveUrl(String url, String baseUrl) {
        if (url == null || url.isEmpty()) return "";
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url;
        }

        try {
            URL base = new URL(baseUrl);
            if (url.startsWith("//")) {
                return base.getProtocol() + ":" + url;
            } else if (url.startsWith("/")) {
                return base.getProtocol() + "://" + base.getHost() + url;
            } else {
                String path = base.getPath();
                if (path.contains("/")) {
                    path = path.substring(0, path.lastIndexOf("/") + 1);
                } else {
                    path = "/";
                }
                return base.getProtocol() + "://" + base.getHost() + path + url;
            }
        } catch (Exception e) {
            return url;
        }
    }

    /**
     * Validate if a URL is a proper .m3u8 URL.
     */
    private boolean isValidM3U8Url(String url) {
        if (url == null || url.isEmpty()) return false;
        return url.contains(".m3u8") &&
                (url.startsWith("http://") || url.startsWith("https://"));
    }

    /**
     * Find known HLS file names relative to the page URL path.
     */
    private List<HLSStream> findKnownFiles(String baseUrl) {
        List<HLSStream> results = new ArrayList<>();
        try {
            URL base = new URL(baseUrl);
            String path = base.getPath();
            if (path.contains("/")) {
                path = path.substring(0, path.lastIndexOf("/"));
            }

            for (String fileName : HLS_FILE_NAMES) {
                String candidate = base.getProtocol() + "://" +
                        base.getHost() + path + "/" + fileName;
                HLSStream stream = new HLSStream(candidate);
                stream.setSource("guessed_path");
                // Note: these are guesses, not confirmed files
                // In practice, you'd check with an HTTP HEAD request
                // For now, we skip guessed URLs to avoid false positives
            }
        } catch (Exception e) {
            // Skip
        }
        return results;
    }

    /**
     * Analyze M3U8 content to find sub-playlists (video/audio variants).
     *
     * @param m3u8Content the content of a master playlist
     * @param baseUrl the base URL for resolving relative playlist URLs
     * @return list of sub-playlists found
     */
    public List<HLSStream> parseMasterPlaylist(String m3u8Content, String baseUrl) {
        List<HLSStream> results = new ArrayList<>();
        if (m3u8Content == null || m3u8Content.isEmpty()) return results;

        String[] lines = m3u8Content.split("\\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            if (line.contains(".m3u8") && !line.startsWith("#")) {
                String resolvedUrl = resolveUrl(line, baseUrl);
                HLSStream stream = new HLSStream(resolvedUrl);
                stream.setSource("master_playlist");
                results.add(stream);
            } else if (line.startsWith("#EXT-X-STREAM-INF:")) {
                // Extract resolution from stream info
                Matcher resMatcher = Pattern.compile("RESOLUTION=(\\d+x\\d+)").matcher(line);
                if (resMatcher.find()) {
                    // Store resolution for the next line
                }
            }
        }
        return results;
    }

    /**
     * Generate a combined M3U8 file from separate audio and video streams.
     *
     * @param videoUrl the video HLS URL
     * @param audioUrl the audio HLS URL
     * @return a valid M3U8 content string
     */
    public String generateCombinedM3U8(String videoUrl, String audioUrl) {
        StringBuilder sb = new StringBuilder();
        sb.append("#EXTM3U\n");
        sb.append("#EXT-X-VERSION:3\n");
        sb.append("#EXT-X-INDEPENDENT-SEGMENTS\n");

        // Audio group
        sb.append("#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"audio\",NAME=\"Audio\",AUTOSELECT=YES,DEFAULT=YES,URI=\"")
                .append(audioUrl).append("\"\n");

        // Video stream
        sb.append("#EXT-X-STREAM-INF:BANDWIDTH=5000000,AUDIO=\"audio\",RESOLUTION=1920x1080\n");
        sb.append(videoUrl).append("\n");

        return sb.toString();
    }
}
