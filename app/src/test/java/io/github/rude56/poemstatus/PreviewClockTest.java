package io.github.rude56.poemstatus;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * 洞页两行预览必须<b>同时</b>有古诗。
 *
 * <p>这是两个样例存在的唯一理由：逐帧对照「隧道」与「虫洞」的差别。
 * 只要某一行提前走空，对比就失效 —— 而这种失效不抛异常、不报错，
 * 只表现为「一块画面是空的」。
 *
 * <p>历史缺陷：
 * <ol>
 *   <li>进度 {@code coerceIn(0f, 1f)} 且起点从不重置 —— 跑到 14 秒后
 *       永久停在 progress=1，两行都是空的；</li>
 *   <li>循环按整条轨道 {@code [0,1]} 走，而虫洞行会提前 19% 走空 ——
 *       每轮有 2.7 秒只剩隧道行还在滚。</li>
 * </ol>
 */
public class PreviewClockTest {

    private static final int CAP = 16;
    private static final float[] S = new float[CAP];
    private static final float[] T = new float[CAP];
    private static final float[] W = new float[CAP];

    /** 该进度下，屏幕上还有没有可见像素（与预览绘制同源）。 */
    private static boolean visible(List<HoleRects.Hole> holes, float canvasWidth,
                                  float textWidth, float progress, boolean wormhole) {
        float left = PreviewClock.trackLeft(canvasWidth, textWidth, progress);
        float right = left + textWidth;
        if (!wormhole) {
            // 隧道：位置不动，只看是否与画布相交（洞里那段被遮住不影响「有没有字」）。
            return right > 0f && left < canvasWidth;
        }
        int n = HoleRects.wormholeSegments(holes, left, right, 0, S, T, W, CAP);
        if (n <= 0) {
            return right > 0f && left < canvasWidth;
        }
        for (int i = 0; i < n; i++) {
            if (W[i] <= 0f) {
                continue;
            }
            float l = Math.max(S[i], 0f);
            float r = Math.min(S[i] + W[i], canvasWidth);
            if (r > l) {
                return true;
            }
        }
        return false;
    }

    /** 按周期取模：永续循环，绝不冻结。 */
    @Test
    public void progressWrapsForeverInsteadOfFreezing() {
        long cycle = PreviewClock.CYCLE_NANOS;
        assertEquals(0f, PreviewClock.progressAt(0L, cycle), 0.0001f);
        assertEquals(0.5f, PreviewClock.progressAt(cycle / 2, cycle), 0.0001f);
        // 超过一轮：必须绕回来，而不是夹在 1。
        assertEquals(0.25f, PreviewClock.progressAt(cycle + cycle / 4, cycle), 0.0001f);
        assertEquals(0.75f, PreviewClock.progressAt(cycle * 7 + cycle * 3 / 4, cycle), 0.0001f);
        // 负时间也要安全（起点用 now - progress*cycle 计算，可能为负）。
        assertEquals(0.75f, PreviewClock.progressAt(-cycle / 4, cycle), 0.0001f);
        // 非法周期不得除零。
        assertEquals(0f, PreviewClock.progressAt(123L, 0L), 0.0001f);
    }

    /**
     * 循环终点必须落在「虫洞行还有字」的范围内。
     *
     * <p>这条直接把「两行同时出现」变成可断言的数字。
     */
    @Test
    public void cycleEndKeepsBothRowsAlive() {
        float canvasW = 1200f;
        float textW = 780f;
        float holeW = canvasW * (0.62f - 0.30f);

        List<HoleRects.Hole> holes = HoleRects.parse("360:744:wormhole:0");
        float end = PreviewClock.cycleEnd(canvasW, textW, holeW);
        assertTrue("循环终点必须小于 1（虫洞行会更早走空）", end < 1f);
        assertTrue("循环终点不能太小，否则诗一闪而过", end > 0.5f);

        // 在 (0, end) 区间内密集取样：两行必须同时有字。
        //
        // 起点 0 不算：那时诗句刚好贴着画布右沿、完全在屏外，
        // 两行都是空的 —— 这是「还没进场」，不是「不同步」。
        int samples = 0;
        for (int i = 1; i < 400; i++) {
            float p = end * i / 400f;
            if (p >= end) {
                continue;
            }
            boolean tunnel = visible(holes, canvasW, textW, p, false);
            boolean worm = visible(holes, canvasW, textW, p, true);
            assertTrue("progress=" + p + " 隧道行没有字", tunnel);
            assertTrue("progress=" + p + " 虫洞行没有字（两行不同步）", worm);
            samples++;
        }
        assertTrue("取样数异常", samples > 300);
    }

    /**
     * 反证：如果循环仍然按整条轨道 {@code [0,1]} 走，就会出现只有一行有字的时段。
     *
     * <p>这条用来说明「为什么必须引入 cycleEnd」，同时防止有人把它改回去。
     */
    @Test
    public void fullTrackCycleWouldLeaveTheWormholeRowEmpty() {
        float canvasW = 1200f;
        float textW = 780f;
        float holeW = canvasW * (0.62f - 0.30f);
        List<HoleRects.Hole> holes = HoleRects.parse("360:744:wormhole:0");

        float end = PreviewClock.cycleEnd(canvasW, textW, holeW);
        int onlyTunnel = 0;
        for (int i = 1; i < 400; i++) {
            float p = i / 400f;
            if (p >= 1f) {
                continue;
            }
            boolean tunnel = visible(holes, canvasW, textW, p, false);
            boolean worm = visible(holes, canvasW, textW, p, true);
            if (tunnel && !worm) {
                onlyTunnel++;
            }
        }
        assertTrue("按整条轨道循环时，必须真的存在「只有隧道行有字」的时段"
                        + "（否则 cycleEnd 就没有必要了）", onlyTunnel > 0);

        // 而收缩到 cycleEnd 之后，这个时段必须完全消失。
        int onlyTunnelAfter = 0;
        for (int i = 1; i < 400; i++) {
            float p = end * i / 400f;
            if (p >= end) {
                continue;
            }
            boolean tunnel = visible(holes, canvasW, textW, p, false);
            boolean worm = visible(holes, canvasW, textW, p, true);
            if (tunnel && !worm) {
                onlyTunnelAfter++;
            }
        }
        assertEquals("收缩后不得再有单行时段", 0, onlyTunnelAfter);
    }

    /** 没有虫洞时，循环终点就是整条轨道（两行本来就会同时走空）。 */
    @Test
    public void cycleEndIsFullTrackWhenThereIsNoWormhole() {
        assertEquals(1f, PreviewClock.cycleEnd(1200f, 780f, 0f), 0.0001f);
    }

    /** 退化输入不得让循环卡死（终点为 0 会让进度永远不动）。 */
    @Test
    public void cycleEndIsSafeForDegenerateInputs() {
        float end = PreviewClock.cycleEnd(100f, 100f, 500f);
        assertTrue("终点必须为正", end > 0f);
        // 洞比整条轨道还宽时，仍然要给出一个可动的最小范围。
        assertEquals(0.02f, end, 0.0001f);
    }

    /** 轨道位置：起点在画布右侧之外，终点在左侧之外。 */
    @Test
    public void trackLeftGoesFromRightEdgeToPastLeftEdge() {
        assertEquals(1200f, PreviewClock.trackLeft(1200f, 780f, 0f), 0.01f);
        assertEquals(-780f, PreviewClock.trackLeft(1200f, 780f, 1f), 0.01f);
        // 单调递减：速度恒定，不因洞而改变。
        float prev = Float.MAX_VALUE;
        for (int i = 0; i <= 100; i++) {
            float v = PreviewClock.trackLeft(1200f, 780f, i / 100f);
            assertTrue("轨道位置必须单调递减", v < prev);
            prev = v;
        }
    }
}
