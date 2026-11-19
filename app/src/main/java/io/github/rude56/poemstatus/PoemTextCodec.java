package io.github.rude56.poemstatus;

import java.util.ArrayList;
import java.util.List;

public final class PoemTextCodec {
    public static final char SEPARATOR = '|';
    private PoemTextCodec() {
    }
    public static String encode(List<PoemItem> items) {
        StringBuilder sb = new StringBuilder();
        if (items != null) {
            for (PoemItem p : items) {
                if (p == null || p.text == null || p.text.trim().isEmpty()) {
                    continue;
                }
                sb.append(p.text.trim());
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
    public static List<PoemItem> decode(String text) {
        List<PoemItem> out = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return out;
        }

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

            source = stripBookMarks(source);
            out.add(new PoemItem("", poemText, author, source, true));
        }
        return out;
    }
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
    private static String stripBookMarks(String source) {
        String s = source;
        while (s.length() >= 2 && s.charAt(0) == '《' && s.charAt(s.length() - 1) == '》') {
            s = s.substring(1, s.length() - 1).trim();
        }
        return s;
    }

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
