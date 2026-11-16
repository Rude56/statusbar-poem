package io.github.rude56.poemstatus;

/** 帧节奏：按速度自适应回调间隔，低速时减少唤醒、高速时封顶 30fps。 */
public final class FramePacer {

    public static final int TARGET_FPS = 30;
    public static final long TARGET_FRAME_NS = 1_000_000_000L / TARGET_FPS;
    /** 位移小于该值（设备像素）就不绘制。 */
    public static final float MIN_DRAW_DELTA_PX = 1f;
    /**
     * 自适应间隔的上限（100ms）。
     *
     * 再慢也要偶尔醒一次：保证「暂停 / 布局变化 / 掉帧」能被及时反映，
     * 也让极低速下的帧回调数不至于低到失去响应。
     */
    public static final long MAX_ADAPTIVE_INTERVAL_NS = 100_000_000L;

    private FramePacer() {
    }

    /**
     * 这一帧之后应该隔多久再要下一帧。
     *
     * 规则：走到 {@link #MIN_DRAW_DELTA_PX} 需要的时间，与
     * {@link #TARGET_FRAME_NS} 取较大者（即「不高于 30fps」），
     * 再用 {@link #MAX_ADAPTIVE_INTERVAL_NS} 兜住下限频率。
     *
     * @param speedPx 滚动速度（px/s）；非正数按封顶帧率处理
     */
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
