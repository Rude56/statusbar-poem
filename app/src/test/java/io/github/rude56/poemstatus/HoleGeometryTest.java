package io.github.rude56.poemstatus;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * 虫洞（传送门）/ 隧道的几何验证 —— 只检查算出来的结果。
 *
 * <p>定稿语义（用户确认）：
 * <ul>
 *   <li><b>虫洞 = 传送门</b>：文字整体从右往左滚。字到达洞口<b>右沿</b>的瞬间，
 *       出现在洞口<b>左沿</b>，之后继续往左走，速度不变。洞里不画。</li>
 *   <li>于是整句在屏幕上被<b>撑长</b>一个洞宽：已经穿过门的字在左侧更靠左，
 *       还没穿过的字保持原位，中间隔着洞口 —— 左端因此<b>提早离开屏幕</b>。</li>
 *   <li><b>隧道</b>：古诗位置和速度完全不变，那一段被遮住看不见；
 *       绝不遮挡状态栏。</li>
 * </ul>
 */
public class HoleGeometryTest {

    private static final float[] S = new float[12];
    private static final float[] T = new float[12];
    private static final float[] W = new float[12];

    private static float span(int n) {
        float lo = Float.MAX_VALUE;
        float hi = -Float.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            if (W[i] <= 0f) {
                continue;
            }
            lo = Math.min(lo, S[i]);
            hi = Math.max(hi, S[i] + W[i]);
        }
        return hi - lo;
    }

    private static float totalWidth(int n) {
        float t = 0f;
        for (int i = 0; i < n; i++) {
            t += W[i];
        }
        return t;
    }

    @Test
    public void nothingMovesBeforeThePortalIsReached() {
        // 诗还在洞口右侧：不能有任何位移（首字消失 bug 的回归）。
        List<HoleRects.Hole> holes = HoleRects.parse("700:800:wormhole");
        int n = HoleRects.wormholeSegments(holes, 1200f, 1500f, 0, S, T, W, 12);
        assertEquals(1, n);
        assertEquals("未到洞口不得位移", 1200f, S[0], 0.01f);
        assertEquals(1200f, T[0], 0.01f);
        assertEquals(300f, W[0], 0.01f);
    }

            @Test
    public void segmentsNeverOverlapAndNeverLoseText() {
        // 全帧扫描：不重叠、不丢字。
        List<HoleRects.Hole> holes = HoleRects.parse(
                "400:500:wormhole,670:770:wormhole,1100:1200:wormhole");
        int frames = 0;
        for (float left = 1800f; left >= -1400f; left -= 3f) {
            int n = HoleRects.wormholeSegments(holes, left, left + 1400f, 0, S, T, W, 12);
            float prevEnd = Float.NaN;
            for (int i = 0; i < n; i++) {
                if (W[i] <= 0f) {
                    continue;
                }
                assertTrue("段宽必须为正", W[i] > 0f);
                if (!Float.isNaN(prevEnd)) {
                    assertTrue("left=" + left + " 第" + i + "段重叠 "
                            + (prevEnd - S[i]) + "px", prevEnd <= S[i] + 0.01f);
                }
                prevEnd = S[i] + W[i];
            }
            assertEquals("left=" + left + " 文字不得丢失", 1400f, totalWidth(n), 0.01f);
            frames++;
        }
        assertTrue(frames > 900);
    }

    @Test
    public void poemLeavesTheScreenEarlierThanWithoutHoles() {
        // 「提早离开屏幕」：穿门之后，诗的左端比没有洞时更靠左。
        List<HoleRects.Hole> holes = HoleRects.parse("700:800:wormhole");
        // 文字左端在 650（已在门左侧一部分）
        int n = HoleRects.wormholeSegments(holes, 650f, 1250f, 0, S, T, W, 12);
        float leftmost = Float.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            leftmost = Math.min(leftmost, S[i]);
        }
        // 没有洞时左端是 650；有洞时被推到一个洞宽之外。
        assertEquals("左端应比无洞时早一个洞宽离屏", 550f, leftmost, 0.01f);
    }

    @Test
    public void tunnelNeverShiftsAnything() {
        // 隧道：古诗位置完全不动，只是那一段被遮住。
        List<HoleRects.Hole> holes = HoleRects.parse("700:800:tunnel");
        int n = HoleRects.wormholeSegments(holes, 300f, 600f, 0, S, T, W, 12);
        assertEquals(1, n);
        assertEquals("隧道不产生任何位移", 300f, S[0], 0.01f);
        assertEquals(300f, T[0], 0.01f);
        assertEquals(300f, W[0], 0.01f);
    }

    @Test
    public void holesNeverAffectTheStatusBar() throws java.io.IOException {
        // 洞只作用于古诗层：状态栏的遮挡只由「古诗真实可见的分段」决定，
        // 绝不能把用户配的洞直接拿去挖状态栏。
        java.io.File f = new java.io.File(
                "src/main/java/io/github/rude56/poemstatus/PoemRuntime.java");
        assertTrue("必须能读到源码: " + f, f.isFile());
        String rt = new String(java.nio.file.Files.readAllBytes(f.toPath()),
                java.nio.charset.StandardCharsets.UTF_8);
        assertTrue("源码读取异常", rt.length() > 1000);
        assertFalse("不得用用户洞列表直接挖状态栏",
                rt.contains("HolePuncher.updatePoemSegments(configHoles"));
    }

    // ---------------------------------------------------------------- 多虫洞

    /**
     * 两个虫洞：第二个洞的触发点不能被第一个洞的宽度带偏。
     *
     * <p>这是用户报的 bug：「两个虫洞就不正常了，第二个虫洞被第一个偏移了」。
     *
     * <p>根因：屏幕位置本身已经被右侧的洞左移过，所以一个洞在<b>原始坐标</b>里的
     * 触发点不是它的屏幕右沿，而要补上它右侧所有洞的宽度：
     * <pre>
     *   P_j = 右沿_j + Σ{ 洞宽_k : k 在 j 的右边 }
     * </pre>
     * 旧代码直接用屏幕右沿当断点，第二个洞的触发点因此早了「第一个洞宽」那么多。
     *
     * <p>本例的两个门（原始坐标）：
     * <pre>
     *   洞 2（900–1000）：1000 + 0        = 1000
     *   洞 1（400–500） ： 500 + 100（右侧洞 2 的宽） = 600
     * </pre>
     */
    @Test
    public void secondWormholeIsNotShiftedByTheFirst() {
        List<HoleRects.Hole> holes = HoleRects.parse("400:500:wormhole,900:1000:wormhole");

        // ① 文字左端在 1800：两个门都还没到 → 不位移。
        int n = HoleRects.wormholeSegments(holes, 1800f, 3200f, 0, S, T, W, 12);
        assertEquals("未到任何门不得位移", 1800f, S[0], 0.01f);

        // ② 文字左端在 700：已越过洞 1 的门（600），还没到洞 2 的门（1000）。
        //    文字跨过洞 2，因此切成两段：左段左移一个洞宽，右段不位移。
        n = HoleRects.wormholeSegments(holes, 700f, 1700f, 0, S, T, W, 12);
        assertEquals("跨过洞口必须切成两段", 2, n);
        assertEquals("左段左移一个洞宽", 600f, S[0], 0.01f);
        assertEquals(700f, T[0], 0.01f);
        assertEquals(300f, W[0], 0.01f);
        assertEquals("右段不位移", 1000f, S[1], 0.01f);
        assertEquals(1000f, T[1], 0.01f);
        assertEquals(700f, W[1], 0.01f);

        // ③ 文字左端在 1100：两个门都过了 → 全部不位移（位移归零）。
        n = HoleRects.wormholeSegments(holes, 1100f, 2100f, 0, S, T, W, 12);
        assertEquals("两个洞都穿过 → 不位移", 1100f, S[0], 0.01f);
    }

    /**
     * 两个虫洞：整句在屏幕上被撑长「两个洞宽」，且分段不重叠、不丢字。
     */
    @Test
    public void twoWormholesStretchByBothWidths() {
        List<HoleRects.Hole> holes = HoleRects.parse("400:500:wormhole,900:1000:wormhole");
        // 文字完全跨过两个洞：左端 300，右端 1300。
        int n = HoleRects.wormholeSegments(holes, 300f, 1300f, 0, S, T, W, 12);
        assertEquals("字一个都不能丢", 1000f, totalWidth(n), 0.01f);
        // 屏幕跨度 = 原始宽度 + 两个洞宽 = 1000 + 200。
        assertEquals("必须被撑开两个洞宽", 1200f, span(n), 0.01f);
        // 分段不得重叠
        float prevEnd = Float.NaN;
        for (int i = 0; i < n; i++) {
            if (W[i] <= 0f) {
                continue;
            }
            if (!Float.isNaN(prevEnd)) {
                assertTrue("第" + i + "段重叠", prevEnd <= S[i] + 0.01f);
            }
            prevEnd = S[i] + W[i];
        }
    }

    /**
     * 两个虫洞：逐帧扫描，第二个洞的传送点必须始终等于「洞右沿 + 右侧洞宽」。
     *
     * <p>这条覆盖的是「位置随帧变化」的情况 —— 单点断言容易漏掉边界。
     */
    /**
     * 两个虫洞：逐帧扫描，每段的位移必须恰好等于「这一段起点之前已穿过的洞宽之和」。
     *
     * <p>关键：判定一个洞是否「已穿过」，要看<b>段的起点</b>是否已经越过门，
     * 而不是看起点数值大于门就加位移 —— 段的起点在门右侧时，
     * 这一段还没到门，不该有位移。
     *
     * <p>正确判据：段起点 T[i] 落在门左侧（即 T[i] < 门）才说明它已经穿过了门。
     */
    @Test
    public void secondPortalFiresAtTheRightMomentAcrossAllFrames() {
        List<HoleRects.Hole> holes = HoleRects.parse("400:500:wormhole,900:1000:wormhole");
        final float textW = 1400f;
        // 两个门（原始坐标）：洞 2 = 1000；洞 1 = 500 + 100 = 600。
        final float gate2 = 1000f;
        final float gate1 = 600f;
        for (float left = 1800f; left >= -400f; left -= 1f) {
            int n = HoleRects.wormholeSegments(holes, left, left + textW, 0, S, T, W, 12);
            // 不变量 1：总宽不变（不丢字、不重叠）
            assertEquals("left=" + left + " 丢字", textW, totalWidth(n), 0.01f);
            // 不变量 2：每段的屏幕位置 = 原始位置 − 已穿过的洞宽之和
            for (int i = 0; i < n; i++) {
                if (W[i] <= 0f) {
                    continue;
                }
                // 段起点在门左侧 → 这一段已经穿过了门。
                float shift = 0f;
                if (T[i] < gate2) {
                    shift += 100f;
                }
                if (T[i] < gate1) {
                    shift += 100f;
                }
                assertEquals("left=" + left + " 第" + i + "段位移错误",
                        T[i] - shift, S[i], 0.01f);
            }
            // 不变量 3：分段严格递增、不重叠
            float prevEnd = Float.NaN;
            for (int i = 0; i < n; i++) {
                if (W[i] <= 0f) {
                    continue;
                }
                if (!Float.isNaN(prevEnd)) {
                    assertTrue("left=" + left + " 第" + i + "段重叠", prevEnd <= S[i] + 0.01f);
                }
                prevEnd = S[i] + W[i];
            }
        }
    }
}
