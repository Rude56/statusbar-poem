package io.github.rude56.poemstatus;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PreviewClockTest {
    private static final int CAP = 16;
    private static final float[] S = new float[CAP];
    private static final float[] T = new float[CAP];
    private static final float[] W = new float[CAP];
    private static boolean visible(List<HoleRects.Hole> holes, float canvasWidth,
                                  float textWidth, float progress, boolean wormhole) {
        float left = PreviewClock.trackLeft(canvasWidth, textWidth, progress);
        float right = left + textWidth;
        if (!wormhole) {
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

    @Test
    public void progressWrapsForeverInsteadOfFreezing() {
        long cycle = PreviewClock.CYCLE_NANOS;
        assertEquals(0f, PreviewClock.progressAt(0L, cycle), 0.0001f);
        assertEquals(0.5f, PreviewClock.progressAt(cycle / 2, cycle), 0.0001f);

        assertEquals(0.25f, PreviewClock.progressAt(cycle + cycle / 4, cycle), 0.0001f);
        assertEquals(0.75f, PreviewClock.progressAt(cycle * 7 + cycle * 3 / 4, cycle), 0.0001f);

        assertEquals(0.75f, PreviewClock.progressAt(-cycle / 4, cycle), 0.0001f);

        assertEquals(0f, PreviewClock.progressAt(123L, 0L), 0.0001f);
    }

    @Test
    public void cycleEndKeepsBothRowsAlive() {
        float canvasW = 1200f;
        float textW = 780f;
        float holeW = canvasW * (0.62f - 0.30f);

        List<HoleRects.Hole> holes = HoleRects.parse("360:744:wormhole:0");
        float end = PreviewClock.cycleEnd(canvasW, textW, holeW);
        assertTrue("循环终点必须小于 1（虫洞行会更早走空）", end < 1f);
        assertTrue("循环终点不能太小，否则诗一闪而过", end > 0.5f);

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

    @Test
    public void cycleEndIsFullTrackWhenThereIsNoWormhole() {
        assertEquals(1f, PreviewClock.cycleEnd(1200f, 780f, 0f), 0.0001f);
    }

    @Test
    public void cycleEndIsSafeForDegenerateInputs() {
        float end = PreviewClock.cycleEnd(100f, 100f, 500f);
        assertTrue("终点必须为正", end > 0f);

        assertEquals(0.02f, end, 0.0001f);
    }

    @Test
    public void trackLeftGoesFromRightEdgeToPastLeftEdge() {
        assertEquals(1200f, PreviewClock.trackLeft(1200f, 780f, 0f), 0.01f);
        assertEquals(-780f, PreviewClock.trackLeft(1200f, 780f, 1f), 0.01f);

        float prev = Float.MAX_VALUE;
        for (int i = 0; i <= 100; i++) {
            float v = PreviewClock.trackLeft(1200f, 780f, i / 100f);
            assertTrue("轨道位置必须单调递减", v < prev);
            prev = v;
        }
    }
}
