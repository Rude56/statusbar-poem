package io.github.rude56.poemstatus;

public final class MarqueeCycle {
    /** 循环衔接用：文字尾巴补 10 个空格，滚完后再从头进入不会撞在一起。 */
    public static final int GAP_SPACES = 10;
    /** 走完一轮后停 1 秒再从头开始。 */
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
