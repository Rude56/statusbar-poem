package io.github.rude56.poemstatus;

import java.util.ArrayList;
import java.util.List;

/**
 * 模块配置。读写两侧共用同一个类，保证字段与 {@link ConfigKeys} 一一对应。
 */
public final class PoemConfig {

    public static final int SPEED_MIN = 20;
    public static final int SPEED_MAX = 600;
    public static final int SPEED_DEFAULT = 120;
    public static final int CLOCK_MIN = 0;
    public static final int CLOCK_MAX = 600;
    public static final int CLOCK_DEFAULT = 10;

    /** 插件总开关，默认关闭：必须由用户在设置页手动开启。 */
    public boolean enabled = false;
    public int speedPx = SPEED_DEFAULT;
    public int clockSeconds = CLOCK_DEFAULT;
    public boolean showAuthor = true;
    public boolean showSource = true;
    public boolean bobEnabled = false;
    public boolean landscapePause = true;
    /** 锁屏时暂停：锁屏上通常是通知与时间，滚字既不美观也容易分心。 */
    public boolean lockscreenPause = true;
    public String orderMode = PoemLibrary.ORDER_SEQUENTIAL;
    public List<String> whitelist = new ArrayList<>();
    public String poemsJson = "";
    /** 是否已经写过内置诗（一旦为 true，升级永不恢复内置诗）。 */
    public boolean poemsSeeded = false;
    /** 自定义洞列表（可多个），高度恒为状态栏高度。 */
    public String holesJson = "";
    /** 虫洞模式（跳跃）/ 隧道模式（连续）。 */
    public String holeMode = HoleRects.MODE_WORMHOLE;
    public long configRevision = 0L;

    /** 当前生效的洞列表（解析失败时返回空表，不影响播放）。 */
    public java.util.List<HoleRects.Hole> holes() {
        return HoleRects.parse(encodeHoles());
    }

    /**
     * 把 {@code holesJson} 归一成存储格式 {@code "l:r:mode:slot,..."}，存储格式与 JSON 数组都接受。
     * JSON 分支必须走 {@link HoleRects#encode}：否则模式与颜色槽位会丢失（隧道洞读回变成虫洞、颜色重排）。
     */
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

    /** 夹取越界值：Provider 里可能拿到被手改过的 prefs，不能让它跑出边界。 */
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
        holeMode = HoleRects.modeName(HoleRects.parseMode(holeMode));
        return this;
    }

    /** 这个包名是否在白名单里：匹配忽略首尾空白与大小写（白名单可能是手输/粘贴进来的）。 */
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

    /** 深拷贝：新增字段时只改这一处，漏改会立刻在编译期暴露。 */
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
        c.poemsJson = poemsJson;
        c.poemsSeeded = poemsSeeded;
        c.holesJson = holesJson;
        c.holeMode = holeMode;
        c.configRevision = configRevision;
        return c;
    }

    public static int clamp(int v, int min, int max) {
        return v < min ? min : (v > max ? max : v);
    }
}
