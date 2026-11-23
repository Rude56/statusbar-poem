package io.github.rude56.poemstatus;

import java.util.HashMap;
import java.util.Map;

public final class Log {
    private static volatile boolean verbose = true;
    private static volatile boolean debug;
    private static long lastAt;
    private static String lastMsg;
    private static final Map<String, Long> GATES = new HashMap<>();

    private static final int RING = 500;
    private static final String[] RING_BUF = new String[RING];
    private static int ringHead;
    private static int ringCount;
    private static final Object RING_LOCK = new Object();

    private Log() {
    }

    public static void setVerbose(boolean v) {
        verbose = v;
    }

    public static void setDebug(boolean v) {
        if (debug != v) {
            debug = v;
            GATES.clear();
            android.util.Log.i(FeatureAudit.TAG, "POEM debugLog=" + v);
        }
    }

    public static boolean debug() {
        return debug;
    }

    /** 关键生命周期日志：始终输出（调用方自行用 {@link #allow} 限频）。 */
    public static void i(String msg) {
        android.util.Log.i(FeatureAudit.TAG, msg);
        remember(msg);
        ring(msg);
    }

    /** 诊断日志：仅调试开关打开时输出，关闭时零开销。 */
    public static void diag(String msg) {
        if (!debug) {
            return;
        }
        android.util.Log.i(FeatureAudit.TAG, msg);
        remember(msg);
        ring(msg);
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
        ring(msg);
        android.util.Log.d(FeatureAudit.TAG, msg);
    }

    public static void w(String msg) {
        android.util.Log.w(FeatureAudit.TAG, msg);
        ring("W " + msg);
    }

    public static boolean allow(String key, long minIntervalMs) {
        if (debug) {
            return true;
        }
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

    /** 导出最近日志（按时间正序，单行一条）。 */
    public static String dump() {
        synchronized (RING_LOCK) {
            StringBuilder sb = new StringBuilder();
            int start = (ringHead - ringCount + RING) % RING;
            for (int i = 0; i < ringCount; i++) {
                String line = RING_BUF[(start + i) % RING];
                if (line != null) {
                    sb.append(line).append('\n');
                }
            }
            return sb.toString();
        }
    }

    private static long lastFlushAt;

    /** 调试模式下把环形缓冲推给模块进程落盘，供「导出日志」使用。 */
    static void flushIfDebug() {
        if (!debug) {
            return;
        }
        long now = android.os.SystemClock.elapsedRealtime();
        if (now - lastFlushAt < 2000L) {
            return;
        }
        lastFlushAt = now;
        LogSink.flushIfDebug();
    }

    public static void clearRing() {
        synchronized (RING_LOCK) {
            ringHead = 0;
            ringCount = 0;
            for (int i = 0; i < RING; i++) {
                RING_BUF[i] = null;
            }
        }
    }

    private static void ring(String msg) {
        if (msg == null) {
            return;
        }
        long ms = android.os.SystemClock.elapsedRealtime();
        String line = formatTime(ms) + " " + msg;
        synchronized (RING_LOCK) {
            RING_BUF[ringHead] = line;
            ringHead = (ringHead + 1) % RING;
            if (ringCount < RING) {
                ringCount++;
            }
        }
    }

    private static String formatTime(long elapsedMs) {
        long sec = elapsedMs / 1000L;
        long h = (sec / 3600L) % 24L;
        long m = (sec / 60L) % 60L;
        long s = sec % 60L;
        long ms = elapsedMs % 1000L;
        return pad(h) + ":" + pad(m) + ":" + pad(s) + "." + pad3(ms);
    }

    private static String pad(long v) {
        return v < 10 ? "0" + v : String.valueOf(v);
    }

    private static String pad3(long v) {
        if (v < 10) {
            return "00" + v;
        }
        if (v < 100) {
            return "0" + v;
        }
        return String.valueOf(v);
    }

    private static void remember(String msg) {
        lastMsg = msg;
        lastAt = android.os.SystemClock.elapsedRealtime();
    }
}
