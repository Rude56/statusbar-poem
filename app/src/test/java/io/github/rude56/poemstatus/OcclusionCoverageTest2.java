package io.github.rude56.poemstatus;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * 重绘区域的正确性：洞跟着诗句往左走，旧位置必须被擦掉。
 *
 * {@code HolePuncher} 每帧只重绘「这一帧的洞」，而洞在左移 —— 诗句让出来的那条右侧条带
 * 必须由本帧的重绘区一并覆盖，否则状态栏图标会缺一条并越拖越长。
 */
public class OcclusionCoverageTest2 {

    private static final int BAR_W = 1440;
    private static final float TEXT_W = 1284f;
    private static final int CAP = 64;

    private static final float[] SEG_S = new float[CAP];
    private static final float[] SEG_T = new float[CAP];
    private static final float[] SEG_W = new float[CAP];
    private static final float[] VIS_L = new float[CAP];
    private static final float[] VIS_R = new float[CAP];

    /**
     * 一帧的可见段（与运行时走同一条路径：先虫洞分段，再算可见段）。
     *
     * 必须把分段的真实输出喂给 {@code visibleSegments}，
     * 传 null 会走「没有虫洞」的整条轨道分支 —— 那样测不到多段情形。
     */
    private static int visible(float left, List<HoleRects.Hole> holes) {
        int n = HoleRects.wormholeSegments(holes, left, left + TEXT_W, 0,
                SEG_S, SEG_T, SEG_W, CAP);
        return HoleRects.visibleSegments(holes, SEG_S, SEG_W, n,
                left, left + TEXT_W, 0, BAR_W, VIS_L, VIS_R);
    }

    /**
     * 模拟 {@code HolePuncher} 的脏区记账。
     *
     * 与实现同一套算术：脏区 = 本帧所有段的并集 ∪ 上一帧还没被擦掉的并集。
     */
    private static final class DirtyTracker {
        int lastL;
        int lastR;
        boolean has;

        /** @return 该帧应重绘的 {left, right}；无事可做时 null。 */
        int[] frame(float[] lefts, float[] rights, int count) {
            if (count <= 0) {
                // 本帧没有任何可见文字：只需擦掉上一帧挖过的范围。
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

    /**
     * 诗句一路左移：每一帧的重绘区都必须覆盖「上一帧被挖掉的范围」。
     *
     * 只重绘 {@code [新left, 新right]} 时，{@code 新right &lt; 旧right}，
     * 那么 {@code [新right, 旧right]} 就永远没人补画。
     */
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
            // 本帧重绘区必须覆盖「本帧要挖的」那几段。
            assertTrue("第 " + frames + " 帧重绘区 " + dirty[0] + "-" + dirty[1]
                            + " 没覆盖本帧的洞 " + (int) VIS_L[0] + "-" + (int) VIS_R[n - 1],
                    dirty[0] <= (int) VIS_L[0] && dirty[1] >= (int) VIS_R[n - 1]);
            // 度量的是「少画了多少」，不是多画多少：
            // 多画是刻意的（上一帧的洞必须一起补），少画才是缺陷。
            worstMiss = Math.max(worstMiss,
                    Math.max(0, (int) VIS_L[0] - dirty[0])
                            + Math.max(0, (int) VIS_R[n - 1] - dirty[1]));
        }
        assertTrue("必须真的跑满整段轨迹", frames > 100);
        assertTrue("这条配置下必须出现过「跨过洞口」的多段帧", multi > 0);
        assertEquals("本帧的洞必须被完整覆盖", 0, worstMiss);
    }

    /**
     * 每一帧的重绘区必须覆盖上一帧的整条洞范围 —— 逐帧逐步验证。
     *
     * 诗句每帧左移一点，右侧就让出一点；那一点只要没被重绘就会留在屏上。
     */
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

    /**
     * 字整段落进隧道（本帧 0 段）时，上一帧的洞仍然必须被擦掉。
     *
     * 否则那个缺口会永久留在屏幕上 —— 因为之后再没有任何一帧会覆盖到它。
     */
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

        // 已经擦干净之后再问一次：不该再产生无谓的重绘。
        assertEquals("没事可擦时不得重绘", null, t.frame(sl, sr, 0));
    }

    /**
     * 虫洞把句子切成两段时，一次重绘必须覆盖两段的并集。
     *
     * 分两次 invalidate 效果相同，但每次都要走一遍 ViewRootImpl 的脏区登记；
     * 而诗句在移动，两次调用之间还可能各触发一次遍历。
     */
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
