package io.github.rude56.poemstatus;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class PoemLibraryTest {

    @Test
    public void defaultsHaveSixPoems() {
        List<PoemItem> list = PoemLibrary.defaults();
        assertEquals(6, list.size());
        assertEquals(6, PoemLibrary.BUILTIN_COUNT);
        for (PoemItem p : list) {
            assertFalse(p.text.isEmpty());
            assertFalse(p.id.isEmpty());
            assertTrue(p.enabled);
        }
    }

    @Test
    public void firstRunSeedsDefaults() {
        String json = PoemLibrary.resolveInitial(false, null);
        assertEquals(6, PoemItem.listFromJson(json).size());
    }

    @Test
    public void explicitEmptyLibraryStaysEmpty() {
        assertEquals("[]", PoemLibrary.resolveInitial(true, "[]"));
        assertTrue(PoemLibrary.load(true, "[]").isEmpty());
        assertEquals(0, PoemLibrary.enabledOnly(PoemLibrary.load(true, "[]")).size());
    }

    @Test
    public void deletedBuiltinNeverComesBack() {
        List<PoemItem> list = new ArrayList<>();
        list.add(new PoemItem("", "自定义诗句", "", "", true));
        String json = PoemItem.listToJson(list);
        List<PoemItem> reloaded = PoemLibrary.load(true, json);
        assertEquals(1, reloaded.size());
        assertEquals("自定义诗句", reloaded.get(0).text);
        assertFalse(PoemLibrary.isDuplicate(reloaded, "长风破浪会有时，直挂云帆济沧海。", "李白", "行路难", null));
    }

    @Test
    public void duplicateDetection() {
        List<PoemItem> list = PoemLibrary.defaults();
        assertTrue(PoemLibrary.isDuplicate(list, "长风破浪会有时，直挂云帆济沧海。", "李白", "行路难", null));
        assertFalse(PoemLibrary.isDuplicate(list, "长风破浪会有时，直挂云帆济沧海。", "李白", "行路难", list.get(0).id));
        assertNull(PoemLibrary.add(list, "长风破浪会有时，直挂云帆济沧海。", "李白", "行路难"));
        assertNotNull(PoemLibrary.add(list, "全新的一句诗", "某人", "某书"));
        assertEquals(7, list.size());
    }

    @Test
    public void editChangesIdAndKeepsEnabled() {
        List<PoemItem> list = PoemLibrary.defaults();
        PoemItem first = list.get(0).withEnabled(false);
        list.set(0, first);
        boolean ok = PoemLibrary.edit(list, first.id, "改过的诗句", "作者", "出处");
        assertTrue(ok);
        assertEquals("改过的诗句", list.get(0).text);
        assertFalse(list.get(0).enabled);
        assertFalse(list.get(0).id.equals(first.id));
    }

    @Test
    public void moveAndRemove() {
        List<PoemItem> list = PoemLibrary.defaults();
        String second = list.get(1).id;
        assertTrue(PoemLibrary.moveUp(list, second));
        assertEquals(second, list.get(0).id);
        assertTrue(PoemLibrary.moveDown(list, second));
        assertEquals(second, list.get(1).id);
        assertFalse(PoemLibrary.moveUp(list, list.get(0).id));
        assertFalse(PoemLibrary.moveDown(list, list.get(list.size() - 1).id));
        assertTrue(PoemLibrary.remove(list, second));
        assertEquals(5, list.size());
    }

    @Test
    public void charCountIgnoresAuthorAndSource() {
        PoemItem p = new PoemItem("", "床前明月光", "李白", "静夜思", true);
        assertEquals(5, p.charCount);
        assertEquals("床前明月光", p.displayText(false, false));
        assertEquals("床前明月光 —— 李白", p.displayText(true, false));
        assertEquals("床前明月光《静夜思》", p.displayText(false, true));
        assertEquals("床前明月光 —— 李白《静夜思》", p.displayText(true, true));
    }

    @Test
    public void stableIdIsDeterministicAndContentBased() {
        String a = PoemItem.stableId("abc", "作者", "出处");
        String b = PoemItem.stableId("abc", "作者", "出处");
        String c = PoemItem.stableId("abc2", "作者", "出处");
        assertEquals(a, b);
        assertFalse(a.equals(c));
        assertEquals(20, a.length());
    }

    @Test
    public void enabledOnlyFiltersDisabled() {
        List<PoemItem> list = PoemLibrary.defaults();
        list.set(0, list.get(0).withEnabled(false));
        assertEquals(5, PoemLibrary.enabledOnly(list).size());
    }

    private static PoemItem p(String text) {
        return new PoemItem("", text, "", "", true);
    }

    private static List<String> texts(List<PoemItem> list) {
        List<String> out = new ArrayList<>();
        for (PoemItem item : list) {
            out.add(item.text);
        }
        return out;
    }

    private static Set<String> idsOf(PoemItem... items) {
        Set<String> out = new HashSet<>();
        for (PoemItem item : items) {
            out.add(item.id);
        }
        return out;
    }

    @Test
    public void moveAmongVisibleSkipsFilteredOutPoems() {
        PoemItem a = p("甲");
        PoemItem b = p("乙");
        PoemItem hidden1 = p("丙");
        PoemItem hidden2 = p("丁");
        PoemItem c = p("戊");
        List<PoemItem> list = new ArrayList<>(Arrays.asList(a, b, hidden1, hidden2, c));

        Set<String> visible = idsOf(a, b, c);

        assertTrue(PoemLibrary.moveAmongVisible(list, a.id, visible, 1));
        assertEquals(Arrays.asList("乙", "甲", "丙", "丁", "戊"), texts(list));

        assertTrue(PoemLibrary.moveAmongVisible(list, a.id, visible, 1));
        assertEquals(Arrays.asList("乙", "戊", "丙", "丁", "甲"), texts(list));
    }
    @Test
    public void hiddenPoemsKeepTheirRelativeOrder() {
        PoemItem a = p("甲");
        PoemItem h1 = p("乙");
        PoemItem h2 = p("丙");
        PoemItem h3 = p("丁");
        PoemItem b = p("戊");
        List<PoemItem> list = new ArrayList<>(Arrays.asList(a, h1, h2, h3, b));
        Set<String> visible = idsOf(a, b);

        assertTrue(PoemLibrary.moveAmongVisible(list, a.id, visible, 1));

        assertEquals(Arrays.asList("戊", "乙", "丙", "丁", "甲"), texts(list));
        assertEquals(h1.id, list.get(1).id);
        assertEquals(h2.id, list.get(2).id);
        assertEquals(h3.id, list.get(3).id);
    }

    @Test
    public void moveStopsAtTheEndsWithoutTouchingTheList() {
        PoemItem a = p("甲");
        PoemItem b = p("乙");
        List<PoemItem> list = new ArrayList<>(Arrays.asList(a, b));
        Set<String> visible = idsOf(a, b);

        assertFalse("第一首不能再上移", PoemLibrary.moveAmongVisible(list, a.id, visible, -1));
        assertEquals(Arrays.asList("甲", "乙"), texts(list));

        assertFalse("最后一首不能再下移", PoemLibrary.moveAmongVisible(list, b.id, visible, 1));
        assertEquals(Arrays.asList("甲", "乙"), texts(list));
    }

    @Test
    public void moveStopsWhenOnlyHiddenPoemsRemainInThatDirection() {
        PoemItem a = p("甲");
        PoemItem h = p("乙");
        List<PoemItem> list = new ArrayList<>(Arrays.asList(a, h));
        Set<String> visible = idsOf(a);

        assertFalse(PoemLibrary.moveAmongVisible(list, a.id, visible, 1));
        assertEquals(Arrays.asList("甲", "乙"), texts(list));
    }

    @Test
    public void moveWithNoFilterIsPlainAdjacentSwap() {
        PoemItem a = p("甲");
        PoemItem b = p("乙");
        PoemItem c = p("丙");
        List<PoemItem> list = new ArrayList<>(Arrays.asList(a, b, c));
        Set<String> visible = idsOf(a, b, c);

        assertTrue(PoemLibrary.moveAmongVisible(list, c.id, visible, -1));
        assertEquals(Arrays.asList("甲", "丙", "乙"), texts(list));
    }

    @Test
    public void repeatedMovesNeverLoseOrDuplicatePoems() {
        List<PoemItem> list = new ArrayList<>(PoemLibrary.defaults());
        Set<String> all = new HashSet<>();
        for (PoemItem item : list) {
            all.add(item.id);
        }
        Set<String> visible = new HashSet<>();
        for (int i = 0; i < list.size(); i += 2) {
            visible.add(list.get(i).id);
        }

        String moving = list.get(0).id;
        for (int i = 0; i < 200; i++) {
            PoemLibrary.moveAmongVisible(list, moving, visible, (i % 3 == 0) ? -1 : 1);
            assertEquals("长度不能变", PoemLibrary.BUILTIN_COUNT, list.size());
            Set<String> now = new HashSet<>();
            for (PoemItem item : list) {
                now.add(item.id);
            }
            assertEquals("不能丢诗也不能重复", all, now);
        }
    }

    @Test
    public void moveIsSafeForDegenerateInput() {
        List<PoemItem> list = new ArrayList<>(Arrays.asList(p("甲")));
        assertFalse(PoemLibrary.moveAmongVisible(null, "x", idsOf(), 1));
        assertFalse(PoemLibrary.moveAmongVisible(list, null, idsOf(), 1));
        assertFalse(PoemLibrary.moveAmongVisible(list, list.get(0).id, null, 1));
        assertFalse(PoemLibrary.moveAmongVisible(list, list.get(0).id, idsOf(), 0));
        assertFalse(PoemLibrary.moveAmongVisible(list, "不存在的id", idsOf(), 1));
        assertEquals(1, list.size());    }
}
