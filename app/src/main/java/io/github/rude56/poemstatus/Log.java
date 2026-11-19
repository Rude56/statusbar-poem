package io.github.rude56.poemstatus;

import java.util.HashMap;
import java.util.Map;

public final class Log {
    private static volatile boolean verbose = true;
    private static long lastAt;
    private static String lastMsg;
    private static final Map<String, Long> GATES = new HashMap<>();

    private Log() {
    }

    public static void setVerbose(boolean v) {
        verbose = v;
    }

    public static void i(String msg) {
        android.util.Log.i(FeatureAudit.TAG, msg);
        remember(msg);
    }

    public static void d(String msg) {
        if (!verbose) {
            return;
        }
        long now = android.os.SystemClock.elapsedRealtime();
        if (msg != null && msg.equals(lastMsg) && now - lastAt < 1000L) {
            return;
        }
        lastAt = now;
        remember(msg);
        android.util.Log.d(FeatureAudit.TAG, msg);
    }

    public static void w(String msg) {
        android.util.Log.w(FeatureAudit.TAG, msg);
    }

    public static boolean allow(String key, long minIntervalMs) {
        long now = android.os.SystemClock.elapsedRealtime();
        Long prev = GATES.get(key);
        if (prev != null && now - prev < minIntervalMs) {
            return false;
        }
        GATES.put(key, now);
        return true;
    }
    public static void resetGate(String key) {
        GATES.remove(key);
    }
    private static void remember(String msg) {
        lastMsg = msg;
        lastAt = android.os.SystemClock.elapsedRealtime();
    }
}
