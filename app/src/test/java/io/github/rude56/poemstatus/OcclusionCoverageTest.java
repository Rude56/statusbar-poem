package io.github.rude56.poemstatus;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * 遮挡必须跟着<b>文字真正画出来的位置</b>走。
 *
 * <p>用户报的现象：「虫洞能正确传送文字，但不能正确传送遮挡，文字到了，不会遮挡」。
 *
 * <p>根因：{@code updateOcclusion} 里用
 * <pre>
 *   if (segR &lt;= l + originX || segL &gt;= barW + originX) continue;
 * </pre>
 * 做可见性过滤。其中 {@code l} 是<b>轨道</b>左端（未减跳跃位移），
 * 而 {@code segL/segR} 是<b>屏幕</b>坐标（已减掉一个洞宽）。
 * 于是「已经传送到洞口左边」的那一段，屏幕右端可能小于轨道左端，
 * 被误判成「还没进入状态栏」而<b>整段丢掉</b>：
 * 文字画在洞左边，遮挡却没跟过去。偏差从 0 一路涨到接近一整个洞宽。
 *
 * <p>这一组测试是<b>行为验证</b>，不是源码字符串匹配 ——
 * 上一个 bug 恰好证明了字符串断言对这个类问题完全无效。
 *
 * <p>校验口径：把「文字层实际画出来的像素集合」与「遮挡层实际挖掉的像素集合」
 * 逐帧比较，前者必须被后者完整覆盖。两个集合都按真实语义构造：
 * 虫洞分段（{@link HoleRects#wormholeSegments}）与隧道裁剪都要参与。
 */
public class OcclusionCoverageTest {

    private static final int BAR_W = 1440;
    private static final float TEXT_W = 1284f;
    private static final float SPEED = 172f;
    private static final int CAP = 64;

    private static final float[] S = new float[CAP];
    private static final float[] T = new float[CAP];
    private static final float[] W = new float[CAP];
    private static final float[] OL = new float[CAP];
    private static final float[] OR = new float[CAP];
    private static final float[] DL = new float[CAP];
    private static final float[] DR = new float[CAP];
    private static final float[] CL = new float[CAP];
    private static final float[] CR = new float[CAP];

    /** 本帧的虫洞分段数（同时填充 S/T/W）。 */
    private static int segments(List<HoleRects.Hole> holes, float left) {
        return HoleRects.wormholeSegments(holes, left, left + TEXT_W, 0, S, T, W, CAP);
    }

    /**
     * 文字层真正画出来的区间（屏幕坐标）。
     *
     * <p>必须与 {@code drawTextWithHoles} 一致：虫洞分段逐个 {@code clipRect} 后平移绘制，
     * 再叠加隧道的 {@code clipOutRect}。所以这里 = 分段 ∩ 非隧道 ∩ 屏幕。
     */
    private static int drawnSpans(List<HoleRects.Hole> holes, float left) {
        int n = segments(holes, left);
        int k = 0;
        if (n <= 0) {
            return 0;
        }
        for (int i = 0; i < n; i++) {
            if (W[i] <= 0f) {
                continue;
            }
            k = HoleRects.subtractTunnels(holes, S[i], S[i] + W[i], DL, DR, k);
        }
        return k;
    }

    /** 遮挡层真正挖掉的区间（屏幕坐标），裁到状态栏范围。 */
    private static int occludedSpans(List<HoleRects.Hole> holes, float left, int segCount) {
        int n = HoleRects.visibleSegments(holes, S, W, segCount, left, left + TEXT_W,
                0, BAR_W, OL, OR);
        int k = 0;
        for (int i = 0; i < n; i++) {
            float l = Math.max(OL[i], 0f);
            float r = Math.min(OR[i], (float) BAR_W);
            if (r > l) {
                CL[k] = l;
                CR[k] = r;
                k++;
            }
        }
        return k;
    }

    private static float coveredLength(int count, float a, float b) {
        float sum = 0f;
        for (int j = 0; j < count; j++) {
            float l = Math.max(CL[j], a);
            float r = Math.min(CR[j], b);
            if (r > l) {
                sum += r - l;
            }
        }
        return sum;
    }

    private static void assertEveryDrawnPixelIsCovered(String holesRaw) {
        List<HoleRects.Hole> holes = HoleRects.parse(holesRaw);
        int frames = 0;
        for (float left = BAR_W; left + TEXT_W > 0f; left -= SPEED * 0.016f) {
            int nSeg = segments(holes, left);
            int nd = drawnSpans(holes, left);
            int nc = occludedSpans(holes, left, nSeg);
            for (int i = 0; i < nd; i++) {
                float a = Math.max(DL[i], 0f);
                float b = Math.min(DR[i], (float) BAR_W);
                if (b <= a) {
                    continue;                       // 该段完全在屏外
                }
                assertEquals("left=" + left + " 第" + i + "段文字没被遮挡覆盖"
                                + "（洞=" + holesRaw + "，区间 " + a + ".." + b + "）",
                        b - a, coveredLength(nc, a, b), 0.51f);
            }
            frames++;
        }
        assertTrue("扫描帧数异常: " + frames, frames > 500);
    }

    @Test
    public void singleWormholeOcclusionFollowsTheTeleportedText() {
        assertEveryDrawnPixelIsCovered("170:270:wormhole");
    }

    @Test
    public void largeWormholeOcclusionFollowsTheTeleportedText() {
        // 洞越宽，旧实现的偏差越大（最大接近一整个洞宽）。
        assertEveryDrawnPixelIsCovered("150:450:wormhole");
    }

    @Test
    public void wormholePlusTunnelOcclusionStillCoversEverything() {
        // 这是本机当前真实配置：虫洞 + 隧道并存。
        assertEveryDrawnPixelIsCovered("170:270:wormhole,1070:1170:tunnel");
    }

    @Test
    public void twoWormholesOcclusionStillCoversEverything() {
        assertEveryDrawnPixelIsCovered("200:300:wormhole,900:1000:wormhole");
    }

    /** 没有洞时：遮挡就是文字可见部分，边界必须被裁到状态栏范围内。 */
    @Test
    public void noHolesOccludesExactlyTheTextWithinTheBar() {
        List<HoleRects.Hole> holes = HoleRects.parse("");
        // 文字 900..2184：右端超出状态栏，遮挡必须停在 1440。
        int n = occludedSpans(holes, 900f, 0);
        assertEquals(1, n);
        assertEquals(900f, CL[0], 0.01f);
        assertEquals("遮挡不得越过状态栏右沿", (float) BAR_W, CR[0], 0.01f);

        // 左端已经跑到屏幕外：遮挡从 0 开始，不能出现负坐标。
        n = occludedSpans(holes, -200f, 0);
        assertEquals(1, n);
        assertEquals("遮挡不得越过状态栏左沿", 0f, CL[0], 0.01f);
        assertEquals(TEXT_W - 200f, CR[0], 0.01f);
    }

    /** 整句都在屏幕外时，一段遮挡都不该产生（否则会挖出屏幕外的洞）。 */
    @Test
    public void fullyOffscreenTextOccludesNothing() {
        List<HoleRects.Hole> holes = HoleRects.parse("170:270:wormhole");
        float left = -TEXT_W - 10f;
        int nSeg = segments(holes, left);
        assertEquals("整句已离屏，不该再挖洞", 0, occludedSpans(holes, left, nSeg));
    }

    /**
     * 隧道里的字不该遮挡状态栏 —— 但这条不能被「多遮一点」的实现糊过去。
     */
    @Test
    public void tunnelSpanIsNeverOccluded() {
        List<HoleRects.Hole> holes = HoleRects.parse("600:900:tunnel");
        for (float left = 1000f; left > -900f; left -= 40f) {
            int nSeg = segments(holes, left);
            int nc = occludedSpans(holes, left, nSeg);
            for (int j = 0; j < nc; j++) {
                assertFalse("left=" + left + " 隧道内部被遮挡了: " + CL[j] + "-" + CR[j],
                        CR[j] > 600f && CL[j] < 900f);
            }
        }
    }

    /**
     * 遮挡分段必须严格递增、两两不重叠。
     *
     * <p>重叠会让同一个区间被 {@code clipOutRect} 两次 —— 结果不变，
     * 但绘制层与遮挡层的段数对不上时，重叠就是「段被算错」的第一个信号。
     */
    @Test
    public void occlusionSegmentsAreOrderedAndDisjoint() {
        for (String raw : new String[]{"170:270:wormhole",
                "150:450:wormhole", "170:270:wormhole,1070:1170:tunnel",
                "200:300:wormhole,900:1000:wormhole,1200:1300:tunnel"}) {
            List<HoleRects.Hole> holes = HoleRects.parse(raw);
            for (float left = BAR_W; left + TEXT_W > 0f; left -= 7f) {
                int nSeg = segments(holes, left);
                int nc = occludedSpans(holes, left, nSeg);
                float prev = Float.NaN;
                for (int j = 0; j < nc; j++) {
                    assertTrue("洞=" + raw + " left=" + left + " 段不是正宽",
                            CR[j] > CL[j]);
                    if (!Float.isNaN(prev)) {
                        assertTrue("洞=" + raw + " left=" + left + " 第" + j + "段重叠",
                                CL[j] >= prev - 0.01f);
                    }
                    prev = CR[j];
                }
            }
        }
    }

    /**
     * 状态栏不在屏幕原点时，「文字画在哪，遮挡就在哪」仍然必须成立。
     *
     * <p><b>为什么单独测这个</b>：上面所有用例都按 {@code originX = 0} 构造 ——
     * 而这恰好是唯一能掩盖「原点被加两次 / 忘加」的取值。
     * 这类 bug 已经真实发生过两次（一次是无洞快路径直接丢本地坐标，
     * 一次是虫洞可见性判据混用了轨道与屏幕坐标），两次都只在原点非 0 时才露出来。
     *
     * <p>注意不能简单地说「换个原点，结果应该平移」：{@code barW} 不变时，
     * 换原点等于把状态栏挪到屏幕的<b>另一个位置</b>，那是另一个场景，不是同一个。
     * 唯一恒定成立的关系是：<b>遮挡段（屏幕坐标）必须等于绘制段（本地坐标）+ 原点</b>。
     */
    @Test
    public void occlusionMatchesDrawingAtAnyOrigin() {
        for (String raw : new String[]{"170:270:wormhole", "600:900:tunnel",
                "170:270:wormhole,1070:1170:tunnel",
                "200:300:wormhole,900:1000:wormhole"}) {
            for (int origin : new int[]{0, 137, 320}) {
                assertOcclusionMatchesDrawing(raw, origin);
            }
        }
    }

    private static void assertOcclusionMatchesDrawing(String raw, int origin) {
        List<HoleRects.Hole> holes = HoleRects.parse(raw);
        float[] segScreen = new float[CAP];
        float[] segText = new float[CAP];
        float[] segWidth = new float[CAP];
        float[] outL = new float[CAP];
        float[] outR = new float[CAP];
        final float barL = origin;
        final float barR = origin + BAR_W;
        int frames = 0;

        for (float trackLeft = BAR_W; trackLeft + TEXT_W > 0f; trackLeft -= SPEED * 0.016f) {
            int nSeg = HoleRects.wormholeSegments(holes, trackLeft, trackLeft + TEXT_W,
                    origin, segScreen, segText, segWidth, CAP);

            // 绘制段（本地坐标）→ 换成屏幕坐标，并按状态栏范围裁剪。
            float[] drawnL = new float[CAP];
            float[] drawnR = new float[CAP];
            int nd = 0;
            for (int i = 0; i < nSeg; i++) {
                if (segWidth[i] <= 0f) {
                    continue;
                }
                float sl = segScreen[i] + origin;
                float sr = sl + segWidth[i];
                // 隧道里不画字。
                nd = HoleRects.subtractTunnels(holes, sl, sr, drawnL, drawnR, nd);
            }

            int nc = HoleRects.visibleSegments(holes, segScreen, segWidth, nSeg,
                    trackLeft, trackLeft + TEXT_W, origin, BAR_W, outL, outR);

            // 把输出裁到状态栏范围，便于逐个比较。
            int nc2 = 0;
            for (int i = 0; i < nc; i++) {
                float l = Math.max(outL[i], barL);
                float r = Math.min(outR[i], barR);
                if (r > l) {
                    CL[nc2] = l;
                    CR[nc2] = r;
                    nc2++;
                }
            }

            for (int i = 0; i < nd; i++) {
                float a = Math.max(drawnL[i], barL);
                float b = Math.min(drawnR[i], barR);
                if (b <= a) {
                    continue;   // 这一段完全在状态栏外
                }
                assertEquals("原点=" + origin + " 洞=" + raw
                                + " left=" + trackLeft + " 第" + i + "段文字没被遮挡覆盖",
                        b - a, coveredLength(nc2, a, b), 0.51f);
            }
            frames++;
        }
        assertTrue("扫描帧数异常: " + frames, frames > 500);
    }

    /**
     * 状态栏不在原点时，遮挡段绝不允许越出状态栏范围。
     *
     * <p>越界意味着在状态栏外面挖了一个看不见的洞，同时本该遮住的图标没被遮住。
     */
    @Test
    public void occlusionNeverEscapesTheBar() {
        for (int origin : new int[]{0, 137, 320}) {
            List<HoleRects.Hole> holes = HoleRects.parse("170:270:wormhole");
            float[] segScreen = new float[CAP];
            float[] segText = new float[CAP];
            float[] segWidth = new float[CAP];
            float[] l = new float[CAP];
            float[] r = new float[CAP];
            for (float trackLeft = BAR_W; trackLeft + TEXT_W > 0f; trackLeft -= 53f) {
                int n = HoleRects.wormholeSegments(holes, trackLeft, trackLeft + TEXT_W,
                        origin, segScreen, segText, segWidth, CAP);
                int c = HoleRects.visibleSegments(holes, segScreen, segWidth, n,
                        trackLeft, trackLeft + TEXT_W, origin, BAR_W, l, r);
                for (int i = 0; i < c; i++) {
                    assertTrue("段不得越过状态栏左沿（原点=" + origin
                                    + " trackLeft=" + trackLeft + "）",
                            l[i] >= origin - 0.01f);
                    assertTrue("段不得越过状态栏右沿（原点=" + origin
                                    + " trackLeft=" + trackLeft + "）",
                            r[i] <= origin + BAR_W + 0.01f);
                    assertTrue("段必须为正宽", r[i] > l[i]);
                }
            }
        }
    }
}
