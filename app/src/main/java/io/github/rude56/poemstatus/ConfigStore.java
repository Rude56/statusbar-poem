package io.github.rude56.poemstatus;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * App 侧配置存储（SharedPreferences）。
 * 只被本应用进程使用；SystemUI 进程绝不直接读这里的文件，只能通过 {@link ConfigProvider} 拿 JSON。
 */
public final class ConfigStore {

    private ConfigStore() {
    }

    public static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(ConfigKeys.PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** 读取配置；首次使用时写入内置诗库并置 seeded=true。 */
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
        c.holeMode = sp.getString(ConfigKeys.HOLE_MODE, HoleRects.MODE_WORMHOLE);
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
        return c.normalized();
    }

    /** 保存并递增 configRevision；bumpRevision=false 用于首次初始化写入。 */
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
        e.putString(ConfigKeys.POEMS_JSON, c.poemsJson == null ? "" : c.poemsJson);
        e.putBoolean(ConfigKeys.POEMS_SEEDED, c.poemsSeeded);
        e.putString(ConfigKeys.HOLES_JSON, c.holesJson == null ? "" : c.holesJson);
        e.putString(ConfigKeys.HOLE_MODE, c.holeMode);
        e.putLong(ConfigKeys.CONFIG_REVISION, c.configRevision);
        e.apply();
    }

    /** Provider 用：把当前 prefs 原样拼成 JSON（不触发任何写操作）。 */
    public static String readJson(Context ctx) {
        return ConfigCodec.encode(load(ctx));
    }

    /** 供 UI 展示：所有键的当前值（诊断用）。 */
    public static String dump(Context ctx) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, ?> e : prefs(ctx).getAll().entrySet()) {
            sb.append(e.getKey()).append('=').append(e.getValue()).append('\n');
        }
        return sb.toString();
    }

    /** 保存后通知 SystemUI 侧立即重读（零轮询生效）。 */
    public static void notifyConfigChanged(Context ctx) {
        try {
            ctx.getContentResolver().notifyChange(
                    android.net.Uri.parse("content://" + FeatureAudit.PROVIDER_AUTHORITY + "/config"), null);
        } catch (Throwable ignored) {
        }
    }

    /** 白名单列表 → 可直接遍历的 List。 */
    public static List<String> whitelist(Context ctx) {
        String raw = prefs(ctx).getString(ConfigKeys.WHITELIST, "");
        return ConfigCodec.parseWhitelist(raw);
    }
}

