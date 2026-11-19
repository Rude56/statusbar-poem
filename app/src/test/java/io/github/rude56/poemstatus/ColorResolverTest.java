package io.github.rude56.poemstatus;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ColorResolverTest {
    @Test
    public void systemTintWinsOverEverything() {

        ColorResolver.Result r = ColorResolver.resolve(0xFFFFFFFF, 0xFF333333, false, 0xFF202020);
        assertEquals(ColorResolver.Source.TINT, r.source);
        assertEquals(0xFFFFFFFF, r.color);
        assertFalse(r.lightBackground);

        ColorResolver.Result dark = ColorResolver.resolve(0xFF000000, 0xFFFFFFFF, false, 0);
        assertEquals(ColorResolver.Source.TINT, dark.source);
        assertEquals(0xFF000000, dark.color);
        assertTrue(dark.lightBackground);
    }

    @Test
    public void clockColorUsedWhenTintMissing() {
        ColorResolver.Result r = ColorResolver.resolve(0, 0xFF333333, null, 0);
        assertEquals(ColorResolver.Source.CLOCK, r.source);
        assertEquals(0xFF333333, r.color);
        assertTrue("深色字说明是浅背景", r.lightBackground);
    }

    @Test
    public void appearanceUsedWhenNoColorAvailable() {
        ColorResolver.Result dark = ColorResolver.resolve(0, 0, false, 0);
        assertEquals(ColorResolver.Source.APPEARANCE, dark.source);
        assertEquals(ColorResolver.DEFAULT_LIGHT_TEXT, dark.color);
        assertFalse(dark.lightBackground);

        ColorResolver.Result light = ColorResolver.resolve(0, 0, true, 0);
        assertEquals(ColorResolver.Source.APPEARANCE, light.source);
        assertEquals(ColorResolver.DEFAULT_DARK_TEXT, light.color);
        assertTrue(light.lightBackground);
    }

    @Test
    public void moduleTextUsedWhenClockAndAppearanceMissing() {
        ColorResolver.Result r = ColorResolver.resolve(0, 0, null, 0xFF202020);
        assertEquals(ColorResolver.Source.MODULE_TEXT, r.source);
        assertEquals(0xFF202020, r.color);
    }

    @Test
    public void defaultIsLightText() {
        ColorResolver.Result r = ColorResolver.resolve(0, 0, null, 0);
        assertEquals(ColorResolver.Source.DEFAULT, r.source);
        assertEquals(ColorResolver.DEFAULT_LIGHT_TEXT, r.color);
    }

    @Test
    public void tintFlipsWithAppBackground() {

        int darkApp = ColorResolver.resolve(0xFFFFFFFF, 0, false, 0).color;
        int lightApp = ColorResolver.resolve(0xFF000000, 0, true, 0).color;
        assertEquals(ColorResolver.DEFAULT_LIGHT_TEXT, darkApp);
        assertEquals(ColorResolver.DEFAULT_DARK_TEXT, lightApp);
        assertTrue("两者必须相反", darkApp != lightApp);
    }

    @Test
    public void diagnosticFormat() {
        String d = ColorResolver.resolve(0xFFFFFFFF, 0, false, 0).diagnostic();
        assertTrue(d.startsWith("POEM-COLOR src=tint"));
        assertTrue(d.contains("bg=dark"));
        assertTrue(d.contains("light=false"));
        assertTrue(d.contains("fg=#FFFFFFFF"));
    }

    @Test
    public void luminanceAndLightDetection() {
        assertFalse(ColorResolver.isLightBackgroundFor(0xFFFFFFFF));
        assertTrue(ColorResolver.isLightBackgroundFor(0xFF000000));
        assertTrue(ColorResolver.luminance(0xFFFFFFFF) > 0.9f);
        assertTrue(ColorResolver.luminance(0xFF000000) < 0.1f);
        assertTrue(ColorResolver.isUsableTextColor(0xFFFFFFFF));
        assertFalse(ColorResolver.isUsableTextColor(0));
        assertFalse(ColorResolver.isUsableTextColor(0x00FFFFFF));
    }

    @Test
    public void holeListRoundTrips() {

        java.util.List<HoleRects.Hole> holes = HoleRects.parse("100:200,300:340");
        assertEquals(2, holes.size());
        assertEquals(100, holes.get(0).left);
        assertEquals(200, holes.get(0).right);
        assertEquals(40, holes.get(1).width());

        String stored = HoleRects.encode(holes);
        java.util.List<HoleRects.Hole> back = HoleRects.parse(stored);
        assertEquals(2, back.size());
        assertEquals(300, back.get(1).left);

        assertTrue(HoleRects.encode(null).isEmpty());
        assertTrue(HoleRects.parse(HoleRects.encode(null)).isEmpty());
    }
    @Test
    public void holeListRejectsGarbageAndSorts() {

        java.util.List<HoleRects.Hole> holes = HoleRects.parse("abc,5:x,300:400,50:90:wormhole");
        assertEquals(2, holes.size());
        assertEquals(50, holes.get(0).left);
        assertEquals(300, holes.get(1).left);
        assertEquals(HoleRects.Mode.WORMHOLE, holes.get(0).mode);
        assertTrue(HoleRects.parse((String) null).isEmpty());
        assertTrue(HoleRects.parse("").isEmpty());

        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < 40; k++) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(k * 10).append(':').append(k * 10 + 5);
        }
        assertEquals(HoleRects.MAX_HOLES, HoleRects.parse(sb.toString()).size());
    }

    @Test
    public void modeParsesBothWays() {
        assertEquals(HoleRects.Mode.TUNNEL, HoleRects.parseMode(HoleRects.MODE_TUNNEL));
        assertEquals(HoleRects.Mode.WORMHOLE, HoleRects.parseMode(HoleRects.MODE_WORMHOLE));

        assertEquals(HoleRects.Mode.WORMHOLE, HoleRects.parseMode("garbage"));
        assertEquals(HoleRects.MODE_TUNNEL, HoleRects.modeName(HoleRects.Mode.TUNNEL));
    }
    @Test
    public void subtractTunnelsRemovesTheCoveredSpan() {

        java.util.List<HoleRects.Hole> holes = HoleRects.parse("100:200:tunnel");
        float[] l = new float[4];
        float[] r = new float[4];
        int n = HoleRects.subtractTunnels(holes, 50f, 300f, l, r, 0);
        assertEquals(2, n);
        assertEquals(50f, l[0], 0.01f);
        assertEquals(100f, r[0], 0.01f);
        assertEquals(200f, l[1], 0.01f);
        assertEquals(300f, r[1], 0.01f);

        n = HoleRects.subtractTunnels(holes, 0f, 90f, l, r, 0);
        assertEquals(1, n);
        assertEquals(0f, l[0], 0.01f);
        assertEquals(90f, r[0], 0.01f);

        n = HoleRects.subtractTunnels(HoleRects.parse("100:200:wormhole"), 50f, 300f, l, r, 0);
        assertEquals("虫洞不得被减掉", 1, n);
        assertEquals(50f, l[0], 0.01f);
        assertEquals(300f, r[0], 0.01f);

        n = HoleRects.subtractTunnels(null, 10f, 20f, l, r, 2);
        assertEquals(3, n);
        assertEquals(10f, l[2], 0.01f);
    }

    @Test
    public void newHoleAvoidsExistingOnes() {
        java.util.List<HoleRects.Hole> holes = new ArrayList<>();

        int[] first = HoleRects.findFreeSpot(holes, 1440, 100);
        assertNotNull(first);
        holes.add(new HoleRects.Hole(first[0], first[1]));

        int[] second = HoleRects.findFreeSpot(holes, 1440, 100);
        assertNotNull(second);
        assertFalse("新洞不能与已有洞重叠",
                second[1] > first[0] && second[0] < first[1]);

        holes.add(new HoleRects.Hole(second[0], second[1]));
        while (holes.size() < 8) {
            int[] spot = HoleRects.findFreeSpot(holes, 1440, 100);
            if (spot == null) {
                break;
            }
            for (HoleRects.Hole h : holes) {
                assertFalse("第 " + holes.size() + " 个洞重叠",
                        spot[1] > h.left && spot[0] < h.right);
            }
            holes.add(new HoleRects.Hole(spot[0], spot[1]));
        }
        assertTrue("800px 的洞应该能全部放下", holes.size() >= 8);

        java.util.List<HoleRects.Hole> full = new ArrayList<>();
        full.add(new HoleRects.Hole(0, 1440));
        assertNull(HoleRects.findFreeSpot(full, 1440, 100));
        assertNull(HoleRects.findFreeSpot(new ArrayList<>(), 0, 100));
        assertNull(HoleRects.findFreeSpot(new ArrayList<>(), 1440, 0));
        assertNull(HoleRects.findFreeSpot(new ArrayList<>(), 1440, 2000));
    }

    @Test
    public void overlappingHolesAreRejectedOnParse() {

        java.util.List<HoleRects.Hole> holes = HoleRects.parse(
                "100:300:wormhole,200:400:tunnel,500:600:wormhole");
        assertEquals("相交的洞必须被丢弃", 2, holes.size());
        assertEquals(100, holes.get(0).left);
        assertEquals(300, holes.get(0).right);
        assertEquals(500, holes.get(1).left);
        assertEquals(2, HoleRects.parse("100:200:wormhole,200:300:tunnel").size());
    }
    @Test
    public void sanitizeKeepsNonOverlappingOrderAndIsStable() {

        java.util.List<HoleRects.Hole> in = HoleRects.parse("0:10:wormhole,20:30:tunnel,40:50:wormhole");
        java.util.List<HoleRects.Hole> out = HoleRects.sanitize(in);
        assertEquals(3, out.size());
        for (int i = 0; i < 3; i++) {
            assertEquals(in.get(i).left, out.get(i).left);
            assertEquals(in.get(i).right, out.get(i).right);
            assertEquals(in.get(i).mode, out.get(i).mode);
        }

        assertTrue(HoleRects.sanitize(null).isEmpty());
        assertTrue(HoleRects.sanitize(new ArrayList<>()).isEmpty());
        java.util.List<HoleRects.Hole> one = new ArrayList<>();
        one.add(new HoleRects.Hole(5, 10));
        assertEquals(1, HoleRects.sanitize(one).size());
    }

    @Test
    public void overlapDetectionRespectsExceptSlot() {
        java.util.List<HoleRects.Hole> holes = HoleRects.parse("100:300:wormhole:0,500:600:tunnel:1");
        assertTrue(HoleRects.overlapsAny(holes, 250, 350, -1));
        assertFalse(HoleRects.overlapsAny(holes, 350, 450, -1));

        assertFalse("编辑自身不应判定为重合", HoleRects.overlapsAny(holes, 100, 300, 0));

        assertFalse(HoleRects.overlapsAny(holes, 300, 400, -1));

        HoleRects.Hole clash = HoleRects.firstOverlap(holes, 550, 650, -1);
        assertNotNull(clash);
        assertEquals(500, clash.left);
        assertNull(HoleRects.firstOverlap(holes, 700, 800, -1));
    }

    @Test
    public void wormholeKeepsTextIntact() {

        java.util.List<HoleRects.Hole> holes = HoleRects.parse("600:700:wormhole");
        float[] s = new float[8];
        float[] t = new float[8];
        float[] w = new float[8];
        int n = HoleRects.wormholeSegments(holes, 400f, 1000f, 0, s, t, w, 8);
        assertTrue("至少要有一段", n >= 1);
        float total = 0f;
        for (int i = 0; i < n; i++) {
            total += w[i];
        }
        assertEquals("文字不得丢失", 600f, total, 0.01f);

        n = HoleRects.wormholeSegments(holes, 1200f, 1500f, 0, s, t, w, 8);
        assertEquals(1, n);
        assertEquals(1200f, s[0], 0.01f);
    }
    @Test
    public void wormholeKeepsEveryCharacter() {

        java.util.List<HoleRects.Hole> holes = HoleRects.parse("600:700:wormhole");
        float[] s = new float[8];
        float[] t = new float[8];
        float[] w = new float[8];
        int n = HoleRects.wormholeSegments(holes, 200f, 500f, 0, s, t, w, 8);
        float total = 0f;
        for (int i = 0; i < n; i++) {
            total += w[i];
        }

        assertEquals(300f, total, 0.01f);
    }
    @Test
    public void tunnelDoesNotJump() {

        java.util.List<HoleRects.Hole> holes = HoleRects.parse("300:400:tunnel");
        float[] s = new float[4];
        float[] t = new float[4];
        float[] w = new float[4];
        int n = HoleRects.wormholeSegments(holes, 200f, 500f, 0, s, t, w, 4);
        assertEquals(1, n);
        assertEquals(200f, s[0], 0.01f);
        assertEquals(200f, t[0], 0.01f);
        assertEquals(300f, w[0], 0.01f);
    }

    @Test
    public void wormholeSegmentsCoverTextWithoutLoss() {

        java.util.List<HoleRects.Hole> holes = HoleRects.parse("300:400:wormhole");
        float[] s = new float[8];
        float[] t = new float[8];
        float[] w = new float[8];
        for (float left = 900f; left >= -300f; left -= 13f) {
            int n = HoleRects.wormholeSegments(holes, left, left + 400f, 0, s, t, w, 8);
            assertTrue("至少要有一段", n >= 1);
            for (int i = 0; i < n; i++) {
                assertTrue("段宽必须为正 @" + left, w[i] > 0f);
                assertEquals("文字与屏幕宽度必须相同（纯平移）@" + left,
                        w[i], w[i], 0.001f);
            }
        }
    }
}
