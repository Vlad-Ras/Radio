package com.avilixradiomod.client;

import com.avilixradiomod.config.ModConfigs;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public final class UrlHistory {
    private UrlHistory() {}

    public static List<String> getAll() {
        List<? extends String> raw = ModConfigs.CLIENT.urlHistory.get();
        List<String> out = new ArrayList<>();
        for (String s : raw) out.add(s);
        return out;
    }

    public static String getDefaultUrl() {
        // Prefer COMMON defaultStreamUrl if user configured it:
        // - it affects newly placed radios server-side
        // - and users expect the GUI "default URL" to match that setting.
        String common = ModConfigs.COMMON.defaultStreamUrl.get();
        if (common != null) {
            common = common.trim();
            if (!common.isEmpty()) return common;
        }

        // Fallback: client-side GUI default.
        String d = ModConfigs.CLIENT.defaultUrl.get();
        return d == null ? "" : d.trim();
    }

    public static void remember(String url) {
        if (url == null) return;
        url = url.trim();
        if (url.isEmpty()) return;

        int limit = ModConfigs.CLIENT.historyLimit.get();
        if (limit <= 0) return;

        var set = new LinkedHashSet<String>();
        set.add(url);

        for (String old : getAll()) {
            if (set.size() >= limit) break;
            if (old == null) continue;
            old = old.trim();
            if (old.isEmpty()) continue;
            set.add(old);
        }

        ModConfigs.CLIENT.urlHistory.set(new ArrayList<>(set));
    }

    public static void remove(String url) {
        if (url == null) return;
        url = url.trim();
        if (url.isEmpty()) return;

        List<String> list = getAll();
        String finalUrl = url;
        list.removeIf(s -> s != null && s.trim().equals(finalUrl));
        ModConfigs.CLIENT.urlHistory.set(list);
    }

    public static void clear() {
        ModConfigs.CLIENT.urlHistory.set(List.of());
    }
}
