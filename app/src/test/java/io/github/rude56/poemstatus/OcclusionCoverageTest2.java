package io.github.rude56.poemstatus;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class OcclusionCoverageTest2 {
    private static final int BAR_W = 1440;
    private static final float TEXT_W = 1284f;
    private static final int CAP = 64;
    private static final float[] SEG_S = new float[CAP];
    private static final float[] SEG_T = new float[CAP];
    private static final float[] SEG_W = new float[CAP];
    private static final float[] VIS_L = new float[CAP];
    private static final float[] VIS_R = new float[CAP];

    private static int visible(float left, List<HoleRects.Hole> holes) {
        int n = HoleRects.wormholeSegments(holes, left, left + TEXT_W, 0,
                SEG_S, SEG_T, SEG_W, CAP);
        return HoleRects.visibleSegments(holes, SEG_S, SEG_W, n,
                left, left + TEXT_W, 0, BAR_W, VIS_L, VIS_R);
    }

    private static final class DirtyTracker {
        int lastL;
        int lastR;
        boolean has;
        int[] frame(float[] lefts, float[] rights, int count) {
            if (count <= 0) {

                if (!has) {
                    return null;
                }
                has = false;
                return new int[]{lastL, lastR};
            }
            int minL = Integer.MAX_VALUE;
            int maxR = Integer.MIN_VALUE;
            for (int i = 0; i < count; i++) {
                minL = Math.min(minL, (int) lefts[i]);
                maxR = Math.max(maxR, (int) rights[i]);
            }
            int l = has ? Math.min(minL, lastL) : minL;
            int r = has ? Math.max(maxR, lastR) : maxR;
            lastL = minL;
            lastR = maxR;
            has = true;
            return new int[]{l, r};
        }
    }

    @Test
    public void repaintCoversTheAreaThePoemJustLeft() {
        List<HoleRects.Hole> holes = HoleRects.parse("170:1000:wormhole:0");
        DirtyTracker t = new DirtyTracker();

        int frames = 0;
        int multi = 0;
        int worstMiss = 0;

        for (float left = BAR_W; left + TEXT_W > 0f; left -= 12f) {
            int n = visible(left, holes);
            if (n > 1) {
                multi++;
            }
            int[] dirty = t.frame(VIS_L, VIS_R, n);
            frames++;
            if (n <= 0 || dirty == null) {
                continue;
            }
            assertTrue("第 " + frames + " 帧重绘区 " + dirty[0] + "-" + dirty[1]
                            + " 没覆盖本帧的洞 " + (int) VIS_L[0] + "-" + (int) VIS_R[n - 1],
                    dirty[0] <= (int) VIS_L[0] && dirty[1] >= (int) VIS_R[n - 1]);

            worstMiss = Math.max(worstMiss,
                    Math.max(0, (int) VIS_L[0] - dirty[0])
                            + Math.max(0, (int) VIS_R[n - 1] - dirty[1]));
        }
        assertTrue("必须真的跑满整段轨迹", frames > 100);
        assertTrue("这条配置下必须出现过「跨过洞口」的多段帧", multi > 0);
        assertEquals("本帧的洞必须被完整覆盖", 0, worstMiss);
    }

    @Test
    public void everyFrameRepaintsThePreviousHoleCompletely() {
        List<HoleRects.Hole> holes = HoleRects.parse("170:1000:wormhole:0");
        DirtyTracker t = new DirtyTracker();

        int prevL = -1;
        int prevR = -1;
        boolean prevValid = false;
        int checked = 0;

        for (float left = BAR_W; left + TEXT_W > 0f; left -= 7f) {
            int n = visible(left, holes);
            int newL = n > 0 ? (int) VIS_L[0] : -1;
            int newR = n > 0 ? (int) VIS_R[n - 1] : -1;
            int[] dirty = t.frame(VIS_L, VIS_R, n);

            if (prevValid && dirty != null) {
                checked++;
                assertTrue("上一帧的洞 " + prevL + "-" + prevR
                                + " 没被这一帧的重绘区 " + dirty[0] + "-" + dirty[1] + " 覆盖",
                        dirty[0] <= prevL && dirty[1] >= prevR);
            }
            prevL = newL;
            prevR = newR;
            prevValid = n > 0;
        }
        assertTrue("必须真的验证到足够多的帧", checked > 100);
    }

    @Test
    public void zeroSegmentFrameStillErasesThePreviousHole() {
        float[] sl = new float[CAP];
        float[] sr = new float[CAP];
        DirtyTracker t = new DirtyTracker();

        sl[0] = 400f;
        sr[0] = 900f;
        int[] first = t.frame(sl, sr, 1);
        assertEquals(400, first[0]);
        assertEquals(900, first[1]);

        int[] erased = t.frame(sl, sr, 0);
        assertTrue("必须给出重绘请求", erased != null);
        assertEquals("必须精确擦掉上一帧的洞", 400, erased[0]);
        assertEquals(900, erased[1]);

        assertEquals("没事可擦时不得重绘", null, t.frame(sl, sr, 0));
    }

    @Test
    public void multiSegmentFrameRepaintsOneUnionBox() {
        List<HoleRects.Hole> holes = HoleRects.parse("170:1000:wormhole:0");
        DirtyTracker t = new DirtyTracker();

        int found = 0;
        for (float left = BAR_W; left + TEXT_W > 0f; left -= 4f) {
            int n = visible(left, holes);
            if (n < 2) {
                continue;
            }
            found++;
            int[] dirty = t.frame(VIS_L, VIS_R, n);
            assertTrue("并集左边界必须覆盖第一段", dirty[0] <= (int) VIS_L[0]);
            assertTrue("并集右边界必须覆盖最后一段", dirty[1] >= (int) VIS_R[n - 1]);
            break;
        }
        assertTrue("这条配置下必须能找到「跨过洞口」的帧", found > 0);
    }
}
