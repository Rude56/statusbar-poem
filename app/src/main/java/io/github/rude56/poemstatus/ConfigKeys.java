package io.github.rude56.poemstatus;

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

    public static final String WHITELIST_SEEDED = "whitelistSeeded";
    public static final String POEMS_JSON = "poemsJson";
    public static final String POEMS_SEEDED = "poemsSeeded";

    public static final String HOLES_JSON = "holesJson";
    public static final String CONFIG_REVISION = "configRevision";
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
            WHITELIST_SEEDED,
            POEMS_JSON,
            POEMS_SEEDED,
            HOLES_JSON,
            CONFIG_REVISION,
    };

    public static final String PREFS_NAME = "poem_status_config";

    private ConfigKeys() {
    }
}
