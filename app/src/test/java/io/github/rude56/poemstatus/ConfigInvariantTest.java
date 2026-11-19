package io.github.rude56.poemstatus;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class ConfigInvariantTest {
    @Test
    public void copyCoversEveryField() throws Exception {
        PoemConfig src = new PoemConfig();
        src.enabled = true;
        src.speedPx = 433;
        src.clockSeconds = 77;
        src.showAuthor = false;
        src.showSource = false;
        src.bobEnabled = true;
        src.landscapePause = false;
        src.lockscreenPause = false;
        src.orderMode = PoemLibrary.ORDER_RANDOM;
        src.whitelist = new ArrayList<>(Arrays.asList("a.b.c"));
        src.poemsJson = "[{\"text\":\"x\"}]";
        src.poemsSeeded = true;
        src.whitelistSeeded = true;
        src.holesJson = "1:2:wormhole:0";
        src.configRevision = 987654321L;

        PoemConfig copy = src.copy();

        List<String> missed = new ArrayList<>();
        for (Field f : PoemConfig.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) || f.isSynthetic()) {
                continue;
            }
            f.setAccessible(true);
            Object a = f.get(src);
            Object b = f.get(copy);
            boolean same = (a == null) ? (b == null) : a.equals(b);
            if (!same) {
                missed.add(f.getName() + " (" + a + " -> " + b + ")");
            }
        }
        assertTrue("copy() 漏了这些字段: " + missed, missed.isEmpty());
    }

    @Test
    public void copyIsDeepForWhitelist() {
        PoemConfig src = new PoemConfig();
        src.whitelist = new ArrayList<>(Arrays.asList("a.b.c"));
        PoemConfig copy = src.copy();
        copy.whitelist.add("x.y.z");
        assertEquals("改副本不能影响原件", 1, src.whitelist.size());
    }

    @Test
    public void codecRoundTripIsLossless() {
        PoemConfig c = new PoemConfig();
        c.enabled = true;
        c.speedPx = 211;
        c.clockSeconds = 45;
        c.showAuthor = false;
        c.showSource = true;
        c.bobEnabled = true;
        c.landscapePause = false;
        c.lockscreenPause = false;
        c.orderMode = PoemLibrary.ORDER_RANDOM;
        c.whitelist = new ArrayList<>(Arrays.asList("p.one", "p.two"));
        c.poemsJson = "[{\"id\":\"i\",\"text\":\"t\",\"author\":\"a\",\"source\":\"s\",\"enabled\":true}]";
        c.poemsSeeded = true;
        c.whitelistSeeded = true;
        c.holesJson = "100:200:tunnel:1,300:400:wormhole:0";
        c.configRevision = 42;
        PoemConfig back = ConfigCodec.decode(ConfigCodec.encode(c));

        assertEquals(c.enabled, back.enabled);
        assertEquals(c.speedPx, back.speedPx);
        assertEquals(c.clockSeconds, back.clockSeconds);
        assertEquals(c.showAuthor, back.showAuthor);
        assertEquals(c.showSource, back.showSource);
        assertEquals(c.bobEnabled, back.bobEnabled);
        assertEquals(c.landscapePause, back.landscapePause);
        assertEquals(c.lockscreenPause, back.lockscreenPause);
        assertEquals(c.orderMode, back.orderMode);
        assertEquals(c.whitelist, back.whitelist);
        assertEquals(c.poemsJson, back.poemsJson);
        assertEquals(c.poemsSeeded, back.poemsSeeded);
        assertEquals(c.whitelistSeeded, back.whitelistSeeded);
        assertEquals("洞配置必须逐字一致", c.holesJson, back.holesJson);
        assertEquals(c.configRevision, back.configRevision);

        assertEquals(ConfigCodec.encode(back), ConfigCodec.encode(ConfigCodec.decode(ConfigCodec.encode(back))));
    }

    @Test
    public void jsonHolesKeepModeAndSlot() {
        PoemConfig c = new PoemConfig();
        c.holesJson = "[{\"l\":100,\"r\":200,\"m\":\"tunnel\",\"s\":3},"
                + "{\"l\":300,\"r\":400,\"m\":\"wormhole\",\"s\":1}]";

        List<HoleRects.Hole> holes = c.holes();
        assertEquals("两个洞都要读出来", 2, holes.size());
        assertEquals(100, holes.get(0).left);
        assertEquals(HoleRects.Mode.TUNNEL, holes.get(0).mode);
        assertEquals(3, holes.get(0).slot);
        assertEquals(HoleRects.Mode.WORMHOLE, holes.get(1).mode);
        assertEquals(1, holes.get(1).slot);
    }

    @Test
    public void holeEncodingRoundTripKeepsEverything() {

        String raw = "100:200:tunnel:2:1,300:400:wormhole:0:1";
        List<HoleRects.Hole> holes = HoleRects.parse(raw);
        assertEquals(2, holes.size());
        assertEquals(raw, HoleRects.encode(holes));
        List<HoleRects.Hole> legacy = HoleRects.parse("100:200:tunnel:2");
        assertEquals(1, legacy.size());
        assertTrue("旧配置的洞必须默认启用", legacy.get(0).enabled);

        List<HoleRects.Hole> disabled = HoleRects.parse("100:200:wormhole:0:0");
        assertEquals(1, disabled.size());
        assertFalse("enabled=0 必须解析成停用", disabled.get(0).enabled);
        assertEquals("停用的洞必须能重新编码", "100:200:wormhole:0:0", HoleRects.encode(disabled));

        assertEquals("", HoleRects.encode(new ArrayList<>()));
        assertTrue(HoleRects.parse("").isEmpty());
        assertTrue(HoleRects.parse(null).isEmpty());
    }

    @Test
    public void poemTextRoundTripIsLossless() {
        List<PoemItem> list = new ArrayList<>();
        list.add(new PoemItem("", "有出处", "作者甲", "出处乙", true));
        list.add(new PoemItem("", "无出处", "作者丙", "", true));
        list.add(new PoemItem("", "全都没有", "", "", true));

        String text = PoemTextCodec.encode(list);
        List<PoemItem> back = PoemTextCodec.decode(text);

        assertEquals(list.size(), back.size());
        for (int i = 0; i < list.size(); i++) {
            assertEquals("第" + i + "首正文", list.get(i).text, back.get(i).text);
            assertEquals("第" + i + "首作者", list.get(i).author, back.get(i).author);
            assertEquals("第" + i + "首出处", list.get(i).source, back.get(i).source);
        }

        assertEquals(text, PoemTextCodec.encode(back));
    }

    @Test
    public void importingOwnExportAddsNothing() {
        List<PoemItem> list = new ArrayList<>();
        list.add(new PoemItem("", "会当凌绝顶，一览众山小。", "杜甫", "望岳", true));
        list.add(new PoemItem("", "上善若水。", "老子", "道德经", false));

        List<PoemItem> reimport = PoemTextCodec.decode(PoemTextCodec.encode(list));
        int[] result = PoemTextCodec.merge(list, reimport);

        assertEquals("不得新增", 0, result[0]);
        assertEquals("应全部判为重复", reimport.size(), result[1]);
        assertEquals("诗库大小不变", 2, list.size());
    }

    @Test
    public void seededEmptyLibraryStaysEmpty() {
        assertTrue(PoemLibrary.load(true, "[]").isEmpty());
        assertTrue(PoemLibrary.load(true, "").isEmpty());
        assertEquals(PoemLibrary.BUILTIN_COUNT, PoemLibrary.load(false, "").size());

        assertEquals(1, PoemLibrary.load(true,
                "[{\"text\":\"x\",\"author\":\"\",\"source\":\"\",\"enabled\":true}]").size());
    }
}
