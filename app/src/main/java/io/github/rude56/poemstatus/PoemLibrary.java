package io.github.rude56.poemstatus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

public final class PoemLibrary {
    public static final String ORDER_SEQUENTIAL = "sequential";
    public static final String ORDER_RANDOM = "random";
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

    public static String resolveInitial(boolean seeded, String stored) {
        if (seeded) {
            return stored == null ? "[]" : stored;
        }
        return defaultsJson();
    }
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

        while (to >= 0 && to < list.size() && !visible.contains(list.get(to).id)) {
            to += step;
        }
        if (to < 0 || to >= list.size()) {
            return false;
        }
        Collections.swap(list, from, to);
        return true;
    }
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
