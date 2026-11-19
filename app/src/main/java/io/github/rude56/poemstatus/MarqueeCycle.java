package io.github.rude56.poemstatus;

public final class MarqueeCycle {
    public static final int GAP_SPACES = 10;
    public static final long PAUSE_MILLIS = 1000L;

    private MarqueeCycle() { }

    public static String text(String poem) {
        return (poem == null ? "" : poem) + " ".repeat(GAP_SPACES);
    }

    public static float advance(float travelled, float cycleWidth, int speedPxPerSecond, float elapsedMs) {
        if (cycleWidth <= 0f) return 0f;
        float position = Math.max(0f, Math.min(travelled, cycleWidth));
        float dt = Math.max(0f, Math.min(elapsedMs, 100f));
        return Math.min(cycleWidth, position + Math.max(0, speedPxPerSecond) * dt / 1000f);
    }
}
