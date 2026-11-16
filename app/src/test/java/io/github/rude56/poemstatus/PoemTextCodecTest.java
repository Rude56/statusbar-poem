package io.github.rude56.poemstatus;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** 诗库纯文本（诗句|作者|出处）编解码测试。 */
public class PoemTextCodecTest {

    // ---------------------------------------------------------------- 解析

    @Test
    public void parsesThreeFields() {
        List<PoemItem> list = PoemTextCodec.decode(
                "长风破浪会有时，直挂云帆济沧海。|李白|行路难");
        assertEquals(1, list.size());
        assertEquals("长风破浪会有时，直挂云帆济沧海。", list.get(0).text);
        assertEquals("李白", list.get(0).author);
        assertEquals("行路难", list.get(0).source);
        assertTrue("导入的诗默认启用", list.get(0).enabled);
    }

    @Test
    public void authorAndSourceAreOptional() {
        List<PoemItem> list = PoemTextCodec.decode(
                "千磨万击还坚劲，任尔东西南北风。\n"
                        + "欲穷千里目，更上一层楼。|王之涣");
        assertEquals(2, list.size());
        assertEquals("", list.get(0).author);
        assertEquals("", list.get(0).source);
        assertEquals("王之涣", list.get(1).author);
        assertEquals("", list.get(1).source);
    }

    @Test
    public void skipsBlankLinesAndComments() {
        List<PoemItem> list = PoemTextCodec.decode(
                "\n\n# 这是注释\n// 这也是\n会当凌绝顶，一览众山小。|杜甫|望岳\n\n");
        assertEquals("空行与注释必须跳过", 1, list.size());
        assertEquals("杜甫", list.get(0).author);
    }

    @Test
    public void acceptsFullWidthPipeAndTab() {
        List<PoemItem> a = PoemTextCodec.decode("黑发不知勤学早，白首方悔读书迟。｜颜真卿｜劝学");
        List<PoemItem> b = PoemTextCodec.decode("黑发不知勤学早，白首方悔读书迟。\t颜真卿\t劝学");
        assertEquals("全角竖线要能识别", "颜真卿", a.get(0).author);
        assertEquals("制表符要能识别", "颜真卿", b.get(0).author);
        assertEquals("劝学", a.get(0).source);
        assertEquals("劝学", b.get(0).source);
    }

    @Test
    public void stripsLeadingLineNumbers() {
        List<PoemItem> list = PoemTextCodec.decode(
                "1. 沉舟侧畔千帆过，病树前头万木春。|刘禹锡|酬乐天\n"
                        + "2、千磨万击还坚劲，任尔东西南北风。|郑燮|竹石");
        assertEquals("沉舟侧畔千帆过，病树前头万木春。", list.get(0).text);
        assertEquals("千磨万击还坚劲，任尔东西南北风。", list.get(1).text);
    }

    @Test
    public void stripsNestedBookMarksFromSource() {
        List<PoemItem> list = PoemTextCodec.decode("会当凌绝顶，一览众山小。|杜甫|《望岳》");
        assertEquals("望岳", list.get(0).source);
        List<PoemItem> again = PoemTextCodec.decode("会当凌绝顶，一览众山小。|杜甫|《《望岳》》");
        assertEquals("望岳", again.get(0).source);
    }

    @Test
    public void handlesWindowsLineEndings() {
        List<PoemItem> list = PoemTextCodec.decode(
                "会当凌绝顶，一览众山小。|杜甫|望岳\r\n欲穷千里目，更上一层楼。|王之涣|登鹳雀楼\r\n");
        assertEquals(2, list.size());
        assertEquals("杜甫", list.get(0).author);
        assertEquals("王之涣", list.get(1).author);
    }

    @Test
    public void emptyInputYieldsEmptyList() {
        assertTrue(PoemTextCodec.decode(null).isEmpty());
        assertTrue(PoemTextCodec.decode("").isEmpty());
        assertTrue(PoemTextCodec.decode("   \n\n  ").isEmpty());
    }

    // ---------------------------------------------------------------- 导出

    @Test
    public void exportsOnePoemPerLine() {
        List<PoemItem> list = new ArrayList<>();
        list.add(new PoemItem("", "会当凌绝顶，一览众山小。", "杜甫", "望岳", true));
        list.add(new PoemItem("", "欲穷千里目，更上一层楼。", "王之涣", "登鹳雀楼", true));
        String text = PoemTextCodec.encode(list);
        String[] lines = text.split("\n");
        assertEquals(2, lines.length);
        assertEquals("会当凌绝顶，一览众山小。|杜甫|望岳", lines[0]);
        assertEquals("欲穷千里目，更上一层楼。|王之涣|登鹳雀楼", lines[1]);
    }

    @Test
    public void exportsEmptyFieldsCompactly() {
        List<PoemItem> list = new ArrayList<>();
        list.add(new PoemItem("", "只有诗句", "", "", true));
        assertEquals("只有诗句\n", PoemTextCodec.encode(list));

        List<PoemItem> withAuthor = new ArrayList<>();
        withAuthor.add(new PoemItem("", "有作者", "张三", "", true));
        // 出处为空时不能留下多余的分隔符。
        assertEquals("有作者|张三\n", PoemTextCodec.encode(withAuthor));
    }

    @Test
    public void roundTripIsLossless() {
        List<PoemItem> original = new ArrayList<>();
        original.add(new PoemItem("", "长风破浪会有时，直挂云帆济沧海。", "李白", "行路难", true));
        original.add(new PoemItem("", "没有作者的一句", "", "", true));
        original.add(new PoemItem("", "有作者无出处", "张三", "", true));
        original.add(new PoemItem("", "有出处无作者", "", "某书", true));

        String text = PoemTextCodec.encode(original);
        List<PoemItem> restored = PoemTextCodec.decode(text);

        assertEquals(original.size(), restored.size());
        for (int i = 0; i < original.size(); i++) {
            assertEquals(original.get(i).text, restored.get(i).text);
            assertEquals(original.get(i).author, restored.get(i).author);
            assertEquals(original.get(i).source, restored.get(i).source);
        }
    }

    @Test
    public void builtinLibraryRoundTrips() {
        List<PoemItem> builtin = PoemLibrary.defaults();
        List<PoemItem> restored = PoemTextCodec.decode(PoemTextCodec.encode(builtin));
        assertEquals(builtin.size(), restored.size());
        for (int i = 0; i < builtin.size(); i++) {
            assertEquals(builtin.get(i).text, restored.get(i).text);
            assertEquals(builtin.get(i).author, restored.get(i).author);
            assertEquals(builtin.get(i).source, restored.get(i).source);
        }
    }

    // ---------------------------------------------------------------- 合并

    @Test
    public void mergeSkipsDuplicates() {
        List<PoemItem> target = new ArrayList<>(PoemLibrary.defaults());
        int before = target.size();
        // 再导入一遍内置诗：全部应被跳过。
        int[] result = PoemTextCodec.merge(target, PoemTextCodec.decode(PoemTextCodec.encode(target)));
        assertEquals(before, target.size());
        assertEquals("重复的一个都不能加", 0, result[0]);
        assertEquals("重复的要全部计入跳过", before, result[1]);
    }

    @Test
    public void mergeAddsOnlyNewOnes() {
        List<PoemItem> target = new ArrayList<>();
        target.add(new PoemItem("", "会当凌绝顶，一览众山小。", "杜甫", "望岳", true));
        List<PoemItem> incoming = PoemTextCodec.decode(
                "会当凌绝顶，一览众山小。|杜甫|望岳\n"
                        + "欲穷千里目，更上一层楼。|王之涣|登鹳雀楼");
        int[] result = PoemTextCodec.merge(target, incoming);
        assertEquals(2, target.size());
        assertEquals(1, result[0]);
        assertEquals(1, result[1]);
        assertNotNull(target.get(1).id);
    }

    @Test
    public void mergeIntoEmptyLibraryAddsEverything() {
        List<PoemItem> target = new ArrayList<>();
        List<PoemItem> incoming = PoemTextCodec.decode(
                "a|作者A|出处A\nb|作者B|出处B\nc");
        int[] result = PoemTextCodec.merge(target, incoming);
        assertEquals(3, target.size());
        assertEquals(3, result[0]);
        assertEquals(0, result[1]);
    }
}
