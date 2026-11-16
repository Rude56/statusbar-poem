package io.github.rude56.poemstatus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

/**
 * 本地诗库：内置默认诗、增删改、排序、重复检测、播放顺序。
 * 内置 6 首只在首次初始化时写入一次；{@code poemsSeeded=true} 后即使诗库被删空也保持为空。
 */
public final class PoemLibrary {

    public static final String ORDER_SEQUENTIAL = "sequential";
    public static final String ORDER_RANDOM = "random";

    /** 内置 6 首；选常见、字数适中的句子，便于在状态栏里看清滚动。 */
    private static final String[][] BUILTIN = {
            {"长风破浪会有时，直挂云帆济沧海。", "李白", "行路难"},
            {"欲穷千里目，更上一层楼。", "王之涣", "登鹳雀楼"},
            {"会当凌绝顶，一览众山小。", "杜甫", "望岳"},
            {"黑发不知勤学早，白首方悔读书迟。", "颜真卿", "劝学"},
            {"沉舟侧畔千帆过，病树前头万木春。", "刘禹锡", "酬乐天扬州初逢席上见赠"},
            {"千磨万击还坚劲，任尔东西南北风。", "郑燮", "竹石"},
    };

    public static final int BUILTIN_COUNT = BUILTIN.length;

    private PoemLibrary() {
    }

    /** 内置默认诗库（每次返回新对象，调用方可随意修改）。 */
    public static List<PoemItem> defaults() {
        List<PoemItem> out = new ArrayList<>(BUILTIN_COUNT);
        for (String[] row : BUILTIN) {
            out.add(new PoemItem("", row[0], row[1], row[2], true));
        }
        return out;
    }

    public static String defaultsJson() {
        return PoemItem.listToJson(defaults());
    }

    /**
     * 首次初始化决策：已初始化过则原样返回 stored（显式空库保持 "[]"），否则返回内置诗库。
     */
    public static String resolveInitial(boolean seeded, String stored) {
        if (seeded) {
            return stored == null ? "[]" : stored;
        }
        return defaultsJson();
    }

    /** 解析诗库；已初始化过的空串按空库处理，未初始化时才给默认值。 */
    public static List<PoemItem> load(boolean seeded, String json) {
        return PoemItem.listFromJson(resolveInitial(seeded, json));
    }

    public static List<PoemItem> enabledOnly(List<PoemItem> items) {
        List<PoemItem> out = new ArrayList<>();
        if (items != null) {
            for (PoemItem p : items) {
                if (p != null && p.enabled && !p.text.isEmpty()) {
                    out.add(p);
                }
            }
        }
        return out;
    }

    /** 重复检测：以 text+author+source 的稳定 ID 为准；{@code exceptId} 为编辑场景下要排除的自身 ID。 */
    public static boolean isDuplicate(List<PoemItem> list, String text, String author, String source, String exceptId) {
        String id = PoemItem.stableId(text, author, source);
        if (exceptId != null && exceptId.equals(id)) {
            return false;
        }
        if (list == null) {
            return false;
        }
        for (PoemItem p : list) {
            if (p != null && id.equals(p.id)) {
                return true;
            }
        }
        return false;
    }

    /** 追加一条；重复或空诗句返回 null，调用方据此提示用户。 */
    public static PoemItem add(List<PoemItem> list, String text, String author, String source) {
        String t = text == null ? "" : text.trim();
        if (t.isEmpty()) {
            return null;
        }
        if (isDuplicate(list, t, author, source, null)) {
            return null;
        }
        PoemItem p = new PoemItem("", t, author, source, true);
        list.add(p);
        return p;
    }

    /** 编辑：内容变了就换新 ID（ID 由内容决定），并额外做重复校验。 */
    public static boolean edit(List<PoemItem> list, String id, String text, String author, String source) {
        String t = text == null ? "" : text.trim();
        if (t.isEmpty() || list == null) {
            return false;
        }
        if (isDuplicate(list, t, author, source, id)) {
            return false;
        }
        for (int i = 0; i < list.size(); i++) {
            PoemItem p = list.get(i);
            if (p.id.equals(id)) {
                list.set(i, new PoemItem("", t, author, source, p.enabled));
                return true;
            }
        }
        return false;
    }

    public static boolean remove(List<PoemItem> list, String id) {
        if (list == null) {
            return false;
        }
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id.equals(id)) {
                list.remove(i);
                return true;
            }
        }
        return false;
    }

    public static boolean moveUp(List<PoemItem> list, String id) {
        int i = indexOf(list, id);
        if (i <= 0) {
            return false;
        }
        Collections.swap(list, i, i - 1);
        return true;
    }

    public static boolean moveDown(List<PoemItem> list, String id) {
        int i = indexOf(list, id);
        if (i < 0 || i >= list.size() - 1) {
            return false;
        }
        Collections.swap(list, i, i + 1);
        return true;
    }

    public static int indexOf(List<PoemItem> list, String id) {
        if (list == null) {
            return -1;
        }
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id.equals(id)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 在「可见的诗」之间移动一格，被搜索过滤掉的诗原地不动（{@code step}：-1 上移，+1 下移）。
     * 必须用 swap 而不是 remove+insert：交换时被跨过的隐藏诗一个字节都不动。
     */
    public static boolean moveAmongVisible(List<PoemItem> list, String id,
                                           Set<String> visible, int step) {
        if (list == null || id == null || visible == null || step == 0) {
            return false;
        }
        int from = indexOf(list, id);
        if (from < 0) {
            return false;
        }
        int to = from + step;
        // 跳过被过滤掉的诗，直到遇到下一首可见的。
        while (to >= 0 && to < list.size() && !visible.contains(list.get(to).id)) {
            to += step;
        }
        if (to < 0 || to >= list.size()) {
            return false;
        }
        Collections.swap(list, from, to);
        return true;
    }

    /** 去重后的 ID 集合。 */
    public static Set<String> idSet(List<PoemItem> list) {
        Set<String> out = new HashSet<>();
        if (list != null) {
            for (PoemItem p : list) {
                if (p != null) {
                    out.add(p.id);
                }
            }
        }
        return out;
    }

    public static String normalizeOrder(String mode) {
        String m = mode == null ? "" : mode.trim().toLowerCase(Locale.ROOT);
        return ORDER_RANDOM.equals(m) ? ORDER_RANDOM : ORDER_SEQUENTIAL;
    }

    /** 带游标的播放器。顺序模式从第一首开始；随机模式保证不连续重复。 */
    public static final class Playback {
        private final List<PoemItem> source;
        private final String mode;
        private final Random random;
        private int cursor = -1;
        private int lastRandom = -1;

        public Playback(List<PoemItem> source, String mode, long seed) {
            this.source = source == null ? Collections.<PoemItem>emptyList() : source;
            this.mode = normalizeOrder(mode);
            this.random = new Random(seed);
        }

        public String mode() {
            return mode;
        }

        public int size() {
            return source.size();
        }

        public int cursor() {
            return cursor;
        }

        public PoemItem next() {
            if (source.isEmpty()) {
                return null;
            }
            if (ORDER_RANDOM.equals(mode)) {
                if (source.size() == 1) {
                    cursor = 0;
                    return source.get(0);
                }
                int pick;
                do {
                    pick = random.nextInt(source.size());
                } while (pick == lastRandom);
                lastRandom = pick;
                cursor = pick;
                return source.get(pick);
            }
            cursor = (cursor + 1) % source.size();
            return source.get(cursor);
        }
    }
}
