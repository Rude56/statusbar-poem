package io.github.rude56.poemstatus;

import java.util.ArrayList;
import java.util.List;

/**
 * 诗库的纯文本编解码：一行一首，字段用竖线分隔。
 *
 * <pre>
 * 长风破浪会有时，直挂云帆济沧海。|李白|行路难
 * 欲穷千里目，更上一层楼。|王之涣|登鹳雀楼
 * 千磨万击还坚劲，任尔东西南北风。
 * </pre>
 *
 * <p>格式刻意做得极简，因为用户可能用任何文本编辑器、备忘录、聊天窗口来编辑它。
 * 为此解析尽量宽容：
 * <ul>
 *   <li>作者/出处可省略（只有一句诗也能导入）；</li>
 *   <li>全角竖线 {@code ｜} 与制表符 {@code \t} 都当分隔符；</li>
 *   <li>空行、以 {@code #} 或 {@code //} 开头的行跳过；</li>
 *   <li>行首的序号（{@code 1. } / {@code 1、}）自动剥掉；</li>
 *   <li>行尾的 {@code 。} 保留 —— 它是诗句的一部分，不能替用户改。</li>
 * </ul>
 *
 * <p>导出的文本可以直接再导入，往返无损。
 */
public final class PoemTextCodec {

    /** 字段分隔符。 */
    public static final char SEPARATOR = '|';

    private PoemTextCodec() {
    }

    /**
     * 诗库 → 文本。
     *
     * @param items 诗句列表，null 视为空
     * @return 每行一首，行尾统一 {@code \n}；空库返回空串
     */
    public static String encode(List<PoemItem> items) {
        StringBuilder sb = new StringBuilder();
        if (items != null) {
            for (PoemItem p : items) {
                if (p == null || p.text == null || p.text.trim().isEmpty()) {
                    continue;
                }
                sb.append(p.text.trim());
                // 作者和出处都可能为空；只要有一个非空就要补分隔符，
                // 否则「诗|作者」会被解析成「诗」，作者丢失。
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

    /**
     * 文本 → 诗句列表。
     *
     * <p>返回的 {@link PoemItem} 都是 {@code enabled=true}，id 由内容哈希生成；
     * 去重由调用方（{@link PoemLibrary}）负责，这样导入可以报告「跳过几首重复」。
     *
     * @param text 待解析文本，null 或空返回空列表
     * @return 解析出的诗句，顺序与文本一致
     */
    public static List<PoemItem> decode(String text) {
        List<PoemItem> out = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return out;
        }
        // 统一换行符：Windows 的 \r\n、老 Mac 的 \r 都要能读。
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
            // 出处里多余的《》去掉，避免导出再导入后变成《《登鹳雀楼》》。
            source = stripBookMarks(source);
            out.add(new PoemItem("", poemText, author, source, true));
        }
        return out;
    }

    /**
     * 按竖线/全角竖线/制表符切分。
     *
     * <p>只切前两个分隔符：诗句本身可能含竖线（极少见），
     * 但出处里出现竖线的概率更低，宁可把多余的分隔符留在出处里也不要丢内容。
     */
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

    /** 去掉出处外层的《》，最多两层，防止重复导入越套越多。 */
    private static String stripBookMarks(String source) {
        String s = source;
        while (s.length() >= 2 && s.charAt(0) == '《' && s.charAt(s.length() - 1) == '》') {
            s = s.substring(1, s.length() - 1).trim();
        }
        return s;
    }

    /**
     * 把导入结果合并进现有诗库。
     *
     * <p>去重规则与手动添加一致（text+author+source 的稳定 ID），
     * 所以「导入自己刚导出的文件」不会产生任何重复。
     *
     * @param target 现有诗库（会被修改）
     * @param source 待导入诗句
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
