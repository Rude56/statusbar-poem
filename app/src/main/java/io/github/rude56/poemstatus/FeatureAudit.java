package io.github.rude56.poemstatus;

public final class FeatureAudit {
    public static final String ENTRY_TITLE = "古诗虫";
    public static final String LIBRARY_SCREEN_TITLE = "古诗库";

    public static final String SCOPE_PACKAGE = "com.android.systemui";

    public static final String SELF_PACKAGE = "io.github.rude56.poemstatus";

    public static final String PERMISSION_INTERNET = "android.permission.INTERNET";

    public static final String PROVIDER_AUTHORITY = "io.github.rude56.poemstatus.config";
    public static final String PROVIDER_METHOD = "poem_get_config";
    public static final String PROVIDER_TOKEN = "poem-status-hook-v1";

    public static final String TAG = "PoemStatus";

    private FeatureAudit() {
    }
}
