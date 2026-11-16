package io.github.rude56.poemstatus;

/**
 * 帧节奏控制（纯逻辑）。
 *
 * <p>省电核心之一：不用「每个 vsync 都回调、再由自己丢掉多余的」，
 * 而是算出到下一个目标点还剩多少毫秒，交给
 * {@code Choreographer.postFrameCallbackDelayed} —— 120Hz 屏上主线程唤醒
 * 从 120 次/秒降到 30 次/秒。
 *
 * <p>第二个省电点：位移不足 {@link #MIN_DRAW_DELTA_PX} 时直接跳过这一帧，
 * 不绘制、不重排。
 *
 * <p>第三个省电点（{@link #intervalNsForSpeed}）：既然「位移不足 1px 不画」，
 * 那么回调节奏也不该固定 30fps —— 20px/s 时 1px 要走 50ms，
 * 每 33ms 醒一次纯属空转。按速度把间隔拉到「走 1px 所需时间」即可，
 * 高速时自然回落到 30fps 封顶。
 *
 * <p><b>为什么本类只剩一个静态函数</b>：这里曾经还有一套
 * {@code reset/advance/delayMs/shouldDraw} 的实例状态机，但真正跑在设备上的
 * 是 {@code PoemRuntime.OverlayView} 里按速度自适应的实现，那些实例方法
 * <b>在生产代码里零调用</b>，只有单元测试在用 —— 于是测试「覆盖」的是一段
 * 死代码，而真实行为没有任何测试盯着。那些方法已删除，测试改为直接覆盖
 * 真正被调用的纯函数。
 */
public final class FramePacer {

    public static final int TARGET_FPS = 30;
    public static final long TARGET_FRAME_NS = 1_000_000_000L / TARGET_FPS;
    /** 位移小于该值（设备像素）就不绘制。 */
    public static final float MIN_DRAW_DELTA_PX = 1f;
    /**
     * 自适应间隔的上限（100ms）。
     *
     * <p>再慢也要偶尔醒一次：保证「暂停 / 布局变化 / 掉帧」能被及时反映，
     * 也让极低速下的帧回调数不至于低到失去响应。
     */
    public static final long MAX_ADAPTIVE_INTERVAL_NS = 100_000_000L;

    private FramePacer() {
    }

    /**
     * 这一帧之后应该隔多久再要下一帧。
     *
     * <p>规则：走到 {@link #MIN_DRAW_DELTA_PX} 需要的时间，与
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
