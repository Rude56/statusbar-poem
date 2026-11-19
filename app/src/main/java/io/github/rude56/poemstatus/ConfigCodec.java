package io.github.rude56.poemstatus;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class ConfigCodec {
    private ConfigCodec() {
    }
    public static String encode(PoemConfig c) {
        PoemConfig cfg = c == null ? new PoemConfig() : c.normalized();
        JSONObject o = new JSONObject();
        try {
            o.put(ConfigKeys.ENABLED, cfg.enabled);
            o.put(ConfigKeys.SPEED_PX, cfg.speedPx);
            o.put(ConfigKeys.CLOCK_SECONDS, cfg.clockSeconds);
            o.put(ConfigKeys.SHOW_AUTHOR, cfg.showAuthor);
            o.put(ConfigKeys.SHOW_SOURCE, cfg.showSource);
            o.put(ConfigKeys.BOB_ENABLED, cfg.bobEnabled);
            o.put(ConfigKeys.LANDSCAPE_PAUSE, cfg.landscapePause);
            o.put(ConfigKeys.LOCKSCREEN_PAUSE, cfg.lockscreenPause);
            o.put(ConfigKeys.ORDER_MODE, cfg.orderMode);
            JSONArray wl = new JSONArray();
            for (String p : cfg.whitelist) {
                if (p != null && !p.trim().isEmpty()) {
                    wl.put(p.trim());
                }
            }
            o.put(ConfigKeys.WHITELIST, wl);
            o.put(ConfigKeys.POEMS_JSON, cfg.poemsJson == null ? "" : cfg.poemsJson);
            o.put(ConfigKeys.POEMS_SEEDED, cfg.poemsSeeded);
            o.put(ConfigKeys.WHITELIST_SEEDED, cfg.whitelistSeeded);
            o.put(ConfigKeys.HOLES_JSON, cfg.holesJson == null ? "" : cfg.holesJson);
            o.put(ConfigKeys.CONFIG_REVISION, cfg.configRevision);
        } catch (Throwable ignored) {

        }
        return o.toString();
    }

    public static PoemConfig decode(String json) {
        PoemConfig c = new PoemConfig();
        if (json == null || json.trim().isEmpty()) {
            return c.normalized();
        }
        JSONObject o;
        try {
            o = new JSONObject(json);
        } catch (Throwable t) {
            return c.normalized();
        }
        c.enabled = o.optBoolean(ConfigKeys.ENABLED, c.enabled);
        c.speedPx = o.optInt(ConfigKeys.SPEED_PX, c.speedPx);
        c.clockSeconds = o.optInt(ConfigKeys.CLOCK_SECONDS, c.clockSeconds);
        c.showAuthor = o.optBoolean(ConfigKeys.SHOW_AUTHOR, c.showAuthor);
        c.showSource = o.optBoolean(ConfigKeys.SHOW_SOURCE, c.showSource);
        c.bobEnabled = o.optBoolean(ConfigKeys.BOB_ENABLED, c.bobEnabled);
        c.landscapePause = o.optBoolean(ConfigKeys.LANDSCAPE_PAUSE, c.landscapePause);
        c.lockscreenPause = o.optBoolean(ConfigKeys.LOCKSCREEN_PAUSE, c.lockscreenPause);
        c.orderMode = o.optString(ConfigKeys.ORDER_MODE, c.orderMode);
        List<String> wl = new ArrayList<>();
        JSONArray arr = o.optJSONArray(ConfigKeys.WHITELIST);
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                String p = arr.optString(i, "").trim();
                if (!p.isEmpty()) {
                    wl.add(p);
                }
            }
        }
        c.whitelist = wl;
        c.poemsJson = o.optString(ConfigKeys.POEMS_JSON, "");
        c.poemsSeeded = o.optBoolean(ConfigKeys.POEMS_SEEDED, false);
        c.whitelistSeeded = o.optBoolean(ConfigKeys.WHITELIST_SEEDED, false);
        c.holesJson = o.optString(ConfigKeys.HOLES_JSON, "");
        c.configRevision = o.optLong(ConfigKeys.CONFIG_REVISION, 0L);
        return c.normalized();
    }

    public static String whitelistToText(List<String> list) {
        StringBuilder sb = new StringBuilder();
        if (list != null) {
            for (String p : list) {
                if (p == null || p.trim().isEmpty()) {
                    continue;
                }
                if (sb.length() > 0) {
                    sb.append(',');
                }
                sb.append(p.trim());
            }
        }
        return sb.toString();
    }

    public static List<String> parseWhitelist(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) {
            return out;
        }
        for (String part : text.split("[,;\\s]+")) {
            String p = part.trim();
            if (!p.isEmpty() && !out.contains(p)) {
                out.add(p);
            }
        }
        return out;
    }
}
