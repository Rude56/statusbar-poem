package io.github.rude56.poemstatus;

import java.util.HashMap;
import java.util.Map;

/**
 * 极简日志门面：只写 logcat，绝不落盘、绝不联网。
 * 本项目常驻 SystemUI 进程，凡是「每首都会发生」的日志都必须经过 {@link #allow} 限频，避免持续写 logd。
 */
public final class Log {

    private static volatile boolean verbose = true;
    private static long lastAt;
    private static String lastMsg;

    /** 限频通道：key → 上次放行时刻。容量极小（只有个位数个 key）。 */
    private static final Map<String, Long> GATES = new HashMap<>();

    private Log() {
    }

    public static void setVerbose(boolean v) {
        verbose = v;
    }

    /** 关键生命周期：INJECTED / CONF apply / POEM start / clock hold / paused。 */
    public static void i(String msg) {
        android.util.Log.i(FeatureAudit.TAG, msg);
        remember(msg);
    }

    /** 低频诊断：同一条内容 1 秒内只打一次，避免刷屏与耗电。 */
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

    /**
     * 限频闸门：同一个 key 每 {@code minIntervalMs} 毫秒最多放行一次。
     * 刻意拆成「先问再拼字符串」两步，被挡下时连字符串都不会拼、不分配 StringBuilder。
     *
     * @return true 表示这次可以打
     */
    public static boolean allow(String key, long minIntervalMs) {
        long now = android.os.SystemClock.elapsedRealtime();
        Long prev = GATES.get(key);
        if (prev != null && now - prev < minIntervalMs) {
            return false;
        }
        GATES.put(key, now);
        return true;
    }

    /** 清掉某个闸门（配置变化、重新开始时用，保证下一次一定打得出来）。 */
    public static void resetGate(String key) {
        GATES.remove(key);
    }

    private static void remember(String msg) {
        lastMsg = msg;
        lastAt = android.os.SystemClock.elapsedRealtime();
    }
}
