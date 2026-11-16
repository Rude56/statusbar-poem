package io.github.rude56.poemstatus;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 自定义「洞」：可以加多个矩形，高度固定为状态栏高度。
 *
 * <p>为什么高度固定：洞只在状态栏这条带子里挖，纵向永远是满高，
 * 让用户填高度只会引入「洞比字高/比字矮」的对不齐问题。
 * 所以一个洞只需要「左边界 + 右边界」，上下直接取状态栏上下沿。
 *
 * <p>两种模式：
 * <ul>
 *   <li>{@link Mode#WORMHOLE}（虫洞）：洞内的字<b>不参与排版</b>，
 *       诗句会「跳过」这段宽度，看起来像被吞进去又从另一头吐出来；</li>
 *   <li>{@link Mode#TUNNEL}（隧道）：诗句照常连续滚动，
 *       只是落在洞里的那部分像素被遮住，像从隧道后面穿过。</li>
 * </ul>
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
         * 颜色槽位。
         *
         * <p>为什么需要它：颜色如果按下标取，删掉第一个洞会让第二个洞换色，
         * 改左右边界导致重新排序也会换色 —— 用户会觉得「我什么都没动，颜色却变了」。
         * 槽位跟着洞走，增删其它洞都不影响它。
         *
         * <p>-1 表示「未指定」，解析时按排序下标补上。
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
     * <p>mode 省略时默认虫洞（向后兼容旧配置）。
     * slot 省略时按排序下标补上（旧配置没有槽位信息）。
     * 非法段直接丢弃，不让一条脏数据把整份配置废掉。
     *
     * <p>返回的列表保证<b>两两不重合</b>（见 {@link #sanitize}）。
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
     * <p>规则：已有合法槽位保持不动；缺槽位的洞取「当前已用的最小空槽」。
     * 这样旧配置升级后颜色稳定，新加的洞也不会抢走别人的颜色。
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
     * 保证洞两两<b>不重合</b>。
     *
     * <p>为什么必须禁止重合：两个洞叠在一起时，「这一段到底该按虫洞还是隧道处理」
     * 没有答案 —— 绘制层会同时 clipOut 又 clipRect，最终结果取决于遍历顺序，
     * 表现为「同一个位置有时看得见、有时看不见」。而且遮挡分段是按洞逐个切成
     * 多段的，重叠会让段边界互相抵消，出现「诗过去了图标还缺一块」。
     *
     * <p>处理方式：按左边界排序后顺序扫描，只保留与已接受区间不相交的洞；
     * 相交的那个<b>整条丢弃</b>（而不是裁剪到不重合）—— 裁剪会悄悄改掉用户
     * 亲手量出来的边界，比丢弃更难理解。调用方（编辑页）会在保存前拦住这种情况
     * 并给出提示，所以这里只是「解析脏数据」的兜底。
     *
     * <p>返回新列表，不改动入参。
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
     * 为「新增洞」找一个不与已有洞重叠的位置。
     *
     * <p>从屏幕中央开始向左右交替搜索，找到第一段长度 ≥ {@code wantWidth}
     * 且与所有已有洞都不相交的空隙；都放不下时返回 {@code null}。
     *
     * <p>这样连续点「添加」不会把洞叠在一起（叠在一起的洞既看不出来，
     * 也让模式判定变得没有意义）。
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
     * 从一段横向区间里<b>减掉所有隧道洞</b>，把剩余部分追加到调用方数组。
     *
     * <p>为什么必须减：隧道里的字被遮住、屏幕上看不见，那段自然不该再遮挡
     * 状态栏内容。否则会出现「诗进了隧道看不见，图标却还被挖掉一块」。
     *
     * <p>纯函数、帧内零分配：结果写进调用方提供的数组（{@code count} 是已有段数，
     * 返回值是追加后的段数），因此可以直接被
     * {@code PoemRuntime.updateOcclusion} 复用，不需要在帧里 new 任何东西。
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
     * 按「文字真正画出来的位置」生成<b>遮挡</b>分段。
     *
     * <p>这是虫洞遮挡的正确性核心。<b>文字层画在哪，遮挡就必须落在哪。</b>
     *
     * <p>用户报的现象：「虫洞能正确传送文字，但不能正确传送遮挡，文字到了，不会遮挡」。
     * 根因就是这里曾经拿错坐标做可见性判断：
     * <pre>
     *   segScreen[i]  = 已经减掉跳跃位移的屏幕位置
     *   trackLeft     = 还没减位移的轨道左端
     *   旧判据：segScreen[i] + segWidth[i] &lt;= trackLeft  → 整段丢弃
     * </pre>
     * 于是「已经传送到洞口左侧」的那一段，因为屏幕坐标本来就比轨道坐标小一个洞宽，
     * 被误判成「还没进入状态栏」而<b>整段扔掉</b>：字明明画在洞左边，遮挡却没跟过去。
     * 偏差从 0 随文字左移一路增大到接近一整个洞宽。
     *
     * <p>正确的裁边界是<b>状态栏的左右沿</b>，不是文字轨道的两端。
     *
     * <p>另一件事：隧道洞里的字被 {@code clipOutRect} 遮住、屏幕上根本看不见，
     * 所以那一段也不该遮挡状态栏（否则「诗在隧道里，图标却还被挖掉一块」）。
     * 由 {@link #subtractTunnels} 减掉。
     *
     * <p>纯函数、帧内零分配：结果写进调用方数组，与
     * {@link #wormholeSegments} / {@link #subtractTunnels} 同一套约定。
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
                // 关键：段的屏幕位置就是它被画出来的位置，直接用，不再和轨道坐标比较。
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
     * 整句在屏幕上<b>最后消失</b>时，轨道左端会走到哪里。
     *
     * <p>这是「一首诗什么时候结束」的唯一正确判据。
     *
     * <p><b>为什么不能用 {@code left + width < 0}</b>：那个判据假设「轨道坐标 = 屏幕坐标」，
     * 也就是洞不存在的情形。但虫洞会把整句往<b>左</b>瞬移一个洞宽 ——
     * 屏幕上最后一个字早在一个洞宽之前就消失了，轨道却还要再走一个洞宽才「走完」。
     *
     * <p>用户报的现象正是这个：「诗穿过一个比较长的虫洞后要等好久才来下一首」。
     * 当前配置（洞宽 610px、172px/s）下每次换首前空等 <b>3.5 秒</b>；
     * 洞宽 1000px 时空等 5.8 秒 —— 而且这段时间里屏幕上一个字都没有。
     *
     * <p>算法：虫洞的位移是「已穿过的洞宽之和」，它是单调不减的，
     * 所以最后一个字消失的那一刻，位移就是<b>全部虫洞宽度之和</b>。
     * 于是轨道左端的临界值 = {@code -width + 总洞宽}。
     *
     * <p>纯函数、帧内零分配。
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
     * 计算「虫洞」的绘制分段 —— 字到洞口<b>瞬移</b>到另一侧，一个字都不丢。
     *
     * <p>模型：洞那段屏幕不存在。文字在「抽掉了洞宽的压缩轨道」上匀速前进，
     * 贴到屏幕上时：
     * <pre>
     *   轨道坐标 u &lt; 洞左沿   → 屏幕 x = u
     *   轨道坐标 u ≥ 洞左沿   → 屏幕 x = u + 洞宽
     * </pre>
     * 于是字跨过洞口的瞬间，屏幕位置从 <b>洞右沿</b> 直接跳到 <b>洞左沿</b> ——
     * 这正是「传送门」的观感；字没有丢，只是那段距离被压缩掉了。
     *
     * <p>对比 {@link Mode#TUNNEL}：那种情况下文字位置不动、洞内不画，
     * 落在洞里的字会被「火车隧道」遮掉、看不见。
     *
     * <p>每个分段的宽度在两种坐标下相同（纯平移，不缩放），
     * 结果写入调用方数组，帧内零分配。
     *
     * @param holes       洞列表（按 left 升序，可混有 tunnel，本方法只看虫洞）
     * @param textLeft    文字左端（屏幕/轨道一致，因为左端左侧不放洞）
     * @param textRight   文字右端
     * @param originX     状态栏在屏幕上的原点
     * @param segScreen   输出：每段在屏幕上的起点
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
        // ---------------- 虫洞 = 传送门 ----------------
        //
        // 判据来自用户的三句话：
        //   ①「诗的速度不变」        → 每个字自身匀速；
        //   ②「虫洞把诗砍成两半，拼起来不会变短」→ 不丢字、不重叠；
        //   ③「整句在屏幕上不应该变长一个洞宽吗」→ 诗中间夹了个洞，
        //      两侧被撑开，整句在屏幕上比原宽<b>长</b>一个洞宽。
        //
        // 单个字的轨迹：屏幕位置 x 从右往左匀速减小；一旦 x 触到某个洞的
        // <b>右沿</b>，立刻瞬移到该洞的<b>左沿</b>，然后继续往左。
        //
        // 于是「原始位置 p」（不含跳跃、匀速减小）与屏幕位置 x 的关系是：
        //   x = p - Σ{ 洞宽 : 这个字已经穿过该洞 }
        //
        // 关键：一个洞的触发点不能直接用它的屏幕右沿，因为屏幕位置本身
        // 已经被右侧的洞左移过了。换算到原始坐标要补上右侧所有洞的宽度：
        //   P_j = 洞右沿_j + Σ{ 洞宽_k : k 在 j 的右边 }
        //
        // 之前的版本漏掉了这个补偿，直接拿屏幕右沿当断点 ——
        // 结果第二个虫洞的触发点被第一个洞的宽度带偏，
        // 表现为「两个虫洞时第二个洞被第一个偏移」。
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