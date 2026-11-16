package io.github.rude56.poemstatus;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/**
 * 一条诗句。不可变对象，App 与 SystemUI 共用；不保存像素宽度，
 * 宽度必须在 SystemUI 进程内按真实 Paint 测量后放进 LRU 缓存。
 */
public final class PoemItem {

    public final String id;
    public final String text;
    public final String author;
    public final String source;
    public final boolean enabled;
    /** 诗句本身的码点数（不含作者/出处），用于上限校验与诊断。 */
    public final int charCount;

    public PoemItem(String id, String text, String author, String source, boolean enabled) {
        this.text = text == null ? "" : text.trim();
        this.author = author == null ? "" : author.trim();
        this.source = source == null ? "" : source.trim();
        this.id = (id == null || id.isEmpty()) ? stableId(this.text, this.author, this.source) : id;
        this.enabled = enabled;
        this.charCount = this.text.codePointCount(0, this.text.length());
    }

    public PoemItem withEnabled(boolean value) {
        return new PoemItem(id, text, author, source, value);
    }

    /**
     * 实际显示文本：作者/出处被关掉时不拼接，
     * 因此文字宽度、滚动时长、遮挡包围盒都基于真正的显示内容计算。
     */
    public String displayText(boolean showAuthor, boolean showSource) {
        if (!showAuthor && !showSource) {
            return text;
        }
        StringBuilder out = new StringBuilder(text.length() + 24);
        out.append(text);
        if (showAuthor && !author.isEmpty()) {
            out.append(" —— ").append(author);
        }
        if (showSource && !source.isEmpty()) {
            out.append("《").append(source).append('》');
        }
        return out.toString();
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("id", id);
            o.put("text", text);
            o.put("author", author);
            o.put("source", source);
            o.put("enabled", enabled);
        } catch (Throwable ignored) {
            // org.json 在 Android 上对字符串 put 不抛异常；这里只是防御性兜底。
        }
        return o;
    }

    public static PoemItem fromJson(JSONObject o) {
        if (o == null) {
            return null;
        }
        String text = o.optString("text", "");
        if (text.trim().isEmpty()) {
            return null;
        }
        return new PoemItem(
                o.optString("id", ""),
                text,
                o.optString("author", ""),
                o.optString("source", ""),
                o.optBoolean("enabled", true));
    }

    public static List<PoemItem> listFromJson(String json) {
        List<PoemItem> out = new ArrayList<>();
        if (json == null || json.trim().isEmpty()) {
            return out;
        }
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                PoemItem p = fromJson(arr.optJSONObject(i));
                if (p != null) {
                    out.add(p);
                }
            }
        } catch (Throwable ignored) {
            // 损坏的 JSON 当作空库处理：宁可暂时不播放，也不能让 SystemUI 崩。
        }
        return out;
    }

    public static String listToJson(List<PoemItem> items) {
        JSONArray arr = new JSONArray();
        try {
            if (items != null) {
                for (PoemItem p : items) {
                    if (p != null && !p.text.isEmpty()) {
                        arr.put(p.toJson());
                    }
                }
            }
        } catch (Throwable ignored) {
            // 极端实现下也不抛，兜底防御。
        }
        return arr.toString();
    }

    /** ID 由 文本 + 作者 + 出处 的稳定哈希生成，编辑内容即产生新 ID。 */
    public static String stableId(String text, String author, String source) {
        String raw = (text == null ? "" : text.trim()) + "\u0000"
                + (author == null ? "" : author.trim()) + "\u0000"
                + (source == null ? "" : source.trim());
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(20);
            for (int i = 0; i < 10; i++) {
                sb.append(Character.forDigit((digest[i] >> 4) & 0xF, 16));
                sb.append(Character.forDigit(digest[i] & 0xF, 16));
            }
            return sb.toString();
        } catch (Throwable ignored) {
            return "h" + Integer.toHexString(raw.hashCode());
        }
    }
}
