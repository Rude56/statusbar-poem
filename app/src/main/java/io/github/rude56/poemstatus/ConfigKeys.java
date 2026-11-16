package io.github.rude56.poemstatus;

/**
 * 配置键的唯一定义处。
 *
 * <p>App 侧写 SharedPreferences、Hook 侧读 Provider，两边必须引用同一组常量，
 * 否则会出现「设置保存了但模块读不到」这种最难查的问题。{@link #ALL} 同时被
 * {@link ConfigCodec} 与单元测试用来做一一对应校验。
 */
public final class ConfigKeys {

    public static final String ENABLED = "enabled";
    public static final String SPEED_PX = "speedPx";
    public static final String CLOCK_SECONDS = "clockSeconds";
    public static final String SHOW_AUTHOR = "showAuthor";
    public static final String SHOW_SOURCE = "showSource";
    public static final String BOB_ENABLED = "bobEnabled";
    public static final String LANDSCAPE_PAUSE = "landscapePause";
    public static final String LOCKSCREEN_PAUSE = "lockscreenPause";
    public static final String ORDER_MODE = "orderMode";
    public static final String WHITELIST = "whitelist";
    public static final String POEMS_JSON = "poemsJson";
    public static final String POEMS_SEEDED = "poemsSeeded";
    /** 自定义洞列表（"left:right,..."，高度恒为状态栏高度）。 */
    public static final String HOLES_JSON = "holesJson";
    /** 虫洞模式 / 隧道模式。 */
    public static final String HOLE_MODE = "holeMode";
    public static final String CONFIG_REVISION = "configRevision";

    /** 全部键，顺序与 {@link ConfigCodec} 输出的字段顺序一致。 */
    public static final String[] ALL = {
            ENABLED,
            SPEED_PX,
            CLOCK_SECONDS,
            SHOW_AUTHOR,
            SHOW_SOURCE,
            BOB_ENABLED,
            LANDSCAPE_PAUSE,
            LOCKSCREEN_PAUSE,
            ORDER_MODE,
            WHITELIST,
            POEMS_JSON,
            POEMS_SEEDED,
            HOLES_JSON,
            HOLE_MODE,
            CONFIG_REVISION,
    };

    public static final String PREFS_NAME = "poem_status_config";

    private ConfigKeys() {
    }
}
