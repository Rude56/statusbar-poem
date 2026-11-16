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

/** 诗库：默认 6 首、显式空库保持为空、增删改排序、重复检测、升级不复活。 */
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
        // 用户删掉了前 5 首（含内置），只剩 1 首自定义
        List<PoemItem> list = new ArrayList<>();
        list.add(new PoemItem("", "自定义诗句", "", "", true));
        String json = PoemItem.listToJson(list);
        // 升级后再次加载：seeded=true，不能把内置 6 首合并回来
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

    // ---------------------------------------------------------------- 搜索态排序

    /** 造一首诗，文本就是它的标识，方便断言顺序。 */
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

    /**
     * 核心回归：搜索过滤后拖动，只能和「看得见的诗」换位。
     *
     * <p>旧实现按整表下标交换，用户拖 A（可见）往下时，实际是 A 和它下面
     * 那首<b>被过滤掉的</b>诗换位 —— 屏幕上什么都不动，松手后顺序已变。
     */
    @Test
    public void moveAmongVisibleSkipsFilteredOutPoems() {
        PoemItem a = p("甲");
        PoemItem b = p("乙");
        PoemItem hidden1 = p("丙");
        PoemItem hidden2 = p("丁");
        PoemItem c = p("戊");
        List<PoemItem> list = new ArrayList<>(Arrays.asList(a, b, hidden1, hidden2, c));

        // 只看得见甲、乙、戊
        Set<String> visible = idsOf(a, b, c);

        // 把「甲」下移一格：和下一首可见的「乙」交换。
        assertTrue(PoemLibrary.moveAmongVisible(list, a.id, visible, 1));
        assertEquals(Arrays.asList("乙", "甲", "丙", "丁", "戊"), texts(list));

        // 再下移一格：跳过两首隐藏诗，和「戊」交换。
        // 隐藏诗原地不动 —— 若实现成「删除再插入」，这里会变成 乙 戊 丙 丁 甲。
        assertTrue(PoemLibrary.moveAmongVisible(list, a.id, visible, 1));
        assertEquals(Arrays.asList("乙", "戊", "丙", "丁", "甲"), texts(list));
    }

    /** 被过滤掉的诗在整次移动里一个字节都不许动。 */
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
        // 隐藏的三首必须还待在原来的下标上，顺序仍是 乙 丙 丁
        assertEquals(Arrays.asList("戊", "乙", "丙", "丁", "甲"), texts(list));
        assertEquals(h1.id, list.get(1).id);
        assertEquals(h2.id, list.get(2).id);
        assertEquals(h3.id, list.get(3).id);
    }

    /** 到头了就返回 false，列表一个字节都不许改。 */
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

    /** 中间只剩隐藏诗时也不能越界。 */
    @Test
    public void moveStopsWhenOnlyHiddenPoemsRemainInThatDirection() {
        PoemItem a = p("甲");
        PoemItem h = p("乙");
        List<PoemItem> list = new ArrayList<>(Arrays.asList(a, h));
        Set<String> visible = idsOf(a);

        assertFalse(PoemLibrary.moveAmongVisible(list, a.id, visible, 1));
        assertEquals(Arrays.asList("甲", "乙"), texts(list));
    }

    /** 全可见时行为必须退化成普通的相邻交换。 */
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

    /**
     * 反复移动必须能走遍所有可见位置，且不丢诗、不重复。
     *
     * <p>这是「拖动几十次之后列表会不会坏掉」的等价断言：
     * 每次移动都只做一次交换，长度和 id 集合必须始终不变。
     */
    @Test
    public void repeatedMovesNeverLoseOrDuplicatePoems() {
        List<PoemItem> list = new ArrayList<>(PoemLibrary.defaults());
        Set<String> all = new HashSet<>();
        for (PoemItem item : list) {
            all.add(item.id);
        }
        // 只让奇数下标的诗可见，模拟搜索命中一半
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

    /** 参数异常一律返回 false，不抛异常、不改列表。 */
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
