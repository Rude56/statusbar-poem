package io.github.rude56.poemstatus;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

/**
 * 只读配置通道。
 *
 * <p>SystemUI 进程不能读本 App 的私有目录，也不能联网，因此配置通过
 * {@link #call(String, String, Bundle)} 返回一段 JSON 文本。
 *
 * <p>安全：导出的 provider 只接受带 {@link FeatureAudit#PROVIDER_TOKEN} 的调用；
 * 不带 token 直接返回 null。provider 内部没有任何写入路径。
 */
public final class ConfigProvider extends ContentProvider {

    /**
     * 最后一次被 <b>SystemUI 进程</b>调用的时刻（{@code elapsedRealtime}）。
     *
     * <p>为什么用它来判断「LSPosed 到底有没有生效」：
     * 注入成功的 SystemUI 每 2 秒会来读一次配置，所以这个时间戳会持续刷新；
     * 如果模块没启用 / LSPosed 没注入 / 作用域没勾，就<b>永远不会有人来调</b>，
     * 时间戳会一直变旧。这是唯一一个不需要额外权限、也不需要跨进程写文件的
     * 「模块是否真的在跑」信号。
     *
     * <p>0 表示本次 App 进程启动以来还没被调用过。
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
        // 只记录「调用方确实是 SystemUI」的那些，避免 App 自己的诊断调用
        // 把连接状态误判成「已连接」。
        try {
            if (FeatureAudit.SCOPE_PACKAGE.equals(getCallingPackage())) {
                lastSystemUiCallAt = android.os.SystemClock.elapsedRealtime();
            }
        } catch (Throwable ignored) {
            // 拿不到调用方包名时不计入，宁可显示「未连接」也不要误报。
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
