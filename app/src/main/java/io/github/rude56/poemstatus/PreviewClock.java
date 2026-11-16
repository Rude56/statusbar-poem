package io.github.rude56.poemstatus;

/**
 * 洞页两个样例的共用时钟（纯逻辑，JVM 可测）：两行必须由同一进度驱动、走同一条轨道，否则逐帧对照失效。
 */
public final class PreviewClock {

    /** 一轮的时长。 */
    public static final long CYCLE_NANOS = 14_000_000_000L;

    private PreviewClock() {
    }

    /**
     * 当前进度，取值 {@code [0, 1)}。
     *
     * 用取模而不是夹取：夹取会在周期末尾冻结，取模则循环。
     *
     * @param elapsedNanos 从起点算起经过的时间（可以是负数或超过一个周期）
     */
    public static float progressAt(long elapsedNanos, long cycleNanos) {
        if (cycleNanos <= 0L) {
            return 0f;
        }
        long e = elapsedNanos % cycleNanos;
        if (e < 0L) {
            e += cycleNanos;
        }
        return (float) e / (float) cycleNanos;
    }

    /**
     * 一轮循环实际需要的进度跨度 —— 两行必须同时有字。
     *
     * 虫洞把整句往左瞬移一个洞宽，字在 {@code progress} 还差一点到 1 时
     * 就已全部离开屏幕，所以循环终点收缩到虫洞行的消失点，到点立刻重启。
     *
     * @param wormholeWidth 虫洞宽度（像素）；没有虫洞时传 0
     * @return 循环终点，取值 {@code (0, 1]}
     */
    public static float cycleEnd(float canvasWidth, float textWidth, float wormholeWidth) {
        if (wormholeWidth <= 0f) {
            return 1f;
        }
        float end = progressAtFinish(canvasWidth, textWidth, wormholeWidth);
        // 退化输入（文字极宽 / 洞极宽）时不要返回 0，否则循环会卡死不动。
        if (end <= 0.02f) {
            return 0.02f;
        }
        return Math.min(end, 1f);
    }

    /**
     * 给定进度下，诗句轨道左端的位置。
     *
     * 轨道总长 = 画布宽 + 文字宽：文字从「完全在右边外面」走到「完全在左边外面」。
     * 两种模式必须用同一个公式，否则对比的就不是「洞的差别」，
     * 而是「两套不同速度」的差别。
     */
    public static float trackLeft(float canvasWidth, float textWidth, float progress) {
        float span = canvasWidth + textWidth;
        return canvasWidth - span * progress;
    }

    /**
     * 虫洞把整句往左瞬移了「已穿过的洞宽之和」，所以屏幕上最后一个像素
     * 比轨道走完更早消失 —— 这个进度就是那个临界点。
     *
     * 与运行时 {@link HoleRects#finishLeft} 是同一个判据，预览据此可以
     * 和真实播放对上，而不是靠肉眼估算。
     */
    public static float progressAtFinish(float canvasWidth, float textWidth, float wormholeWidth) {
        float span = canvasWidth + textWidth;
        if (span <= 0f) {
            return 1f;
        }
        float finishLeft = -textWidth + wormholeWidth;
        return (canvasWidth - finishLeft) / span;
    }
}
