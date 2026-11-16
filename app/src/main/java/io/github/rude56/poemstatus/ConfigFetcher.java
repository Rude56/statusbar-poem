package io.github.rude56.poemstatus;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 从本模块的只读 Provider 读取配置 JSON：{@code ContentResolver.call()} 是跨进程同步 Binder 调用，不能阻塞主线程，
 * 故放到常驻单线程执行器（一次只跑一个请求），结果回主线程；失败返回 null，调用方保留上一份有效配置。
 */
final class ConfigFetcher {

    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "poem-config-fetch");
        t.setDaemon(true);
        return t;
    });

    interface Callback {
        /** 在主线程回调；null 表示这次没拿到配置。 */
        void onResult(String json);
    }

    private static final AtomicBoolean IN_FLIGHT = new AtomicBoolean(false);

    /** 主线程 Handler：只建一次，避免每次轮询都分配一个对象。 */
    private static final android.os.Handler MAIN =
            new android.os.Handler(android.os.Looper.getMainLooper());

    private ConfigFetcher() {
    }

    static void fetchAsync(Context ctx, Callback cb) {
        if (ctx == null) {
            cb.onResult(null);
            return;
        }
        if (!IN_FLIGHT.compareAndSet(false, true)) {
            cb.onResult(null);
            return;
        }
        final Context appContext = ctx.getApplicationContext() == null ? ctx : ctx.getApplicationContext();
        try {
            IO.execute(() -> {
                String json = null;
                try {
                    json = fetchBlocking(appContext);
                } catch (Throwable t) {
                    Log.w("CONF fetch failed: " + t.getClass().getSimpleName());
                } finally {
                    IN_FLIGHT.set(false);
                }
                final String result = json;
                MAIN.post(() -> cb.onResult(result));
            });
        } catch (Throwable t) {
            IN_FLIGHT.set(false);
            cb.onResult(null);
        }
    }

    private static String fetchBlocking(Context ctx) {
        ContentResolver cr = ctx.getContentResolver();
        if (cr == null) {
            return null;
        }
        Bundle extras = new Bundle();
        extras.putString("token", FeatureAudit.PROVIDER_TOKEN);
        Bundle out = cr.call(Uri.parse("content://" + FeatureAudit.PROVIDER_AUTHORITY),
                FeatureAudit.PROVIDER_METHOD, null, extras);
        return out == null ? null : out.getString("json");
    }
}
