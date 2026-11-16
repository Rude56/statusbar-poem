package io.github.rude56.poemstatus;

/**
 * 结构性事实常量：入口文案、二级页标题、LSPosed 作用域、权限。
 * 这些值同时被 UI、Manifest、resources 引用，也由 JVM 测试交叉校验，不得随意改动。
 */
public final class FeatureAudit {

    /** 主页上的入口名称。 */
    public static final String ENTRY_TITLE = "古诗虫";
    /** 二级页面（诗库）标题。 */
    public static final String LIBRARY_SCREEN_TITLE = "古诗库";
    /** LSPosed 作用域：只允许 SystemUI。 */
    public static final String SCOPE_PACKAGE = "com.android.systemui";
    /** 本模块自己的包名（用于判断「设置页是否在前台」）。 */
    public static final String SELF_PACKAGE = "io.github.rude56.poemstatus";
    /** 本模块不申请任何联网权限。 */
    public static final String PERMISSION_INTERNET = "android.permission.INTERNET";

    public static final String PROVIDER_AUTHORITY = "io.github.rude56.poemstatus.config";
    public static final String PROVIDER_METHOD = "poem_get_config";
    /** Provider token：避免任意第三方 App 通过 call() 读走配置全文。 */
    public static final String PROVIDER_TOKEN = "poem-status-hook-v1";

    public static final String TAG = "PoemStatus";

    private FeatureAudit() {
    }
}
