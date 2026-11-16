package io.github.rude56.poemstatus;

/**
 * 功能隔离 + 熔断（纯逻辑）。
 *
 * <p>SystemUI 是常驻进程：任何一次未捕获异常都可能导致「状态栏反复消失」。
 * 所有可能抛异常的特性都走 {@link #guard}，连续失败 {@link #MAX_FAIL} 次后熔断，
 * 熔断时调用方必须恢复系统栏（{@code restoreModules()}）。
 *
 * <p><b>熔断必须能自愈</b>：这个类以前一旦 {@code disabled} 就<b>永久</b>禁用，
 * 而 {@code reset()} / {@code resetAll()} 在生产代码里一次都没被调用过。
 * 后果是「遮挡偶发抛了 3 次异常 → 这个进程内遮挡永远失效」，
 * 而且完全静默：诗句照常滚，只是再也不遮状态栏，logcat 里没有任何线索，
 * 只能靠重启 SystemUI 恢复。
 *
 * <p>现在：熔断时打一条日志；此后每成功执行 {@link #MAX_FAIL} 次
 * 就自动放行一次「试探」（见 {@link #MAX_FAIL}），成功即复位计数。
 * 试探频率很低（每 N 次调用一次），失败也只是继续熔断，不影响功耗。
 */
public final class FeatureGuard {

    public static final int MAX_FAIL = 3;

    private final java.util.HashMap<String, Integer> fails = new java.util.HashMap<>();
    private final java.util.HashSet<String> disabled = new java.util.HashSet<>();
    /** 熔断后的「试探」计数：每累积到 {@link #MAX_FAIL} 次调用放行一次。 */
    private final java.util.HashMap<String, Integer> probes = new java.util.HashMap<>();

    /**
     * 报告接口。
     *
     * <p><b>为什么不直接调 {@link Log}</b>：本类是纯逻辑，必须能在 JVM 单元测试里
     * 直接 new 出来跑（{@code android.util.Log} 在本地单测里是「未 mock」的，
     * 一调就抛 RuntimeException）。所以日志走注入，测试里不注入就是静默，
     * 设备上由 {@code SystemUiController} 注入真实实现。
     */
    interface Reporter {
        void report(String message);
    }

    private Reporter reporter;

    void setReporter(Reporter r) {
        reporter = r;
    }

    private void report(String message) {
        Reporter r = reporter;
        if (r != null) {
            r.report(message);
        }
    }

    public interface Body {
        void run() throws Throwable;
    }

    /** @return true 表示这次执行成功 */
    public boolean guard(String id, Body body) {
        if (!admit(id)) {
            return false;
        }
        try {
            body.run();
            if (probeGranted) {
                probeGranted = false;
                reset(id);
                report("POEM guard recovered id=" + id);
            }
            fails.remove(id);
            return true;
        } catch (Throwable t) {
            probeGranted = false;
            return recordFailure(id);
        }
    }

    /**
     * 帧内快路径：语义与 {@link #guard} 一致，但成功路径上<b>不碰任何 Map</b>。
     *
     * <p>为什么需要它：{@link #guard} 成功时要 {@code fails.remove(id)}，
     * 这是一次 HashMap 写入级别的操作。它每帧被调用（30 次/秒）时是纯浪费。
     * 这里成功就直接返回，只有真的抛异常才去计数。
     *
     * <p>注意：调用方必须传入<b>复用的</b> Body 实例，否则参数里的 lambda
     * 仍然每帧分配一个对象 —— 夹具在 {@code SystemUiController}。
     */
    public boolean guardFast(String id, Body body) {
        // 未熔断时零 Map 访问：这是帧内的正常路径。
        if (!disabled.isEmpty() && !admit(id)) {
            return false;
        }
        try {
            body.run();
            // 试探成功 = 故障已经消失：这里才解除熔断。
            //
            // 注意不能「一放行就复位」：那样试探失败(仍在熔断中)也会被当成
            // 恢复，熔断形同虚设，失败态会被反复执行。
            if (probeGranted) {
                probeGranted = false;
                reset(id);
                report("POEM guard recovered id=" + id);
            }
            return true;
        } catch (Throwable t) {
            probeGranted = false;
            return recordFailure(id);
        }
    }

    /** 这一轮是否是「试探」放行（成功即解除熔断）。 */
    private boolean probeGranted;

    /**
     * 这一轮是否允许执行。
     *
     * <p>未熔断 → 直接放行（常态，帧内不碰 Map）。
     * 已熔断 → 每 {@link #MAX_FAIL} 次调用放行一次试探，其余直接拒绝。
     */
    private boolean admit(String id) {
        if (!disabled.contains(id)) {
            return true;
        }
        Integer n = probes.get(id);
        int next = (n == null ? 0 : n) + 1;
        if (next < MAX_FAIL) {
            probes.put(id, next);
            return false;
        }
        probes.put(id, 0);
        probeGranted = true;
        report("POEM guard probe id=" + id);
        return true;
    }

    private boolean recordFailure(String id) {
        int n = fails.getOrDefault(id, 0) + 1;
        fails.put(id, n);
        // 试探失败也算一次失败，但不要重复刷熔断日志。
        boolean wasDisabled = disabled.contains(id);
        if (n >= MAX_FAIL && !wasDisabled) {
            disabled.add(id);
            report("POEM guard fused id=" + id + " after " + n + " failures");
        }
        if (n >= MAX_FAIL) {
            disabled.add(id);
        }
        return false;
    }

    public boolean isDisabled(String id) {
        return disabled.contains(id);
    }

    public int failCount(String id) {
        return fails.getOrDefault(id, 0);
    }

    public void reset(String id) {
        disabled.remove(id);
        fails.remove(id);
        probes.remove(id);
    }

    public void resetAll() {
        disabled.clear();
        fails.clear();
        probes.clear();
    }

    public java.util.Set<String> disabledIds() {
        return disabled;
    }
}
