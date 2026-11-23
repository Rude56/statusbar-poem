package io.github.rude56.poemstatus;

import android.graphics.Canvas;
import android.os.SystemClock;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

final class HolePuncher {
    /** 挖洞会让整条状态栏重绘，必须节流，否则等于每帧全画一遍。 */
    private static final long INVALIDATE_INTERVAL_MS = 30L;
    private static final int HOLE_PAD_PX = 2;
    static boolean installed;
    private static View root;
    /** 挖洞根的 identityHashCode，供热路径 isRootFast 快速挡掉无关实例。 */
    private static int rootIdentity;
    private static int rootLeft, rootTop, rootRight, rootBottom;
    private static final int MAX_SEGMENTS = (HoleRects.MAX_HOLES + 1) * (HoleRects.MAX_HOLES + 1);
    private static final int[] segLeft = new int[MAX_SEGMENTS];
    private static final int[] segRight = new int[MAX_SEGMENTS];
    private static int segCount;
    private static boolean poemValid;
    private static boolean tallRoot;
    private static boolean inDraw;
    private static final ClipStack clips = new ClipStack();
    private static long lastInvalidateAt;
    private static final List<HoleRects.Hole> holes = new ArrayList<>(8);
    private HolePuncher() {
    }

    static void setHoles(List<HoleRects.Hole> list) {
        holes.clear();
        if (list != null) {
            for (HoleRects.Hole h : list) {

                if (h != null && h.valid() && h.enabled
                        && holes.size() < HoleRects.MAX_HOLES) {
                    holes.add(h);
                }
            }
        }

        if (root != null && poemValid) {
            root.invalidate();
        }
    }
    static int holeCount() {
        return holes.size();
    }
    static String rootName() {
        View v = root;
        return v == null ? "null" : v.getClass().getSimpleName();
    }

    static void bindRoot(View v) {
        root = v;
        rootIdentity = v == null ? 0 : System.identityHashCode(v);
        refreshBounds();
        // 「整屏根」= 比状态栏本身高得多。用 root 自己的状态栏高度做基准，
        // 而不是写死 400px（那个阈值只对高分辨率机型成立）。
        final int h = rootBottom - rootTop;
        int barH = v == null ? 0 : ViewTree.statusBarHeightPx(v);
        if (barH <= 0) {
            barH = 24 * 3;
        }
        tallRoot = v != null && h > HOLE_PAD_PX * 8 && h > barH * 3;
        RootHook hk = rootHook;
        if (hk != null && v != null) {
            try {
                hk.onRoot(v);
            } catch (Throwable ignored) {

            }
        }
    }

    interface RootHook {
        void onRoot(View v);
    }

    private static volatile RootHook rootHook;

    static void setRootHook(RootHook h) {
        rootHook = h;
    }

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

    static void updatePoemSegments(float[] lefts, float[] rights, int count,
                                   float top, float bottom) {

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
        bandTop = top;
        bandBottom = bottom;
        long now = SystemClock.uptimeMillis();
        if (now - lastInvalidateAt < INVALIDATE_INTERVAL_MS) {
            return;
        }
        lastInvalidateAt = now;

        int l = hadPrev ? Math.min(minL, prevL) : minL;
        int r = hadPrev ? Math.max(maxR, prevR) : maxR;
        invalidateBox(l, r);
    }

    private static int dirtyL;
    private static int dirtyR;
    private static float bandTop;
    private static float bandBottom;
    private static void erasePrevious(boolean hadPrev, int l, int r) {
        if (root == null || !hadPrev) {
            return;
        }
        invalidateBox(l, r);
        lastInvalidateAt = SystemClock.uptimeMillis();
    }
    private static void invalidateBox(int screenL, int screenR) {
        View v = root;
        if (v == null) {
            return;
        }
        int l = Math.max(0, screenL - rootLeft - HOLE_PAD_PX);
        int r = Math.min(rootRight - rootLeft, screenR - rootLeft + HOLE_PAD_PX);
        int t;
        int b;
        if (tallRoot) {
            t = Math.max(0, (int) Math.floor(bandTop - rootTop) - HOLE_PAD_PX);
            b = Math.min(rootBottom - rootTop, (int) Math.ceil(bandBottom - rootTop) + HOLE_PAD_PX);
        } else {
            t = 0;
            b = rootBottom - rootTop;
        }
        if (r > l && b > t) {
            v.invalidate(l, t, r, b);
        }
    }

    static void clear() {

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

    static boolean isRoot(View v) {
        return installed && v != null && v == root;
    }

    /**
     * 极热的快速判定。挖洞根若落到框架基类（如 ViewGroup.dispatchDraw），
     * 这个判断会被全进程每个同类 View 的每次绘制调用一次，所以必须便宜。
     *
     * <p>用 {@code installed} 布尔短路 + identityHashCode 整数比较，
     * 绝大多数实例在第一次整数比较就被挡掉。实测该机型就是 ViewGroup 基类，
     * 这条路径每帧要跑几千次，所以单独抽出来。
     */
    static boolean isRootFast(View v) {
        if (!installed || v == null) {
            return false;
        }
        if (System.identityHashCode(v) != rootIdentity) {
            return false;
        }
        return v == root;
    }

    static boolean beginClip(Canvas canvas, View view) {
        if (canvas == null || view == null || view != root) {
            return false;
        }
        if (inDraw) {
            return false;
        }
        if (!poemValid || segCount <= 0) {
            return false;
        }

        if (segCount > 1 && DebugFrames.pending()) {
            DebugFrames.consume();
            Log.diag("POEM clip segs=" + segCount
                    + " root=" + (root == null ? "null" : root.getClass().getSimpleName())
                    + " tall=" + tallRoot
                    + " band=" + (int) bandTop + "-" + (int) bandBottom
                    + " rootBox=" + rootLeft + "," + rootTop + "-" + rootRight + "," + rootBottom);
        }
        if (!clips.push(canvas.save())) {
            return false;
        }
        final float top;
        final float bottom;
        if (tallRoot) {
            top = Math.max(0f, bandTop - rootTop - HOLE_PAD_PX);
            bottom = Math.min(rootBottom - rootTop, bandBottom - rootTop + HOLE_PAD_PX);
            if (bottom <= top) {
                clips.pop();
                return false;
            }
        } else {
            top = 0f;
            bottom = rootBottom - rootTop;
        }
        for (int i = 0; i < segCount; i++) {

            float l = segLeft[i] - rootLeft - HOLE_PAD_PX;
            float r = segRight[i] - rootLeft + HOLE_PAD_PX;
            canvas.clipOutRect(l, top, r, bottom);
        }
        inDraw = true;
        return true;
    }

    static void endClip(Canvas canvas) {
        inDraw = false;
        int saveCount = clips.pop();
        if (saveCount < 0 || canvas == null) {
            return;
        }
        canvas.restoreToCount(saveCount);
    }

    /**
     * 挖洞根绝不能是诗句层的祖先。
     *
     * <p>否则 clipOutRect 会把诗句一起裁掉，只能靠「抑制自己 + 根上补画」打补丁，
     * 那会让同一帧画两遍诗句：残影、卡顿、解锁发虚。宁可放弃挖洞也不能这样。
     */
    static boolean isAncestorOfOverlay(View candidate, View overlay) {
        if (candidate == null || overlay == null) {
            return false;
        }
        View p = overlay;
        for (int i = 0; i < 32 && p != null; i++) {
            if (p == candidate) {
                return true;
            }
            p = p.getParent() instanceof View ? (View) p.getParent() : null;
        }
        return false;
    }

    /** 一行诊断：根、尺寸、是否整屏、当前洞。 */
    static String describe() {
        View v = root;
        if (v == null) {
            return "root=null";
        }
        return "root=" + v.getClass().getSimpleName()
                + " box=" + (rootRight - rootLeft) + "x" + (rootBottom - rootTop)
                + " at=" + rootLeft + "," + rootTop
                + " tall=" + tallRoot
                + " segs=" + segCount
                + " valid=" + poemValid;
    }
}
