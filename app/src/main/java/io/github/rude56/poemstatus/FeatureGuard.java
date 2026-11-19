package io.github.rude56.poemstatus;

public final class FeatureGuard {
    public static final int MAX_FAIL = 3;
    private final java.util.HashMap<String, Integer> fails = new java.util.HashMap<>();
    private final java.util.HashSet<String> disabled = new java.util.HashSet<>();
    private final java.util.HashMap<String, Integer> probes = new java.util.HashMap<>();
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

    public boolean guardFast(String id, Body body) {

        if (!disabled.isEmpty() && !admit(id)) {
            return false;
        }
        try {
            body.run();

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
    private boolean probeGranted;
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
