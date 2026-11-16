package io.github.rude56.poemstatus;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 * 配置层的不变量。
 *
 * <p>这些属性看起来「显然成立」，但配置要跨两进程、跨两次启动、还要能被手改，
 * 任何一处不对称都会变成用户侧「我改的东西没了 / 变成另一种了」。
 * 所以这里用<b>属性断言</b>而不是逐字段列举 —— 尤其是 {@link #copyCoversEveryField}，
 * 它用反射保证「新增字段但忘了加进 copy()」会在测试里立刻失败。
 */
public class ConfigInvariantTest {

    /**
     * {@code copy()} 必须覆盖<b>每一个</b>实例字段。
     *
     * <p>为什么用反射：{@code copy()} 是手写的字段罗列，新增字段时最容易漏。
     * 漏了不会编译报错，只会在运行时表现为「某个开关保存后不生效」——
     * 而这正是最难查的一类 bug：UI 上明明拨过去了，SystemUI 那边永远是旧值。
     * 注释里写着「漏改会立刻在编译期暴露」，实际上编译期<b>不会</b>暴露，
     * 所以必须靠这条测试兜住。
     */
    @Test
    public void copyCoversEveryField() throws Exception {
        PoemConfig src = new PoemConfig();
        // 给每个字段一个与默认值不同的值，才能看出「漏拷贝」。
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
        src.holesJson = "1:2:wormhole:0";
        src.holeMode = HoleRects.MODE_TUNNEL;
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

    /** copy() 必须是深拷贝：改副本的白名单不能影响原件。 */
    @Test
    public void copyIsDeepForWhitelist() {
        PoemConfig src = new PoemConfig();
        src.whitelist = new ArrayList<>(Arrays.asList("a.b.c"));
        PoemConfig copy = src.copy();
        copy.whitelist.add("x.y.z");
        assertEquals("改副本不能影响原件", 1, src.whitelist.size());
    }

    /**
     * 配置编解码必须往返无损。
     *
     * <p>这条覆盖「App 存 → Provider 编码 → SystemUI 解码」整条链路：
     * 任何一侧漏字段，用户看到的就是「设置不生效」。
     */
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
        c.holesJson = "100:200:tunnel:1,300:400:wormhole:0";
        c.holeMode = HoleRects.MODE_TUNNEL;
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
        assertEquals("洞配置必须逐字一致", c.holesJson, back.holesJson);
        assertEquals(c.holeMode, back.holeMode);
        assertEquals(c.configRevision, back.configRevision);
        // 二次往返也必须稳定（幂等）。
        assertEquals(ConfigCodec.encode(back), ConfigCodec.encode(ConfigCodec.decode(ConfigCodec.encode(back))));
    }

    /**
     * {@code holesJson} 若是一段 JSON 数组，也必须保住每个洞的模式与槽位。
     *
     * <p>曾经这里把洞重新拼成 {@code "l:r"} 两字段形式，
     * <b>模式和槽位被直接丢掉</b>，于是隧道洞被当成虫洞、颜色也全乱了。
     * 虽然正常写入走的是 {@code HoleRects.encode()}（不是 JSON），
     * 但「兼容旧格式」的这段代码不该比正常路径更不保真 ——
     * 它存在的意义恰恰是把旧数据读对。
     */
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

    /** 存储格式的往返：模式与槽位都不能丢。 */
    @Test
    public void holeEncodingRoundTripKeepsEverything() {
        String raw = "100:200:tunnel:2,300:400:wormhole:0";
        List<HoleRects.Hole> holes = HoleRects.parse(raw);
        assertEquals(2, holes.size());
        assertEquals(raw, HoleRects.encode(holes));

        // 空列表往返为空串，再解析回空列表。
        assertEquals("", HoleRects.encode(new ArrayList<>()));
        assertTrue(HoleRects.parse("").isEmpty());
        assertTrue(HoleRects.parse(null).isEmpty());
    }

    /** 诗库文本格式往返：作者/出处都不能丢，也不能多套一层《》。 */
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
        // 再导出一次必须逐字相同（幂等）。
        assertEquals(text, PoemTextCodec.encode(back));
    }

    /**
     * 导入自己刚导出的内容，不应产生任何新条目。
     *
     * <p>这是「导出再导入」最容易被破坏的性质：只要 id 计算或去重规则有一边走偏，
     * 用户每导入一次就多一份副本。
     */
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

    /** 诗库清空后，内置诗不得再被合并回来（poemsSeeded 一旦为 true 就永久生效）。 */
    @Test
    public void seededEmptyLibraryStaysEmpty() {
        assertTrue(PoemLibrary.load(true, "[]").isEmpty());
        assertTrue(PoemLibrary.load(true, "").isEmpty());
        assertEquals(PoemLibrary.BUILTIN_COUNT, PoemLibrary.load(false, "").size());
        // 已初始化过的普通库原样返回。
        assertEquals(1, PoemLibrary.load(true,
                "[{\"text\":\"x\",\"author\":\"\",\"source\":\"\",\"enabled\":true}]").size());
    }
}
