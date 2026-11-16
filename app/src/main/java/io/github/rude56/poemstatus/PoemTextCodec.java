package io.github.rude56.poemstatus;

import java.util.ArrayList;
import java.util.List;

/**
 * 诗库的纯文本编解码：一行一首，字段用竖线分隔，作者/出处可省略，全角竖线与制表符同样当分隔符。
 * 导出文本可原样再导入，往返无损；行尾的 {@code 。} 属于诗句内容，不得剥除。
 */
public final class PoemTextCodec {

    /** 字段分隔符。 */
    public static final char SEPARATOR = '|';

    private PoemTextCodec() {
    }

    /** 诗库 → 文本：每行一首，行尾统一 {@code \n}；空库返回空串。 */
    public static String encode(List<PoemItem> items) {
        StringBuilder sb = new StringBuilder();
        if (items != null) {
            for (PoemItem p : items) {
                if (p == null || p.text == null || p.text.trim().isEmpty()) {
                    continue;
                }
                sb.append(p.text.trim());
                // 作者为空但出处非空时也要补空字段占位，否则出处会被解析成作者。
                if (!p.author.isEmpty() || !p.source.isEmpty()) {
                    sb.append(SEPARATOR).append(p.author);
                }
                if (!p.source.isEmpty()) {
                    sb.append(SEPARATOR).append(p.source);
                }
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    /** 文本 → 诗句列表，顺序与文本一致；去重由调用方（{@link PoemLibrary}）负责。 */
    public static List<PoemItem> decode(String text) {
        List<PoemItem> out = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return out;
        }
        // 统一换行符：\r\n 与 \r 都要能读。
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        for (String rawLine : normalized.split("\n")) {
            String line = stripLeadingNumber(rawLine.trim());
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) {
                continue;
            }
            String[] parts = split(line);
            String poemText = parts[0].trim();
            if (poemText.isEmpty()) {
                continue;
            }
            String author = parts.length > 1 ? parts[1].trim() : "";
            String source = parts.length > 2 ? parts[2].trim() : "";
            // 剥掉出处自带的《》，否则导出再导入会层层叠加。
            source = stripBookMarks(source);
            out.add(new PoemItem("", poemText, author, source, true));
        }
        return out;
    }

    /** 只切前两个分隔符：多余分隔符留在出处里，宁可多留也不丢内容。 */
    private static String[] split(String line) {
        String prepared = line.replace('｜', SEPARATOR).replace('\t', SEPARATOR);
        int first = prepared.indexOf(SEPARATOR);
        if (first < 0) {
            return new String[]{prepared};
        }
        int second = prepared.indexOf(SEPARATOR, first + 1);
        if (second < 0) {
            return new String[]{
                    prepared.substring(0, first),
                    prepared.substring(first + 1),
            };
        }
        return new String[]{
                prepared.substring(0, first),
                prepared.substring(first + 1, second),
                prepared.substring(second + 1),
        };
    }

    /** 剥掉行首的「1. 」「1、」「1) 」这类序号。 */
    private static String stripLeadingNumber(String line) {
        int i = 0;
        while (i < line.length() && Character.isDigit(line.charAt(i))) {
            i++;
        }
        if (i == 0 || i >= line.length()) {
            return line;
        }
        char c = line.charAt(i);
        if (c == '.' || c == '、' || c == ')' || c == '．') {
            return line.substring(i + 1).trim();
        }
        return line;
    }

    /** 去掉出处外层的《》，防止重复导入越套越多。 */
    private static String stripBookMarks(String source) {
        String s = source;
        while (s.length() >= 2 && s.charAt(0) == '《' && s.charAt(s.length() - 1) == '》') {
            s = s.substring(1, s.length() - 1).trim();
        }
        return s;
    }

    /**
     * 把导入结果合并进现有诗库：去重规则与手动添加一致（text+author+source 的稳定 ID），
     * 导入自己刚导出的文件不会产生重复。
     *
     * @return {新增数量, 跳过数量}
     */
    public static int[] merge(List<PoemItem> target, List<PoemItem> source) {
        int added = 0;
        int skipped = 0;
        if (target == null || source == null) {
            return new int[]{0, 0};
        }
        for (PoemItem p : source) {
            if (p == null || p.text.isEmpty()) {
                continue;
            }
            if (PoemLibrary.isDuplicate(target, p.text, p.author, p.source, null)) {
                skipped++;
            } else {
                target.add(p);
                added++;
            }
        }
        return new int[]{added, skipped};
    }
}
