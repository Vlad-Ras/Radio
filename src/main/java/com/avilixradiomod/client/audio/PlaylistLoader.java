package com.avilixradiomod.client.audio;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class PlaylistLoader {

    private PlaylistLoader() {}

    public static boolean isPlaylistUrl(String url) {
        if (url == null) return false;
        String u = url.trim().toLowerCase();
        return u.endsWith(".m3u") || u.endsWith(".m3u8") || u.endsWith(".pls");
    }

    public static List<PlaylistEntry> load(String url) throws Exception {
        if (url == null) return List.of();
        String u = url.trim();
        if (u.isEmpty()) return List.of();

        String low = u.toLowerCase();
        if (low.endsWith(".pls")) return loadPLS(u);
        if (low.endsWith(".m3u") || low.endsWith(".m3u8")) return loadM3U(u);

        return List.of(new PlaylistEntry(u, null)); // обычный стрим
    }

    private static List<PlaylistEntry> loadM3U(String url) throws Exception {
        List<PlaylistEntry> list = new ArrayList<>();
        String lastTitle = null;

        try (BufferedReader br = open(url)) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                // #EXTINF:-1,Artist - Title
                if (line.startsWith("#EXTINF:")) {
                    int comma = line.indexOf(',');
                    if (comma >= 0 && comma + 1 < line.length()) {
                        lastTitle = line.substring(comma + 1).trim();
                        if (lastTitle.isEmpty()) lastTitle = null;
                    } else {
                        lastTitle = null;
                    }
                    continue;
                }

                if (line.startsWith("#")) continue;

                if (line.startsWith("http://") || line.startsWith("https://")) {
                    list.add(new PlaylistEntry(line, lastTitle));
                    lastTitle = null;
                }
            }
        }
        return list;
    }

    private static List<PlaylistEntry> loadPLS(String url) throws Exception {
        List<PlaylistEntry> list = new ArrayList<>();
        try (BufferedReader br = open(url)) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (!line.regionMatches(true, 0, "File", 0, 4)) continue;
                int eq = line.indexOf('=');
                if (eq <= 0) continue;

                String val = line.substring(eq + 1).trim();
                if (val.startsWith("http://") || val.startsWith("https://")) {
                    list.add(new PlaylistEntry(val, null));
                }
            }
        }
        return list;
    }

    private static BufferedReader open(String url) throws Exception {
        URLConnection c = new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(15000);
        c.setRequestProperty("User-Agent", "AvilixRadioMod");
        return new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8));
    }
}
