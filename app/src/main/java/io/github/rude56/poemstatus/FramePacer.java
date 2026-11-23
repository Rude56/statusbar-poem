package io.github.rude56.poemstatus;

public final class FramePacer {
    public static final int MIN_FPS = 30;
    public static final int MAX_FPS = 60;
    public static final int TARGET_FPS = MAX_FPS;
    public static final long TARGET_FRAME_NS = 1_000_000_000L / TARGET_FPS;
    public static final float MIN_DRAW_DELTA_PX = 1f;
    public static final long MAX_ADAPTIVE_INTERVAL_NS = 100_000_000L;
    public static final int PX_PER_FRAME = 8;
    private FramePacer() {
    }
    public static long intervalNsForSpeed(int speedPx) {
        return 1_000_000_000L / fpsForSpeed(speedPx);
    }

    public static int fpsForSpeed(int speedPx) {
        if (speedPx <= 0) {
            return MIN_FPS;
        }
        int fps = speedPx / PX_PER_FRAME;
        if (fps < MIN_FPS) {
            return MIN_FPS;
        }
        return Math.min(fps, MAX_FPS);
    }
}
