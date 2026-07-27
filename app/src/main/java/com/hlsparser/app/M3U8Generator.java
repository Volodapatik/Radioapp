package com.hlsparser.app;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

/**
 * M3U8Generator - handles generation and saving of M3U8 files.
 * Supports creating master playlists and combined audio/video playlists.
 */
public class M3U8Generator {

    /**
     * Generate a master playlist that lists all found streams.
     *
     * @param streams list of HLS streams to include
     * @return M3U8 content as string
     */
    public String generateMasterPlaylist(List<HLSAnalyzer.HLSStream> streams) {
        if (streams == null || streams.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("#EXTM3U\n");
        sb.append("#EXT-X-VERSION:3\n");
        sb.append("\n");

        for (HLSAnalyzer.HLSStream stream : streams) {
            String bandwidth = "0";
            String resolution = "";

            // Determine bandwidth based on type
            switch (stream.getType()) {
                case "audio":
                    bandwidth = "128000";
                    break;
                case "video":
                    bandwidth = "5000000";
                    break;
                case "master":
                    bandwidth = "3000000";
                    break;
                default:
                    bandwidth = "1500000";
                    break;
            }

            // Add resolution if available
            if (!stream.getResolution().isEmpty()) {
                resolution = ",RESOLUTION=" + stream.getResolution();
            }

            if (stream.getType().equals("audio")) {
                // Audio stream - use EXT-X-MEDIA
                sb.append("#EXT-X-MEDIA:TYPE=AUDIO,")
                        .append("GROUP-ID=\"audio\",")
                        .append("NAME=\"")
                        .append(stream.getResolution().isEmpty() ? "Audio" : stream.getResolution())
                        .append("\",AUTOSELECT=YES,")
                        .append("DEFAULT=YES,")
                        .append("URI=\"")
                        .append(stream.getUrl())
                        .append("\"\n");
            } else {
                // Video stream - use EXT-X-STREAM-INF
                sb.append("#EXT-X-STREAM-INF:BANDWIDTH=")
                        .append(bandwidth)
                        .append(resolution)
                        .append(stream.getType().equals("video") ? ",CODECS=\"avc1.640028,mp4a.40.2\"" : "")
                        .append("\n");
                sb.append(stream.getUrl())
                        .append("\n\n");
            }
        }

        return sb.toString();
    }

    /**
     * Generate a combined playlist for separate audio and video streams.
     *
     * @param videoUrl the video HLS stream URL
     * @param audioUrl the audio HLS stream URL
     * @return combined M3U8 content
     */
    public String generateCombinedPlaylist(String videoUrl, String audioUrl) {
        StringBuilder sb = new StringBuilder();
        sb.append("#EXTM3U\n");
        sb.append("#EXT-X-VERSION:3\n");
        sb.append("#EXT-X-INDEPENDENT-SEGMENTS\n\n");

        // Define audio media
        sb.append("#EXT-X-MEDIA:TYPE=AUDIO,")
                .append("GROUP-ID=\"audio\",\n")
                .append("NAME=\"Audio\",")
                .append("AUTOSELECT=YES,")
                .append("DEFAULT=YES,")
                .append("URI=\"")
                .append(audioUrl)
                .append("\"\n\n");

        // Video stream with audio group reference
        sb.append("#EXT-X-STREAM-INF:")
                .append("BANDWIDTH=5000000,")
                .append("CODECS=\"avc1.640028,mp4a.40.2\",")
                .append("AUDIO=\"audio\",")
                .append("RESOLUTION=1920x1080\n");
        sb.append(videoUrl).append("\n");

        return sb.toString();
    }

    /**
     * Save M3U8 content to a file.
     *
     * @param content the M3U8 content
     * @param fileName desired file name (without extension)
     * @param context Android context for file operations
     * @return the saved file, or null on failure
     */
    public File saveToFile(String content, String fileName, android.content.Context context) {
        try {
            File downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS
            );

            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs();
            }

            File m3u8Dir = new File(downloadsDir, "HLSParser");
            if (!m3u8Dir.exists()) {
                m3u8Dir.mkdirs();
            }

            File file = new File(m3u8Dir, fileName + ".m3u8");

            // Handle file name collision
            int counter = 1;
            while (file.exists()) {
                file = new File(m3u8Dir, fileName + "_" + counter + ".m3u8");
                counter++;
            }

            FileWriter writer = new FileWriter(file);
            writer.write(content);
            writer.close();

            return file;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Save M3U8 content to internal app storage.
     *
     * @param content the M3U8 content
     * @param fileName desired file name
     * @param context Android context
     * @return the saved file, or null on failure
     */
    public File saveToInternalStorage(String content, String fileName, android.content.Context context) {
        try {
            File file = new File(context.getFilesDir(), fileName + ".m3u8");
            FileWriter writer = new FileWriter(file);
            writer.write(content);
            writer.close();
            return file;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Build a file name from the URL.
     *
     * @param url the stream URL
     * @return a clean file name
     */
    public String buildFileNameFromUrl(String url) {
        if (url == null || url.isEmpty()) {
            return "playlist";
        }

        // Extract the last part of the URL
        String[] parts = url.split("/");
        String lastPart = parts[parts.length - 1];

        // Remove query parameters
        int queryIndex = lastPart.indexOf("?");
        if (queryIndex > 0) {
            lastPart = lastPart.substring(0, queryIndex);
        }

        // Remove .m3u8 extension
        if (lastPart.endsWith(".m3u8")) {
            lastPart = lastPart.substring(0, lastPart.length() - 5);
        }

        // Clean up
        lastPart = lastPart.replaceAll("[^a-zA-Z0-9_-]", "_");

        return lastPart.isEmpty() ? "playlist" : lastPart;
    }
}
