package io.github.rude56.poemstatus;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class HoleRects {
    /** 虫洞 = 传送门：字到洞口右沿立刻出现在左沿，洞口里不画。 */
    public static final String MODE_WORMHOLE = "wormhole";
    /** 隧道 = 只遮古诗、不遮状态栏图标。 */
    public static final String MODE_TUNNEL = "tunnel";
    public static final int MAX_HOLES = 8;
    private HoleRects() {
    }
    public static final class Hole {
        public final int left;
        public final int right;

        public final Mode mode;

        public final int slot;

        public final boolean enabled;

        public Hole(int left, int right) {
            this(left, right, Mode.WORMHOLE, -1, true);
        }

        public Hole(int left, int right, Mode mode) {
            this(left, right, mode, -1, true);
        }

        public Hole(int left, int right, Mode mode, int slot) {
            this(left, right, mode, slot, true);
        }
        public Hole(int left, int right, Mode mode, int slot, boolean enabled) {
            this.left = left;
            this.right = right;
            this.mode = mode == null ? Mode.WORMHOLE : mode;
            this.slot = slot;
            this.enabled = enabled;
        }
        public Hole withSlot(int newSlot) {
            return new Hole(left, right, mode, newSlot, enabled);
        }
        public Hole withRange(int newLeft, int newRight) {
            return new Hole(newLeft, newRight, mode, slot, enabled);
        }

        public Hole withEnabled(boolean newEnabled) {
            return new Hole(left, right, mode, slot, newEnabled);
        }

        public int width() {
            return right - left;
        }

        public boolean valid() {
            return right > left;
        }

        public boolean isWormhole() {
            return mode == Mode.WORMHOLE;
        }
    }

    public enum Mode {WORMHOLE, TUNNEL}

    public static Mode parseMode(String raw) {
        return MODE_TUNNEL.equals(raw) ? Mode.TUNNEL : Mode.WORMHOLE;
    }

    public static String modeName(Mode m) {
        return m == Mode.TUNNEL ? MODE_TUNNEL : MODE_WORMHOLE;
    }

    public static List<Hole> parse(String raw) {
        List<Hole> out = new ArrayList<>();
        if (raw == null || raw.trim().isEmpty()) {
            return out;
        }
        for (String part : raw.split(",")) {
            String p = part.trim();
            if (p.isEmpty()) {
                continue;
            }
            String[] seg = p.split(":");
            if (seg.length < 2) {
                continue;
            }
            try {
                int l = Integer.parseInt(seg[0].trim());
                int r = Integer.parseInt(seg[1].trim());
                Mode m = seg.length >= 3 ? parseMode(seg[2].trim()) : Mode.WORMHOLE;
                int slot = -1;

                boolean enabled = seg.length < 5 || !"0".equals(seg[4].trim());
                if (seg.length >= 4) {
                    try {
                        slot = Integer.parseInt(seg[3].trim());
                    } catch (NumberFormatException ignored) {
                        slot = -1;
                    }
                }
                Hole h = new Hole(Math.max(0, l), Math.max(0, r), m, slot, enabled);
                if (h.valid()) {
                    out.add(h);
                }
            } catch (NumberFormatException ignored) {
            }
            if (out.size() >= MAX_HOLES) {
                break;
            }
        }
        out.sort((a, b) -> Integer.compare(a.left, b.left));
        return assignSlots(sanitize(out));
    }

    public static List<Hole> assignSlots(List<Hole> holes) {
        if (holes == null || holes.isEmpty()) {
            return holes;
        }
        boolean[] used = new boolean[MAX_HOLES];
        for (Hole h : holes) {
            if (h != null && h.slot >= 0 && h.slot < MAX_HOLES) {
                if (used[h.slot]) {

                    continue;
                }
                used[h.slot] = true;
            }
        }
        List<Hole> out = new ArrayList<>(holes.size());
        boolean[] taken = new boolean[MAX_HOLES];
        for (Hole h : holes) {
            if (h == null) {
                continue;
            }
            if (h.slot >= 0 && h.slot < MAX_HOLES && !taken[h.slot]) {
                taken[h.slot] = true;
                out.add(h);
            } else {

                int free = -1;
                for (int i = 0; i < MAX_HOLES; i++) {
                    if (!taken[i]) {
                        free = i;
                        break;
                    }
                }
                if (free < 0) {
                    free = out.size() % MAX_HOLES;
                }
                taken[free] = true;
                out.add(h.withSlot(free));
            }
        }
        return out;
    }
    public static List<Hole> sanitize(List<Hole> holes) {
        List<Hole> out = new ArrayList<>();
        if (holes == null || holes.isEmpty()) {
            return out;
        }
        List<Hole> sorted = new ArrayList<>(holes.size());
        for (Hole h : holes) {
            if (h != null && h.valid()) {
                sorted.add(h);
            }
        }
        sorted.sort((a, b) -> Integer.compare(a.left, b.left));
        for (Hole h : sorted) {
            if (out.size() >= MAX_HOLES) {
                break;
            }
            if (out.isEmpty()) {
                out.add(h);
                continue;
            }
            Hole last = out.get(out.size() - 1);
            if (h.left >= last.right) {
                out.add(h);
            }

        }
        return out;
    }

    public static boolean overlapsAny(List<Hole> holes, int left, int right, int exceptSlot) {
        if (holes == null || right <= left) {
            return false;
        }
        for (int i = 0; i < holes.size(); i++) {
            Hole h = holes.get(i);
            if (h == null || !h.valid() || !h.enabled) {
                continue;
            }
            if (exceptSlot >= 0 && h.slot == exceptSlot) {
                continue;
            }
            if (right > h.left && left < h.right) {
                return true;
            }
        }
        return false;
    }
    public static Hole firstOverlap(List<Hole> holes, int left, int right, int exceptSlot) {
        if (holes == null || right <= left) {
            return null;
        }
        for (int i = 0; i < holes.size(); i++) {
            Hole h = holes.get(i);
            if (h == null || !h.valid() || !h.enabled) {
                continue;
            }
            if (exceptSlot >= 0 && h.slot == exceptSlot) {
                continue;
            }
            if (right > h.left && left < h.right) {
                return h;
            }
        }
        return null;
    }

    public static int nextFreeSlot(List<Hole> holes) {
        boolean[] used = new boolean[MAX_HOLES];
        if (holes != null) {
            for (Hole h : holes) {
                if (h != null && h.slot >= 0 && h.slot < MAX_HOLES) {
                    used[h.slot] = true;
                }
            }
        }
        for (int i = 0; i < MAX_HOLES; i++) {
            if (!used[i]) {
                return i;
            }
        }
        return 0;
    }

    public static String encode(List<Hole> holes) {
        if (holes == null || holes.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Hole h : holes) {

            if (h == null || !h.valid()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(h.left).append(':').append(h.right)
                    .append(':').append(modeName(h.mode))
                    .append(':').append(h.slot)

                    .append(':').append(h.enabled ? "1" : "0");
        }
        return sb.toString();
    }

    public static List<Hole> fromJson(String json) {
        List<Hole> out = new ArrayList<>();
        if (json == null || json.trim().isEmpty()) {
            return out;
        }
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length() && out.size() < MAX_HOLES; i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) {
                    continue;
                }
                Hole h = new Hole(o.optInt("l", 0), o.optInt("r", 0),
                        parseMode(o.optString("m", MODE_WORMHOLE)), o.optInt("s", -1));
                if (h.valid()) {
                    out.add(h);
                }
            }
        } catch (Throwable ignored) {

        }
        out.sort((a, b) -> Integer.compare(a.left, b.left));
        return assignSlots(sanitize(out));
    }

    public static int[] findFreeSpot(List<Hole> holes, int screenW, int wantWidth) {
        if (screenW <= 0 || wantWidth <= 0 || wantWidth > screenW) {
            return null;
        }

        List<int[]> used = new ArrayList<>();
        if (holes != null) {
            for (Hole h : holes) {
                if (h != null && h.valid()) {
                    used.add(new int[]{h.left, h.right});
                }
            }
        }
        used.sort((a, b) -> Integer.compare(a[0], b[0]));

        int center = Math.max(0, Math.min(screenW - wantWidth, (screenW - wantWidth) / 2));
        for (int step = 0; step * wantWidth <= screenW; step++) {
            int[] candidates = {
                    center + step * wantWidth,
                    center - step * wantWidth};
            for (int c : candidates) {
                if (c < 0 || c + wantWidth > screenW) {
                    continue;
                }
                if (fits(used, c, c + wantWidth)) {
                    return new int[]{c, c + wantWidth};
                }
            }
        }
        return null;
    }

    private static boolean fits(List<int[]> used, int left, int right) {
        for (int i = 0; i < used.size(); i++) {
            int[] u = used.get(i);
            if (right > u[0] && left < u[1]) {
                return false;
            }
        }
        return true;
    }

    public static int subtractTunnels(List<Hole> holes, float from, float to,
                                      float[] outLeft, float[] outRight, int count) {
        if (outLeft == null || outRight == null || to <= from) {
            return count;
        }
        int n = count;
        float cursor = from;
        if (holes != null) {
            for (int i = 0; i < holes.size(); i++) {
                Hole h = holes.get(i);
                if (h == null || !h.valid() || !h.enabled || h.isWormhole()) {
                    continue;
                }
                float hl = h.left;
                float hr = h.right;
                if (hr <= cursor || hl >= to) {
                    continue;
                }
                if (hl > cursor && n < outLeft.length) {
                    outLeft[n] = cursor;
                    outRight[n] = Math.min(hl, to);
                    n++;
                }
                cursor = Math.max(cursor, hr);
            }
        }
        if (cursor < to && n < outLeft.length) {
            outLeft[n] = cursor;
            outRight[n] = to;
            n++;
        }
        return n;
    }

    public static int visibleSegments(List<Hole> holes,
                                      float[] segScreen, float[] segWidth, int segCount,
                                      float trackLeft, float trackRight,
                                      int originX, int barW,
                                      float[] outLeft, float[] outRight) {
        if (outLeft == null || outRight == null) {
            return 0;
        }
        final float barL = originX;
        final float barR = barW > 0 ? (float) originX + barW : Float.MAX_VALUE;

        if (segCount > 0 && segScreen != null && segWidth != null) {
            final int limit = Math.min(segCount, Math.min(segScreen.length, segWidth.length));
            int n = 0;
            for (int i = 0; i < limit; i++) {
                float w = segWidth[i];
                if (w <= 0f) {
                    continue;
                }

                float l = segScreen[i] + originX;
                float r = l + w;
                if (l < barL) {
                    l = barL;
                }
                if (r > barR) {
                    r = barR;
                }
                if (r <= l) {
                    continue;
                }
                n = subtractTunnels(holes, l, r, outLeft, outRight, n);
            }
            return n;
        }

        float l = Math.max(trackLeft + originX, barL);
        float r = Math.min(trackRight + originX, barR);
        if (r <= l) {
            return 0;
        }
        return subtractTunnels(holes, l, r, outLeft, outRight, 0);
    }
    public static float finishLeft(List<Hole> holes, float textWidth) {
        return -textWidth + totalWormholeWidth(holes);
    }
    public static float totalWormholeWidth(List<Hole> holes) {
        float sum = 0f;
        if (holes != null) {
            for (int i = 0; i < holes.size(); i++) {
                Hole h = holes.get(i);
                if (h != null && h.valid() && h.enabled && h.isWormhole()) {
                    sum += h.width();
                }
            }
        }
        return sum;
    }

    public static int wormholeSegments(List<Hole> holes, float textLeft, float textRight,
                                       int originX, float[] segScreen, float[] segText,
                                       float[] segWidth, int maxSegments) {
        if (segScreen == null || segText == null || segWidth == null
                || maxSegments <= 0 || textRight <= textLeft
                || holes == null || holes.isEmpty()) {
            return 0;
        }

        int n = 0;
        int count = 0;
        for (int i = 0; i < holes.size() && count < MAX_HOLES; i++) {
            Hole h = holes.get(i);
            if (h == null || !h.valid() || !h.enabled || !h.isWormhole()) {
                continue;
            }
            holeLeft[count] = h.left - originX;
            holeRight[count] = h.right - originX;
            holeW[count] = h.width();
            count++;
        }
        for (int i = 1; i < count; i++) {
            float hl = holeLeft[i];
            float hr = holeRight[i];
            float w = holeW[i];
            int j = i - 1;
            while (j >= 0 && holeLeft[j] > hl) {
                holeLeft[j + 1] = holeLeft[j];
                holeRight[j + 1] = holeRight[j];
                holeW[j + 1] = holeW[j];
                j--;
            }
            holeLeft[j + 1] = hl;
            holeRight[j + 1] = hr;
            holeW[j + 1] = w;
        }
        float rightSum = 0f;
        for (int i = count - 1; i >= 0; i--) {
            holeTrigger[i] = holeRight[i] + rightSum;
            rightSum += holeW[i];
        }

        float shift = 0f;
        for (int i = 0; i < count; i++) {
            if (holeTrigger[i] > textLeft) {
                shift += holeW[i];
            }
        }
        float cursor = textLeft;
        for (int i = 0; i < count && n < maxSegments; i++) {
            final float bp = holeTrigger[i];
            if (bp <= textLeft) {
                continue;
            }
            if (bp >= textRight) {
                break;
            }
            if (bp > cursor) {
                segScreen[n] = cursor - shift;
                segText[n] = cursor;
                segWidth[n] = bp - cursor;
                n++;
                if (n >= maxSegments) {
                    return n;
                }
                cursor = bp;
            }
            shift -= holeW[i];
            if (shift < 0f) {
                shift = 0f;
            }
        }
        if (textRight > cursor && n < maxSegments) {
            segScreen[n] = cursor - shift;
            segText[n] = cursor;
            segWidth[n] = textRight - cursor;
            n++;
        }
        return n;
    }

    private static final float[] holeLeft = new float[MAX_HOLES];
    private static final float[] holeRight = new float[MAX_HOLES];
    private static final float[] holeW = new float[MAX_HOLES];
    private static final float[] holeTrigger = new float[MAX_HOLES];
}
