package io.github.rude56.poemstatus;

/**
 * 洞页两个样例的<b>共用时钟</b>（纯逻辑，JVM 可测）。
 *
 * <p>为什么需要单独一个类：上下两行样例（隧道 / 虫洞）的全部意义就是<b>逐帧对照</b>，
 * 所以它们必须由<b>同一个进度</b>驱动、走<b>同一条轨道</b>。只要有任何一行自己算时间，
 * 对比就失效了 —— 而这种失效不会报错，只会让两块画面对不上，肉眼很难判断是谁错了。
 *
 * <p>以前这段数学内联在 Composable 的 {@code LaunchedEffect} 里，且有一个真缺陷：
 * 进度被 {@code coerceIn(0f, 1f)} 夹住，而起点从不重置 ——
 * 于是跑到 14 秒后<b>永久停在 progress=1</b>，两行都是空的，再也没法对比。
 * 这里改成按周期取模，永续循环。
 */
public final class PreviewClock {

    /** 一轮的时长。 */
    public static final long CYCLE_NANOS = 14_000_000_000L;

    private PreviewClock() {
    }

    /**
     * 当前进度，取值 {@code [0, 1)}。
     *
     * <p>按周期取模而不是夹取：夹取会在周期末尾冻结，取模则会循环。
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
     * 一轮循环实际需要的进度跨度 —— 两行必须<b>同时</b>有字。
     *
     * <p>这里有个真实缺陷：进度本来按整条轨道 {@code [0,1]} 循环，
     * 但虫洞把整句往左瞬移了一个洞宽，字在 {@code progress} 还差一点才到 1 时
     * 就已经<b>全部离开屏幕</b>了。于是每轮末尾有一段时间虫洞行是空的、隧道行还在滚 ——
     * 用户报的「两个预览没有同时出现古诗」正是这个。
     *
     * <p>量化（画布 1200px / 文字 780px / 洞宽 384px，一轮 14 秒）：
     * 虫洞行消失于 {@code progress=0.806}，隧道行消失于 {@code 1.0} ——
     * 每轮有 <b>2.7 秒</b>（19.4%）只有一行有字。
     *
     * <p>所以循环终点取「<b>两行都还有字</b>」的最大进度，即虫洞行的消失点。
     * 到点立刻重启，两行永远同时在场。
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
     * <p>轨道总长 = 画布宽 + 文字宽：文字从「完全在右边外面」走到「完全在左边外面」。
     * <b>两种模式必须用同一个公式</b>，否则对比的就不是「洞的差别」，
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
     * <p>与运行时 {@link HoleRects#finishLeft} 是同一个判据，预览据此可以
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
