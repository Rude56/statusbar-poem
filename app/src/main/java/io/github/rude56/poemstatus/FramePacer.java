package io.github.rude56.poemstatus;

public final class FramePacer {
    /** 上限 30fps：状态栏文字滚动不需要更高帧率。 */
    public static final int TARGET_FPS = 30;
    public static final long TARGET_FRAME_NS = 1_000_000_000L / TARGET_FPS;
    /** 位移不足 1px 不画，避免慢速时做无用重绘。 */
    public static final float MIN_DRAW_DELTA_PX = 1f;
    public static final long MAX_ADAPTIVE_INTERVAL_NS = 100_000_000L;
    private FramePacer() {
    }
    public static long intervalNsForSpeed(int speedPx) {
        if (speedPx <= 0) {
            return TARGET_FRAME_NS;
        }
        long bySpeed = (long) (MIN_DRAW_DELTA_PX * 1_000_000_000.0 / speedPx);
        if (bySpeed < TARGET_FRAME_NS) {
            return TARGET_FRAME_NS;
        }
        return Math.min(bySpeed, MAX_ADAPTIVE_INTERVAL_NS);
    }
}
