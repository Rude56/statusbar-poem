package io.github.rude56.poemstatus;

public final class FeatureAudit {
    public static final String ENTRY_TITLE = "古诗虫";
    public static final String LIBRARY_SCREEN_TITLE = "古诗库";

    /** 静态作用域：只允许 SystemUI 被注入。 */
    public static final String SCOPE_PACKAGE = "com.android.systemui";

    public static final String SELF_PACKAGE = "io.github.rude56.poemstatus";

    public static final String PERMISSION_INTERNET = "android.permission.INTERNET";

    /** 只读配置通道：SystemUI 通过 ContentResolver.call 读取，token 防止第三方读走配置。 */
    public static final String PROVIDER_AUTHORITY = "io.github.rude56.poemstatus.config";
    public static final String PROVIDER_METHOD = "poem_get_config";
    /** 调试日志回传：SystemUI 写不了磁盘，由模块进程落盘。 */
    public static final String PROVIDER_LOG_METHOD = "poem_push_log";
    public static final String PROVIDER_TOKEN = "poem-status-hook-v1";

    public static final String TAG = "PoemStatus";

    private FeatureAudit() {
    }
}
