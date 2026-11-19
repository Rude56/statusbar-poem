package io.github.rude56.poemstatus;

import java.util.ArrayList;
import java.util.List;

public final class PoemConfig {
    public static final int SPEED_MIN = 20;
    public static final int SPEED_MAX = 600;
    public static final int SPEED_DEFAULT = 120;

    public static final String[] WHITELIST_DEFAULTS = {
            "me.weishu.kernelsu",
            "org.lsposed.manager",
    };
    public static final int CLOCK_MIN = 0;
    public static final int CLOCK_MAX = 600;
    public static final int CLOCK_DEFAULT = 10;

    public boolean enabled = false;
    public int speedPx = SPEED_DEFAULT;
    public int clockSeconds = CLOCK_DEFAULT;
    public boolean showAuthor = true;
    public boolean showSource = true;
    public boolean bobEnabled = false;
    public boolean landscapePause = true;
    public boolean lockscreenPause = true;
    public String orderMode = PoemLibrary.ORDER_SEQUENTIAL;
    public List<String> whitelist = new ArrayList<>();

    public boolean whitelistSeeded = false;
    public String poemsJson = "";

    public boolean poemsSeeded = false;

    public String holesJson = "";

    public long configRevision = 0L;

    public java.util.List<HoleRects.Hole> holes() {
        return HoleRects.parse(encodeHoles());
    }

    private String encodeHoles() {
        String raw = holesJson == null ? "" : holesJson.trim();
        if (raw.isEmpty() || "[]".equals(raw)) {
            return "";
        }
        if (raw.startsWith("[")) {
            return HoleRects.encode(HoleRects.fromJson(raw));
        }
        return raw;
    }
    public PoemConfig normalized() {
        int s = clamp(speedPx, SPEED_MIN, SPEED_MAX);
        int c = clamp(clockSeconds, CLOCK_MIN, CLOCK_MAX);
        if (s != speedPx) {
            speedPx = s;
        }
        if (c != clockSeconds) {
            clockSeconds = c;
        }
        orderMode = PoemLibrary.normalizeOrder(orderMode);
        if (whitelist == null) {
            whitelist = new ArrayList<>();
        }
        if (poemsJson == null) {
            poemsJson = "";
        }
        if (holesJson == null) {
            holesJson = "";
        }

        return this;
    }

    public boolean isWhitelisted(String pkg) {
        if (pkg == null) {
            return false;
        }
        String target = pkg.trim();
        if (target.isEmpty()) {
            return false;
        }
        for (int i = 0; i < whitelist.size(); i++) {
            String entry = whitelist.get(i);
            if (entry == null) {
                continue;
            }
            if (entry.trim().equalsIgnoreCase(target)) {
                return true;
            }
        }
        return false;
    }
    public PoemConfig copy() {
        PoemConfig c = new PoemConfig();
        c.enabled = enabled;
        c.speedPx = speedPx;
        c.clockSeconds = clockSeconds;
        c.showAuthor = showAuthor;
        c.showSource = showSource;
        c.bobEnabled = bobEnabled;
        c.landscapePause = landscapePause;
        c.lockscreenPause = lockscreenPause;
        c.orderMode = orderMode;
        c.whitelist = whitelist == null ? new ArrayList<>() : new ArrayList<>(whitelist);
        c.whitelistSeeded = whitelistSeeded;
        c.poemsJson = poemsJson;
        c.poemsSeeded = poemsSeeded;
        c.holesJson = holesJson;

        c.configRevision = configRevision;
        return c;
    }

    public static int clamp(int v, int min, int max) {
        return v < min ? min : (v > max ? max : v);
    }
}
