package io.github.rude56.poemstatus;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

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

        List<HoleRects.Hole> holes = HoleRects.parse("700:800:wormhole");
        int n = HoleRects.wormholeSegments(holes, 1200f, 1500f, 0, S, T, W, 12);
        assertEquals(1, n);
        assertEquals("未到洞口不得位移", 1200f, S[0], 0.01f);
        assertEquals(1200f, T[0], 0.01f);
        assertEquals(300f, W[0], 0.01f);
    }

            @Test
    public void segmentsNeverOverlapAndNeverLoseText() {

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

        List<HoleRects.Hole> holes = HoleRects.parse("700:800:wormhole");

        int n = HoleRects.wormholeSegments(holes, 650f, 1250f, 0, S, T, W, 12);
        float leftmost = Float.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            leftmost = Math.min(leftmost, S[i]);
        }

        assertEquals("左端应比无洞时早一个洞宽离屏", 550f, leftmost, 0.01f);
    }

    @Test
    public void tunnelNeverShiftsAnything() {
        List<HoleRects.Hole> holes = HoleRects.parse("700:800:tunnel");
        int n = HoleRects.wormholeSegments(holes, 300f, 600f, 0, S, T, W, 12);
        assertEquals(1, n);
        assertEquals("隧道不产生任何位移", 300f, S[0], 0.01f);
        assertEquals(300f, T[0], 0.01f);
        assertEquals(300f, W[0], 0.01f);
    }

    @Test
    public void holesNeverAffectTheStatusBar() throws java.io.IOException {

        java.io.File f = new java.io.File(
                "src/main/java/io/github/rude56/poemstatus/PoemRuntime.java");
        assertTrue("必须能读到源码: " + f, f.isFile());
        String rt = new String(java.nio.file.Files.readAllBytes(f.toPath()),
                java.nio.charset.StandardCharsets.UTF_8);
        assertTrue("源码读取异常", rt.length() > 1000);
        assertFalse("不得用用户洞列表直接挖状态栏",
                rt.contains("HolePuncher.updatePoemSegments(configHoles"));
    }

    @Test
    public void secondWormholeIsNotShiftedByTheFirst() {
        List<HoleRects.Hole> holes = HoleRects.parse("400:500:wormhole,900:1000:wormhole");

        int n = HoleRects.wormholeSegments(holes, 1800f, 3200f, 0, S, T, W, 12);
        assertEquals("未到任何门不得位移", 1800f, S[0], 0.01f);

        n = HoleRects.wormholeSegments(holes, 700f, 1700f, 0, S, T, W, 12);
        assertEquals("跨过洞口必须切成两段", 2, n);
        assertEquals("左段左移一个洞宽", 600f, S[0], 0.01f);
        assertEquals(700f, T[0], 0.01f);
        assertEquals(300f, W[0], 0.01f);
        assertEquals("右段不位移", 1000f, S[1], 0.01f);
        assertEquals(1000f, T[1], 0.01f);
        assertEquals(700f, W[1], 0.01f);
        n = HoleRects.wormholeSegments(holes, 1100f, 2100f, 0, S, T, W, 12);
        assertEquals("两个洞都穿过 → 不位移", 1100f, S[0], 0.01f);
    }
    @Test
    public void twoWormholesStretchByBothWidths() {
        List<HoleRects.Hole> holes = HoleRects.parse("400:500:wormhole,900:1000:wormhole");
        int n = HoleRects.wormholeSegments(holes, 300f, 1300f, 0, S, T, W, 12);
        assertEquals("字一个都不能丢", 1000f, totalWidth(n), 0.01f);

        assertEquals("必须被撑开两个洞宽", 1200f, span(n), 0.01f);
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

    @Test
    public void secondPortalFiresAtTheRightMomentAcrossAllFrames() {
        List<HoleRects.Hole> holes = HoleRects.parse("400:500:wormhole,900:1000:wormhole");
        final float textW = 1400f;

        final float gate2 = 1000f;
        final float gate1 = 600f;
        for (float left = 1800f; left >= -400f; left -= 1f) {
            int n = HoleRects.wormholeSegments(holes, left, left + textW, 0, S, T, W, 12);

            assertEquals("left=" + left + " 丢字", textW, totalWidth(n), 0.01f);
            for (int i = 0; i < n; i++) {
                if (W[i] <= 0f) {
                    continue;
                }
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
