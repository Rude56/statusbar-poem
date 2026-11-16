package io.github.rude56.poemstatus;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

/**
 * 只读配置通道：SystemUI 进程不能读本 App 私有目录也不能联网，配置经 {@link #call} 返回一段 JSON。
 * 导出的 provider 只接受带 token {@code poem-status-hook-v1} 的调用，内部没有任何写入路径。
 */
public final class ConfigProvider extends ContentProvider {

    /**
     * 最后一次被 SystemUI 进程调用的时刻（{@code elapsedRealtime}），0 表示本次 App 进程启动以来还没被调用过。
     * 注入成功的 SystemUI 会周期性来读配置，时间戳持续刷新，是「模块是否真的在跑」的信号。
     */
    private static volatile long lastSystemUiCallAt = 0L;

    /** 供 UI 读取：最后一次 SystemUI 调用的时刻（elapsedRealtime），0 表示从未。 */
    public static long lastSystemUiCallAt() {
        return lastSystemUiCallAt;
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if (!FeatureAudit.PROVIDER_METHOD.equals(method)) {
            return null;
        }
        if (extras == null || !FeatureAudit.PROVIDER_TOKEN.equals(extras.getString("token"))) {
            return null;
        }
        if (getContext() == null) {
            return null;
        }
        // 只记录调用方确实是 SystemUI 的，避免 App 自己的诊断调用把连接状态误判成「已连接」。
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
