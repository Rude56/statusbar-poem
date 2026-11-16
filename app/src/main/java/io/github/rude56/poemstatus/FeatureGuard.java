package io.github.rude56.poemstatus;

/**
 * 功能隔离 + 熔断（纯逻辑）：可能抛异常的特性都走 {@link #guard}，连续失败 {@link #MAX_FAIL} 次后熔断，
 * 熔断时调用方必须恢复系统栏；熔断后每 {@link #MAX_FAIL} 次调用放行一次试探，成功即复位。
 */
public final class FeatureGuard {

    public static final int MAX_FAIL = 3;

    private final java.util.HashMap<String, Integer> fails = new java.util.HashMap<>();
    private final java.util.HashSet<String> disabled = new java.util.HashSet<>();
    /** 熔断后的「试探」计数：每累积到 {@link #MAX_FAIL} 次调用放行一次。 */
    private final java.util.HashMap<String, Integer> probes = new java.util.HashMap<>();

    /** 报告接口：日志走注入，本类才能在 JVM 单测里直接 new（{@code android.util.Log} 本地一调就抛）。 */
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

    /** 执行一次受保护逻辑；true 表示这次执行成功。 */
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
     * 帧内快路径：语义与 {@link #guard} 一致，但成功路径上不碰任何 Map；
     * 调用方必须传入复用的 Body 实例，否则 lambda 仍会每帧分配一个对象。
     */
    public boolean guardFast(String id, Body body) {
        // 未熔断时零 Map 访问：这是帧内的正常路径。
        if (!disabled.isEmpty() && !admit(id)) {
            return false;
        }
        try {
            body.run();
            // 只有试探成功才复位：若在放行时就复位，失败态会被反复执行，熔断形同虚设。
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

    /** 这一轮是否允许执行：未熔断直接放行（帧内不碰 Map），已熔断则每 {@link #MAX_FAIL} 次放行一次试探。 */
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
