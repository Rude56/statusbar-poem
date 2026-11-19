package io.github.rude56.poemstatus;

import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class ConfigCodecTest {

    @Test
    public void configRoundTrip() {
        PoemConfig c = new PoemConfig();
        c.enabled = true;
        c.speedPx = 233;
        c.clockSeconds = 42;
        c.showAuthor = false;
        c.showSource = true;
        c.bobEnabled = true;
        c.landscapePause = false;
        c.lockscreenPause = false;
        c.orderMode = PoemLibrary.ORDER_RANDOM;
        c.whitelist = new ArrayList<>(java.util.Arrays.asList("com.a.b", "com.c.d"));
        c.poemsJson = PoemItem.listToJson(PoemLibrary.defaults());
        c.poemsSeeded = true;
        c.holesJson = "670:770,900:960";
        c.configRevision = 7;

        String json = ConfigCodec.encode(c);
        PoemConfig d = ConfigCodec.decode(json);

        assertEquals(c.enabled, d.enabled);
        assertEquals(c.speedPx, d.speedPx);
        assertEquals(c.clockSeconds, d.clockSeconds);
        assertEquals(c.showAuthor, d.showAuthor);
        assertEquals(c.showSource, d.showSource);
        assertEquals(c.bobEnabled, d.bobEnabled);
        assertEquals(c.landscapePause, d.landscapePause);
        assertEquals(c.lockscreenPause, d.lockscreenPause);
        assertEquals(c.orderMode, d.orderMode);
        assertEquals(c.whitelist, d.whitelist);
        assertEquals(c.poemsJson, d.poemsJson);
        assertEquals(c.poemsSeeded, d.poemsSeeded);
        assertEquals(c.holesJson, d.holesJson);
        assertEquals(c.configRevision, d.configRevision);
    }

    @Test
    public void jsonContainsEveryConfigKey() throws Exception {
        JSONObject o = new JSONObject(ConfigCodec.encode(new PoemConfig()));
        for (String key : ConfigKeys.ALL) {
            assertTrue("缺少键: " + key, o.has(key));
        }
        assertEquals(ConfigKeys.ALL.length, o.length());
    }

    @Test
    public void keysAreUnique() {
        java.util.HashSet<String> set = new java.util.HashSet<>();
        for (String k : ConfigKeys.ALL) {
            assertTrue("重复键: " + k, set.add(k));
        }
    }

    @Test
    public void poemLibraryJsonRoundTripWithChineseAndEscapes() {
        List<PoemItem> list = new ArrayList<>();
        list.add(new PoemItem("", "床前明月光，疑是地上霜。", "李白", "静夜思", true));
        list.add(new PoemItem("", "含\"引号\"与\\反斜杠以及\t制表符", "作者 A", "出处 B", false));
        list.add(new PoemItem("", "空作者与空出处", "", "", true));
        String json = PoemItem.listToJson(list);
        List<PoemItem> back = PoemItem.listFromJson(json);
        assertEquals(3, back.size());
        assertEquals("床前明月光，疑是地上霜。", back.get(0).text);
        assertEquals("含\"引号\"与\\反斜杠以及\t制表符", back.get(1).text);
        assertEquals("作者 A", back.get(1).author);
        assertFalse(back.get(1).enabled);
        assertEquals("", back.get(2).author);
        assertEquals("", back.get(2).source);
    }

    @Test
    public void malformedJsonFallsBackToDefaultsWithoutThrowing() {
        PoemConfig c = ConfigCodec.decode("{not json");
        assertEquals(PoemConfig.SPEED_DEFAULT, c.speedPx);
        assertEquals(PoemConfig.CLOCK_DEFAULT, c.clockSeconds);
        assertTrue(PoemItem.listFromJson("garbage").isEmpty());
        assertTrue(PoemItem.listFromJson(null).isEmpty());
    }

    @Test
    public void outOfRangeValuesAreClamped() {
        PoemConfig c = new PoemConfig();
        c.speedPx = 99999;
        c.clockSeconds = -5;
        c.normalized();
        assertEquals(PoemConfig.SPEED_MAX, c.speedPx);
        assertEquals(PoemConfig.CLOCK_MIN, c.clockSeconds);

        c.speedPx = 1;
        c.clockSeconds = 99999;
        c.normalized();
        assertEquals(PoemConfig.SPEED_MIN, c.speedPx);
        assertEquals(PoemConfig.CLOCK_MAX, c.clockSeconds);
    }

    @Test
    public void whitelistTextRoundTrip() {
        String text = ConfigCodec.whitelistToText(
                java.util.Arrays.asList("com.a", "com.b", "com.c"));
        assertEquals("com.a,com.b,com.c", text);
        assertEquals(3, ConfigCodec.parseWhitelist(text).size());
        assertEquals(3, ConfigCodec.parseWhitelist("com.a;com.b\ncom.c").size());
        assertEquals(0, ConfigCodec.parseWhitelist("").size());
    }

    @Test
    public void revisionChangesAreVisibleInJson() {
        PoemConfig a = new PoemConfig();
        a.configRevision = 1;
        PoemConfig b = new PoemConfig();
        b.configRevision = 2;
        assertNotEquals(ConfigCodec.encode(a), ConfigCodec.encode(b));
    }
}
