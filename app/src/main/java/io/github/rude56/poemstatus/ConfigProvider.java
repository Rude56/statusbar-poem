package io.github.rude56.poemstatus;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

public final class ConfigProvider extends ContentProvider {
    private static volatile long lastSystemUiCallAt = 0L;
    public static long lastSystemUiCallAt() {
        return lastSystemUiCallAt;
    }

    @Override
    public boolean onCreate() {
        return true;
    }
    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if (FeatureAudit.PROVIDER_LOG_METHOD.equals(method)) {
            return acceptLog(extras);
        }
        if (!FeatureAudit.PROVIDER_METHOD.equals(method)) {
            return null;
        }
        if (extras == null || !FeatureAudit.PROVIDER_TOKEN.equals(extras.getString("token"))) {
            return null;
        }
        if (getContext() == null) {
            return null;
        }

        try {
            if (FeatureAudit.SCOPE_PACKAGE.equals(getCallingPackage())) {
                lastSystemUiCallAt = android.os.SystemClock.elapsedRealtime();
            }
        } catch (Throwable ignored) {

        }
        String json;
        try {
            json = ConfigStore.readJson(getContext());
        } catch (Throwable t) {
            return null;
        }
        Bundle out = new Bundle();
        out.putString("json", json);
        return out;
    }

    /**
     * 接收 SystemUI 推来的调试日志并落盘。
     *
     * <p>只接受 SystemUI 与 root/system 调用，避免第三方 App 把日志刷爆。
     */
    private Bundle acceptLog(Bundle extras) {
        if (extras == null || !FeatureAudit.PROVIDER_TOKEN.equals(extras.getString("token"))) {
            return null;
        }
        android.content.Context ctx = getContext();
        if (ctx == null) {
            return null;
        }
        if (!isTrustedCaller()) {
            return null;
        }
        String text = extras.getString("text");
        if (text == null || text.isEmpty()) {
            return null;
        }
        lastLogAt = android.os.SystemClock.elapsedRealtime();
        lastLogText = text;
        try {
            java.io.File f = logFile(ctx);
            java.io.File parent = f.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            java.io.FileWriter w = new java.io.FileWriter(f, false);
            w.write(text);
            w.close();
        } catch (Throwable ignored) {

        }
        return null;
    }

    private boolean isTrustedCaller() {
        try {
            String pkg = getCallingPackage();
            if (FeatureAudit.SCOPE_PACKAGE.equals(pkg)) {
                return true;
            }
        } catch (Throwable ignored) {

        }
        try {
            Class<?> b = Class.forName("android.os.Binder");
            int uid = (Integer) b.getMethod("getCallingUid").invoke(null);
            return uid == android.os.Process.SYSTEM_UID || uid == 0;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** 日志文件位置：模块自己的 filesDir，卸载即清。 */
    public static java.io.File logFile(android.content.Context ctx) {
        return new java.io.File(ctx.getFilesDir(), "poem_debug.log");
    }

    private static volatile long lastLogAt;
    private static volatile String lastLogText;

    /** 最近一次收到的日志（进程内直接读，供导出）。 */
    public static String lastLogText() {
        return lastLogText;
    }

    public static long lastLogAt() {
        return lastLogAt;
    }
    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        return null;
    }
    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
