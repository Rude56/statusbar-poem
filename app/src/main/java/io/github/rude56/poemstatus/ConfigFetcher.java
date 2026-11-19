package io.github.rude56.poemstatus;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

final class ConfigFetcher {
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "poem-config-fetch");
        t.setDaemon(true);
        return t;
    });
    interface Callback {

        void onResult(String json);
    }

    private static final AtomicBoolean IN_FLIGHT = new AtomicBoolean(false);

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
