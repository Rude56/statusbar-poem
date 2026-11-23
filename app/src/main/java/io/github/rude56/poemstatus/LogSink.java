package io.github.rude56.poemstatus;

/**
 * 调试日志回传通道。
 *
 * <p>SystemUI 跑在 system_app 域，写不了 /data/local/tmp（实测 FileNotFoundException），
 * 也没有任何能被普通 App 读到的目录。所以日志走已经存在的 ContentProvider：
 * SystemUI 把文本 call 过去，provider 在模块自己的进程里写进 filesDir。
 *
 * <p>这样「导出日志」不需要 root，也不需要用户做任何文件操作。
 */
final class LogSink {
    private static final long MIN_INTERVAL_MS = 2000L;
    private static android.content.Context context;
    private static long lastPushAt;
    private static String lastPushed;

    private LogSink() {
    }

    static void init(android.content.Context ctx) {
        if (ctx != null) {
            context = ctx.getApplicationContext() == null ? ctx : ctx.getApplicationContext();
        }
    }

    /** 把当前环形缓冲推给模块进程；调试关闭时什么都不做。 */
    static void flushIfDebug() {
        if (!Log.debug()) {
            return;
        }
        android.content.Context ctx = context;
        if (ctx == null) {
            return;
        }
        long now = android.os.SystemClock.elapsedRealtime();
        if (now - lastPushAt < MIN_INTERVAL_MS) {
            return;
        }
        String text = Log.dump();
        if (text.isEmpty() || text.equals(lastPushed)) {
            return;
        }
        lastPushAt = now;
        lastPushed = text;
        try {
            android.os.Bundle extras = new android.os.Bundle();
            extras.putString("token", FeatureAudit.PROVIDER_TOKEN);
            extras.putString("text", text);
            ctx.getContentResolver().call(
                    android.net.Uri.parse("content://" + FeatureAudit.PROVIDER_AUTHORITY),
                    FeatureAudit.PROVIDER_LOG_METHOD, null, extras);
        } catch (Throwable t) {
            android.util.Log.w(FeatureAudit.TAG, "POEM log push failed: "
                    + t.getClass().getSimpleName());
        }
    }

    /** 清掉限频状态，下次立即回传。 */
    static void reset() {
        lastPushAt = 0L;
        lastPushed = null;
    }
}
