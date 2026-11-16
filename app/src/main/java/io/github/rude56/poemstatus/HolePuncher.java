package io.github.rude56.poemstatus;

import android.graphics.Canvas;
import android.os.SystemClock;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * 统一遮挡：在状态栏内容根的画布上挖洞，让诗句「穿」过去。
 *
 * Hook {@code View.draw(Canvas)}，目标对象是内容根时在它绘制前用 {@link Canvas#clipOutRect}
 * 挖洞。clip 属于画布状态，会随 {@code save/restore} 传给所有子孙，所以时钟、图标无论原生还是
 * Compose 画的，一次裁剪全部穿透；逐个 View 改 alpha 做不到（Compose 把它们画在同一块画布上）。
 *
 * 省电：一次 draw 只多 N 对 save/clipOut/restore（N = 可见分段数，通常 1~2），无对象分配；
 * 没有诗句在显示时直接返回 false，零开销；只在诗句确实覆盖内容根时节流重绘根视图。
 */
final class HolePuncher {

    /**
     * 重绘节流。
     *
     * 洞的位置必须跟着诗句走，否则会「诗句过去了、图标还缺一块」。30ms 约等于一个帧间隔，
     * 120px/s 下位移约 3.6px，远小于字形宽度，肉眼看不出台阶，同时把根视图重绘压到最多 33 次/秒。
     */
    private static final long INVALIDATE_INTERVAL_MS = 30L;

    /** 挖洞矩形比诗句本身稍微外扩，避免抗锯齿边缘露出 1px 图标。 */
    private static final int HOLE_PAD_PX = 2;

    /** Hook 是否装上；没装上时遮挡直接不生效（诗句照常滚动，只是不遮原生内容）。 */
    static boolean installed;

    private static View root;
    private static int rootLeft, rootTop, rootRight, rootBottom;

    /**
     * 本帧要挖掉的若干矩形（屏幕坐标）。隧道洞内的段不在这里面 —— 那段不遮。
     *
     * 容量必须够 {@link HoleRects#visibleSegments} 输出的最大段数：虫洞最多切出
     * {@code MAX_HOLES + 1} 段，每段再被隧道洞切开最多 {@code MAX_HOLES + 1} 段，
     * 上界 {@code (MAX_HOLES + 1)²}。取满以保证不丢段 —— 丢段等于「字画出来了却没有遮挡」。
     */
    private static final int MAX_SEGMENTS = (HoleRects.MAX_HOLES + 1) * (HoleRects.MAX_HOLES + 1);
    private static final int[] segLeft = new int[MAX_SEGMENTS];
    private static final int[] segRight = new int[MAX_SEGMENTS];
    private static int segCount;
    private static boolean poemValid;

    /** 画布裁剪的 save 栈：{@code View.draw} 可能重入（硬件层子树、ROM 预绘制），单一计数会被内层抹掉。 */
    private static final ClipStack clips = new ClipStack();
    private static long lastInvalidateAt;

    /** 当前生效的洞（屏幕坐标，纵向恒为整条状态栏）。 */
    private static final List<HoleRects.Hole> holes = new ArrayList<>(8);

    private HolePuncher() {
    }

    /**
     * 设置洞列表。配置变化时调用，不进帧循环。
     *
     * @param list 洞列表（屏幕坐标左/右边界）
     */
    static void setHoles(List<HoleRects.Hole> list) {
        holes.clear();
        if (list != null) {
            for (HoleRects.Hole h : list) {
                if (h != null && h.valid() && holes.size() < HoleRects.MAX_HOLES) {
                    holes.add(h);
                }
            }
        }
        // 洞列表只在确实正在挖洞时才重绘：没在播放就没什么可恢复的。
        if (root != null && poemValid) {
            root.invalidate();
        }
    }

    static int holeCount() {
        return holes.size();
    }

    /** 诊断用：当前绑定的内容根类名。 */
    static String rootName() {
        View v = root;
        return v == null ? "null" : v.getClass().getSimpleName();
    }

    /** 每首诗开始时绑定「内容根」：它必须包含所有要穿透的内容，且不含诗句层。 */
    static void bindRoot(View v) {
        root = v;
        refreshBounds();
    }

    /**
     * 布局变化（旋转 / 状态栏尺寸改变）后重算根的屏幕矩形。
     *
     * 必须在 {@code bindRoot} 之外单独暴露：{@code SystemUiController.updateOrientation}
     * 检测到方向变化时调它，否则旋转后的遮挡会整体用旧原点。
     */
    static void refreshBounds() {
        View v = root;
        if (v == null) {
            rootLeft = rootTop = rootRight = rootBottom = 0;
            return;
        }
        int[] loc = new int[2];
        v.getLocationOnScreen(loc);
        rootLeft = loc[0];
        rootTop = loc[1];
        rootRight = rootLeft + v.getWidth();
        rootBottom = rootTop + v.getHeight();
    }

    /**
     * 设置这一帧要挖掉的若干矩形（屏幕坐标）。隧道洞内的段不在这里面 —— 那段不遮。
     *
     * 必须分段挖：隧道里的字看不见，它所在的那段屏幕也不该再遮挡状态栏内容，
     * 否则会出现「诗进了隧道看不见，图标却还被挖掉一块」。
     *
     * @param lefts  每段左边界
     * @param rights 每段右边界
     * @param count  段数
     * @param top    纵向范围（整条状态栏）
     * @param bottom 纵向范围
     */
    static void updatePoemSegments(float[] lefts, float[] rights, int count,
                                   float top, float bottom) {
        // 上一帧挖过的范围还在屏上，稍后要连它一起重绘。
        final int prevL = dirtyL;
        final int prevR = dirtyR;
        final boolean hadPrev = segCount > 0;

        segCount = 0;
        poemValid = count > 0;
        if (count <= 0 || root == null || bottom < rootTop || top > rootBottom) {
            erasePrevious(hadPrev, prevL, prevR);
            return;
        }
        int limit = Math.min(count, segLeft.length);
        int minL = Integer.MAX_VALUE;
        int maxR = Integer.MIN_VALUE;
        for (int i = 0; i < limit; i++) {
            segLeft[i] = (int) lefts[i];
            segRight[i] = (int) rights[i];
            if (segLeft[i] < minL) {
                minL = segLeft[i];
            }
            if (segRight[i] > maxR) {
                maxR = segRight[i];
            }
        }
        segCount = limit;
        dirtyL = minL;
        dirtyR = maxR;

        long now = SystemClock.uptimeMillis();
        if (now - lastInvalidateAt < INVALIDATE_INTERVAL_MS) {
            return;
        }
        lastInvalidateAt = now;
        // 重绘「新旧范围的并集」：脏区只取并集，一次 invalidate 与每段一次结果相同，
        // 但分多次调用每次都要走一遍 ViewRootImpl 的脏区登记。诗句在往左走，
        // 旧范围的右边界一定比新范围大 —— 漏掉它，那条带会一直保留上一帧「挖掉」的样子。
        int l = hadPrev ? Math.min(minL, prevL) : minL;
        int r = hadPrev ? Math.max(maxR, prevR) : maxR;
        invalidateBox(l, r);
    }

    /** 上一帧脏区的左右边界（屏幕坐标）。 */
    private static int dirtyL;
    private static int dirtyR;

    /**
     * 擦掉上一帧挖的洞。
     *
     * 洞是「画布裁剪」，只在重绘时生效：若某一帧变成没有可见文字
     * （字整段落进隧道、或整句移出屏幕）而只把 {@code segCount} 清零不重绘，
     * 上一帧的裁剪不会被撤销，屏幕上会留下永久缺口。
     */
    private static void erasePrevious(boolean hadPrev, int l, int r) {
        if (root == null || !hadPrev) {
            return;
        }
        invalidateBox(l, r);
        lastInvalidateAt = SystemClock.uptimeMillis();
    }

    /** 把屏幕坐标的一段范围换算成内容根本地坐标并重绘（只脏这一条）。 */
    private static void invalidateBox(int screenL, int screenR) {
        View v = root;
        if (v == null) {
            return;
        }
        int l = Math.max(0, screenL - rootLeft - HOLE_PAD_PX);
        int r = Math.min(rootRight - rootLeft, screenR - rootLeft + HOLE_PAD_PX);
        if (r > l) {
            v.invalidate(l, 0, r, rootBottom - rootTop);
        }
    }

    /** 停止播放：清掉洞并重绘一次，让原生内容完整回来。 */
    static void clear() {
        // 没有洞要清时不必重绘：restoreModules() 在暂停路径上会被反复调用，
        // 而 root.invalidate() 是整条状态栏全量重绘，本来就没挖过洞时纯属浪费。
        boolean hadClip = poemValid || segCount > 0;
        poemValid = false;
        segCount = 0;
        dirtyL = 0;
        dirtyR = 0;
        clips.clear();
        if (hadClip && root != null) {
            root.invalidate();
        }
    }

    /** 这个 View 是不是我们绑定的内容根（只有它需要挖洞）。 */
    static boolean isRoot(View v) {
        return installed && v != null && v == root;
    }

    /**
     * 在内容根 {@code draw(Canvas)} 前调用：按「本帧有文字的段」逐段挖洞。
     *
     * @return true 表示已挖洞，调用方必须在绘制后 {@link #endClip} 还原
     */
    static boolean beginClip(Canvas canvas, View view) {
        if (canvas == null || view == null || view != root) {
            return false;
        }
        // 只有诗句自己会遮挡状态栏内容。用户定义的「洞」绝不在这里挖 ——
        // 洞只影响诗句自己的绘制（隧道遮字、虫洞传送）。
        if (!poemValid || segCount <= 0) {
            return false;
        }
        // 诊断：只抓「虫洞正在传送」的那一帧（多段 = 有字被传送到洞口另一侧）；
        // segCount == 1 是诗句刚进屏幕、还没有字穿过虫洞的帧，遮挡与整条矩形一致。
        if (segCount > 1 && DebugFrames.pending()) {
            DebugFrames.consume();
            Log.i("POEM clip segs=" + segCount
                    + " first=" + segLeft[0] + "-" + segRight[0]
                    + " last=" + segLeft[segCount - 1] + "-" + segRight[segCount - 1]
                    + " rootBox=" + rootLeft + "," + rootTop + "-" + rootRight + "," + rootBottom);
        }
        // 入栈而不是覆盖计数：draw 可能被重入（见 ClipStack 的注释）。
        // 深度超限时放弃这一层裁剪 —— 宁可少遮一层，也不能让画布状态错乱。
        if (!clips.push(canvas.save())) {
            return false;
        }
        for (int i = 0; i < segCount; i++) {
            // 段内文字可见 → 遮住这段下方的原生内容。
            float l = segLeft[i] - rootLeft - HOLE_PAD_PX;
            float r = segRight[i] - rootLeft + HOLE_PAD_PX;
            canvas.clipOutRect(l, -HOLE_PAD_PX, r, (rootBottom - rootTop) + HOLE_PAD_PX);
        }
        return true;
    }

    /** 绘制结束后还原画布（必须与 {@link #beginClip} 成对出现）。 */
    static void endClip(Canvas canvas) {
        int saveCount = clips.pop();
        if (saveCount < 0 || canvas == null) {
            return;
        }
        canvas.restoreToCount(saveCount);
    }
}
