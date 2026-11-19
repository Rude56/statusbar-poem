package io.github.rude56.poemstatus;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public final class ConfigStore {
    private ConfigStore() {
    }
    public static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(ConfigKeys.PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static PoemConfig load(Context ctx) {
        SharedPreferences sp = prefs(ctx);
        PoemConfig c = new PoemConfig();
        c.enabled = sp.getBoolean(ConfigKeys.ENABLED, false);
        c.speedPx = sp.getInt(ConfigKeys.SPEED_PX, PoemConfig.SPEED_DEFAULT);
        c.clockSeconds = sp.getInt(ConfigKeys.CLOCK_SECONDS, PoemConfig.CLOCK_DEFAULT);
        c.showAuthor = sp.getBoolean(ConfigKeys.SHOW_AUTHOR, true);
        c.showSource = sp.getBoolean(ConfigKeys.SHOW_SOURCE, true);
        c.bobEnabled = sp.getBoolean(ConfigKeys.BOB_ENABLED, false);
        c.landscapePause = sp.getBoolean(ConfigKeys.LANDSCAPE_PAUSE, true);
        c.lockscreenPause = sp.getBoolean(ConfigKeys.LOCKSCREEN_PAUSE, true);
        c.orderMode = sp.getString(ConfigKeys.ORDER_MODE, PoemLibrary.ORDER_SEQUENTIAL);
        c.poemsJson = sp.getString(ConfigKeys.POEMS_JSON, "");
        c.poemsSeeded = sp.getBoolean(ConfigKeys.POEMS_SEEDED, false);
        c.holesJson = sp.getString(ConfigKeys.HOLES_JSON, "");
        c.configRevision = sp.getLong(ConfigKeys.CONFIG_REVISION, 0L);
        c.whitelist = new ArrayList<>();
        String wl = sp.getString(ConfigKeys.WHITELIST, "");
        if (wl != null && !wl.isEmpty()) {
            c.whitelist = ConfigCodec.parseWhitelist(wl);
        }
        if (!c.poemsSeeded) {
            c.poemsJson = PoemLibrary.defaultsJson();
            c.poemsSeeded = true;
            save(ctx, c, false);
        }

        c.whitelistSeeded = sp.getBoolean(ConfigKeys.WHITELIST_SEEDED, false);
        if (!c.whitelistSeeded) {
            c.whitelist = new ArrayList<>(Arrays.asList(PoemConfig.WHITELIST_DEFAULTS));
            c.whitelistSeeded = true;
            save(ctx, c, false);
        }
        return c.normalized();
    }

    public static void save(Context ctx, PoemConfig c, boolean bumpRevision) {
        if (c == null) {
            return;
        }
        c.normalized();
        if (bumpRevision) {
            c.configRevision = c.configRevision + 1;
        }
        SharedPreferences sp = prefs(ctx);
        SharedPreferences.Editor e = sp.edit();
        e.putBoolean(ConfigKeys.ENABLED, c.enabled);
        e.putInt(ConfigKeys.SPEED_PX, c.speedPx);
        e.putInt(ConfigKeys.CLOCK_SECONDS, c.clockSeconds);
        e.putBoolean(ConfigKeys.SHOW_AUTHOR, c.showAuthor);
        e.putBoolean(ConfigKeys.SHOW_SOURCE, c.showSource);
        e.putBoolean(ConfigKeys.BOB_ENABLED, c.bobEnabled);
        e.putBoolean(ConfigKeys.LANDSCAPE_PAUSE, c.landscapePause);
        e.putBoolean(ConfigKeys.LOCKSCREEN_PAUSE, c.lockscreenPause);
        e.putString(ConfigKeys.ORDER_MODE, c.orderMode);
        e.putString(ConfigKeys.WHITELIST, ConfigCodec.whitelistToText(c.whitelist));
        e.putBoolean(ConfigKeys.WHITELIST_SEEDED, c.whitelistSeeded);
        e.putString(ConfigKeys.POEMS_JSON, c.poemsJson == null ? "" : c.poemsJson);
        e.putBoolean(ConfigKeys.POEMS_SEEDED, c.poemsSeeded);
        e.putString(ConfigKeys.HOLES_JSON, c.holesJson == null ? "" : c.holesJson);
        e.putLong(ConfigKeys.CONFIG_REVISION, c.configRevision);
        e.apply();
    }

    public static String readJson(Context ctx) {
        return ConfigCodec.encode(load(ctx));
    }

    public static String dump(Context ctx) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, ?> e : prefs(ctx).getAll().entrySet()) {
            sb.append(e.getKey()).append('=').append(e.getValue()).append('\n');
        }
        return sb.toString();
    }

    public static void notifyConfigChanged(Context ctx) {
        try {
            ctx.getContentResolver().notifyChange(
                    android.net.Uri.parse("content://" + FeatureAudit.PROVIDER_AUTHORITY + "/config"), null);
        } catch (Throwable ignored) {

        }
    }

    public static List<String> whitelist(Context ctx) {
        String raw = prefs(ctx).getString(ConfigKeys.WHITELIST, "");
        return ConfigCodec.parseWhitelist(raw);
    }
}
