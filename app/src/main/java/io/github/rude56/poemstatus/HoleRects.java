package io.github.rude56.poemstatus;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 自定义「洞」：状态栏里若干横向矩形，高度恒为状态栏高，只需左/右边界。
 * 两种模式：WORMHOLE 洞内的字不参与排版（诗句跳过该宽度），TUNNEL 照常滚动但洞内像素被遮住。
 */
public final class HoleRects {

    public static final String MODE_WORMHOLE = "wormhole";
    public static final String MODE_TUNNEL = "tunnel";

    /** 最多允许的洞数量：再多也只会互相重叠，且拖慢每帧计算。 */
    public static final int MAX_HOLES = 8;

    private HoleRects() {
    }

    /** 一个洞：屏幕坐标的左/右边界，以及它自己的穿越模式。 */
    public static final class Hole {
        public final int left;
        public final int right;
        /** 这个洞单独的模式：WORMHOLE（吞掉诗句）/ TUNNEL（遮住诗句）。 */
        public final Mode mode;
        /**
         * 颜色槽位：颜色跟随槽位而不是列表下标，增删/重排其它洞都不会让它换色。
         * -1 表示未指定，解析时按排序下标补上。
         */
        public final int slot;

        public Hole(int left, int right) {
            this(left, right, Mode.WORMHOLE, -1);
        }

        public Hole(int left, int right, Mode mode) {
            this(left, right, mode, -1);
        }

        public Hole(int left, int right, Mode mode, int slot) {
            this.left = left;
            this.right = right;
            this.mode = mode == null ? Mode.WORMHOLE : mode;
            this.slot = slot;
        }

        /** 换一个颜色槽位（其余不变）。 */
        public Hole withSlot(int newSlot) {
            return new Hole(left, right, mode, newSlot);
        }

        /** 换左右边界（其余不变）。供 UI 复用同一个对象时避免每帧 new。 */
        public Hole withRange(int newLeft, int newRight) {
            return new Hole(newLeft, newRight, mode, slot);
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

    /**
     * 把存储用的 "left:right[:mode][:slot],..." 解析成洞列表。
     *
     * mode 省略时默认虫洞；slot 省略时按排序下标补上。非法段直接丢弃。
     * 返回的列表保证两两不重合（见 {@link #sanitize}）。
     */
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
                if (seg.length >= 4) {
                    try {
                        slot = Integer.parseInt(seg[3].trim());
                    } catch (NumberFormatException ignored) {
                        slot = -1;
                    }
                }
                Hole h = new Hole(Math.max(0, l), Math.max(0, r), m, slot);
                if (h.valid()) {
                    out.add(h);
                }
            } catch (NumberFormatException ignored) {
                // 跳过这一段。
            }
            if (out.size() >= MAX_HOLES) {
                break;
            }
        }
        out.sort((a, b) -> Integer.compare(a.left, b.left));
        return assignSlots(sanitize(out));
    }

    /**
     * 给没有槽位的洞补上槽位，并保证同一列表里槽位互不冲突。
     *
     * 规则：已有合法槽位保持不动；缺槽位的洞取「当前已用的最小空槽」。
     * 这样增删洞后颜色稳定，新加的洞也不会抢走别人的颜色。
     */
    public static List<Hole> assignSlots(List<Hole> holes) {
        if (holes == null || holes.isEmpty()) {
            return holes;
        }
        boolean[] used = new boolean[MAX_HOLES];
        for (Hole h : holes) {
            if (h != null && h.slot >= 0 && h.slot < MAX_HOLES) {
                if (used[h.slot]) {
                    // 重复槽位：后出现的那个视为缺失。
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
                // 缺槽位或槽位冲突：找最小空槽。
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

    /**
     * 保证洞两两不重合：按左边界排序后顺序扫描，相交的洞整条丢弃，而不是裁剪到不重合。
     *
     * 重合必须禁止：两个洞叠在一起时该按虫洞还是隧道处理没有答案，结果取决于遍历顺序；
     * 遮挡分段按洞逐个切分，重叠还会让段边界互相抵消。裁剪则会悄悄改掉用户量出来的边界，比丢弃更难理解。
     * 相邻边相接（{@code right == next.left}）不算重合。
     *
     * 返回新列表，不改动入参。
     */
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
            // 否则与上一个洞相交：整条丢弃。
        }
        return out;
    }

    /**
     * 这个区间是否与列表里任一洞重合。
     *
     * @param exceptSlot 排除自己的槽位（编辑场景），没有则传 -1
     */
    public static boolean overlapsAny(List<Hole> holes, int left, int right, int exceptSlot) {
        if (holes == null || right <= left) {
            return false;
        }
        for (int i = 0; i < holes.size(); i++) {
            Hole h = holes.get(i);
            if (h == null || !h.valid()) {
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

    /** 与给定区间重合的第一个洞；没有则返回 null（编辑页用于提示「和哪个洞撞了」）。 */
    public static Hole firstOverlap(List<Hole> holes, int left, int right, int exceptSlot) {
        if (holes == null || right <= left) {
            return null;
        }
        for (int i = 0; i < holes.size(); i++) {
            Hole h = holes.get(i);
            if (h == null || !h.valid()) {
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

    /** 新洞应该用哪个槽位：取当前没被占用的最小槽位。 */
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

    /** 洞列表 → 存储字符串（含每个洞自己的模式与颜色槽位）。 */
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
                    .append(':').append(h.slot);
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
            // 脏数据当作没有洞。
        }
        out.sort((a, b) -> Integer.compare(a.left, b.left));
        return assignSlots(sanitize(out));
    }

    /**
     * 为「新增洞」找一个不与已有洞重叠的位置：从屏幕中央向左右交替搜索，
     * 找到第一段长度 ≥ {@code wantWidth} 且与所有已有洞都不相交的空隙；都放不下时返回 {@code null}。
     *
     * @param holes     已有洞（会先排序）
     * @param screenW   屏幕宽度
     * @param wantWidth 期望宽度
     * @return {left, right}，放不下时 null
     */
    public static int[] findFreeSpot(List<Hole> holes, int screenW, int wantWidth) {
        if (screenW <= 0 || wantWidth <= 0 || wantWidth > screenW) {
            return null;
        }
        // 收集已占用区间并排序。
        List<int[]> used = new ArrayList<>();
        if (holes != null) {
            for (Hole h : holes) {
                if (h != null && h.valid()) {
                    used.add(new int[]{h.left, h.right});
                }
            }
        }
        used.sort((a, b) -> Integer.compare(a[0], b[0]));

        // 候选起点：屏幕中央，然后左右交替扩散，步长一个洞宽。
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

    /**
     * 从一段横向区间里减掉所有隧道洞，把剩余部分追加到调用方数组。
     *
     * 必须减：隧道里的字被遮住、屏幕上看不见，那段不该再遮挡状态栏内容。
     * 纯函数、帧内零分配：{@code count} 是已有段数，返回值是追加后的段数。
     *
     * @param holes    洞列表（只看隧道洞，虫洞跳过）
     * @param from     区间左端
     * @param to       区间右端
     * @param outLeft  输出：每段左边界
     * @param outRight 输出：每段右边界
     * @param count    已写入的段数（从该下标开始追加）
     * @return 追加后的段数
     */
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
                if (h == null || !h.valid() || h.isWormhole()) {
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
    /**
     * 按「文字真正画出来的位置」生成遮挡分段。
     *
     * 裁边界是状态栏的左右沿，不是文字轨道的两端：分段位置已含虫洞位移，
     * 拿未位移的轨道坐标去比会把「已传送到洞口左侧」的段整段丢掉。
     * 隧道洞内的字被 {@code clipOutRect} 遮住、屏幕上看不见，由 {@link #subtractTunnels} 减掉。
     *
     * 纯函数、帧内零分配：结果写进调用方数组，与 {@link #wormholeSegments} /
     * {@link #subtractTunnels} 同一套约定。{@code trackLeft/trackRight} 是诗句层本地坐标，
     * 输出是屏幕坐标（内部加 {@code originX}），可直接交给 {@code HolePuncher}。
     * 输出数组容量须 ≥ {@code (MAX_HOLES+1)²}：虫洞最多切出 {@code MAX_HOLES+1} 段，
     * 每段再被隧道洞切成 {@code MAX_HOLES+1} 段。
     *
     * @param holes      洞列表（按 left 升序）
     * @param segScreen  绘制分段：每段在状态栏本地坐标系里的起点（见 {@link #wormholeSegments}）
     * @param segWidth   绘制分段：每段宽度
     * @param segCount   绘制分段数；&lt;= 0 表示没有虫洞，按整条轨道处理
     * @param trackLeft  轨道左端（未位移，仅在没有虫洞时使用）
     * @param trackRight 轨道右端（同上）
     * @param originX    状态栏在屏幕上的原点
     * @param barW       状态栏宽度；&lt;= 0 表示未知，此时不裁右边界
     * @param outLeft    输出：每段左边界（屏幕坐标）
     * @param outRight   输出：每段右边界（屏幕坐标）
     * @return 段数
     */
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
                // 段的屏幕位置就是它被画出来的位置，直接使用，不和轨道坐标比较。
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

        // 没有虫洞：整条轨道都是可见的，只需裁到状态栏范围并减掉隧道。
        float l = Math.max(trackLeft + originX, barL);
        float r = Math.min(trackRight + originX, barR);
        if (r <= l) {
            return 0;
        }
        return subtractTunnels(holes, l, r, outLeft, outRight, 0);
    }

    /**
     * 整句在屏幕上最后消失时，轨道左端会走到哪里 —— 「一首诗什么时候结束」的唯一判据。
     *
     * 不能用 {@code left + width < 0}：虫洞把整句往左瞬移一个洞宽，屏幕上最后一个字
     * 早就消失了，轨道却还要再走一个洞宽才走完。虫洞位移是单调不减的已穿过洞宽之和，
     * 所以临界值 = {@code -textWidth + 总虫洞宽}。
     *
     * 纯函数、帧内零分配。
     *
     * @param holes     洞列表
     * @param textWidth 诗句宽度（像素）
     * @return 轨道左端到这个值（或更小）就算整句离屏
     */
    public static float finishLeft(List<Hole> holes, float textWidth) {
        return -textWidth + totalWormholeWidth(holes);
    }

    /** 所有虫洞宽度之和（没有虫洞时为 0）。 */
    public static float totalWormholeWidth(List<Hole> holes) {
        float sum = 0f;
        if (holes != null) {
            for (int i = 0; i < holes.size(); i++) {
                Hole h = holes.get(i);
                if (h != null && h.valid() && h.isWormhole()) {
                    sum += h.width();
                }
            }
        }
        return sum;
    }

    /**
     * 计算虫洞的绘制分段 —— 字到洞口瞬移到另一侧，一个字都不丢。
     *
     * 模型：洞那段屏幕不存在，文字在「抽掉了洞宽的压缩轨道」上匀速前进，
     * 贴到屏幕时轨道坐标 u &lt; 洞左沿 → x = u，u ≥ 洞左沿 → x = u + 洞宽。
     * 于是字跨过洞口的瞬间屏幕位置从洞右沿跳到洞左沿，字没丢，只是那段距离被压缩掉了。
     *
     * 一个洞的触发点不能用它的屏幕右沿：屏幕位置已被右侧的洞左移过，
     * 换算到原始坐标要补上右侧所有洞的宽度，即
     * {@code P_j = holeRight_j + Σ{holeW_k : k 在 j 右边}}。
     *
     * 每个分段宽度在两种坐标下相同（纯平移，不缩放），结果写入调用方数组，帧内零分配。
     *
     * @param holes       洞列表（按 left 升序，可混有 tunnel，本方法只看虫洞）
     * @param textLeft    文字左端（屏幕/轨道一致，因为左端左侧不放洞）
     * @param textRight   文字右端
     * @param originX     状态栏在屏幕上的原点
     * @param segScreen   输出：每段在状态栏本地坐标系里的起点（加 {@code originX} 即屏幕坐标）
     * @param segText     输出：每段对应的轨道(文字)起点
     * @param segWidth    输出：每段宽度
     * @param maxSegments 数组容量
     * @return 段数
     */
    public static int wormholeSegments(List<Hole> holes, float textLeft, float textRight,
                                       int originX, float[] segScreen, float[] segText,
                                       float[] segWidth, int maxSegments) {
        if (segScreen == null || segText == null || segWidth == null
                || maxSegments <= 0 || textRight <= textLeft
                || holes == null || holes.isEmpty()) {
            return 0;
        }
        // 虫洞 = 传送门：每个字自身匀速；不丢字、不重叠；整句在屏幕上比原宽长一个洞宽。
        //
        // 单个字的轨迹：屏幕位置 x 从右往左匀速减小，一旦 x 触到某个洞的右沿，
        // 立刻瞬移到该洞的左沿，然后继续往左。于是「原始位置 p」（不含跳跃、匀速减小）
        // 与屏幕位置 x 的关系是 x = p - Σ{ 洞宽 : 这个字已经穿过该洞 }。
        int n = 0;
        // 1) 收集虫洞（用固定数组，帧内零分配）。
        int count = 0;
        for (int i = 0; i < holes.size() && count < MAX_HOLES; i++) {
            Hole h = holes.get(i);
            if (h == null || !h.valid() || !h.isWormhole()) {
                continue;
            }
            holeLeft[count] = h.left - originX;
            holeRight[count] = h.right - originX;
            holeW[count] = h.width();
            count++;
        }
        // 2) 按左沿升序（洞之间不重叠，所以左沿顺序 = 右沿顺序）。
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
        // 3) 换算每个洞在「原始坐标」里的触发点：
        //    P_j = 右沿_j + 它右侧所有洞的宽度之和。
        float rightSum = 0f;
        for (int i = count - 1; i >= 0; i--) {
            holeTrigger[i] = holeRight[i] + rightSum;
            rightSum += holeW[i];
        }

        // 4) 输出可见段。
        //    起始位移 = 已经穿过（P_j <= textLeft）的洞宽之和。
        float shift = 0f;
        for (int i = 0; i < count; i++) {
            if (holeTrigger[i] > textLeft) {
                shift += holeW[i];
            }
        }
        float cursor = textLeft;
        for (int i = 0; i < count && n < maxSegments; i++) {
            final float bp = holeTrigger[i];     // 传送点（原始坐标）
            if (bp <= textLeft) {
                continue;                        // 起始 shift 已排除
            }
            if (bp >= textRight) {
                break;                           // 文字还没到这道门
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
            shift -= holeW[i];                   // 越过这道门，位移少一个洞宽
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
    /** 分段计算用的复用数组（帧内零分配）。 */
    private static final float[] holeLeft = new float[MAX_HOLES];
    private static final float[] holeRight = new float[MAX_HOLES];
    private static final float[] holeW = new float[MAX_HOLES];
    private static final float[] holeTrigger = new float[MAX_HOLES];

}