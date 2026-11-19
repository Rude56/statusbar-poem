package io.github.rude56.poemstatus;

import android.graphics.Canvas;
import android.os.SystemClock;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

final class HolePuncher {
    private static final long INVALIDATE_INTERVAL_MS = 30L;
    private static final int HOLE_PAD_PX = 2;
    static boolean installed;
    private static View root;
    private static int rootLeft, rootTop, rootRight, rootBottom;
    private static final int MAX_SEGMENTS = (HoleRects.MAX_HOLES + 1) * (HoleRects.MAX_HOLES + 1);
    private static final int[] segLeft = new int[MAX_SEGMENTS];
    private static final int[] segRight = new int[MAX_SEGMENTS];
    private static int segCount;
    private static boolean poemValid;
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
        refreshBounds();
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
        if (r > l) {
            v.invalidate(l, 0, r, rootBottom - rootTop);
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

    static boolean beginClip(Canvas canvas, View view) {
        if (canvas == null || view == null || view != root) {
            return false;
        }
        if (!poemValid || segCount <= 0) {
            return false;
        }

        if (segCount > 1 && DebugFrames.pending()) {
            DebugFrames.consume();
            Log.i("POEM clip segs=" + segCount
                    + " first=" + segLeft[0] + "-" + segRight[0]
                    + " last=" + segLeft[segCount - 1] + "-" + segRight[segCount - 1]
                    + " rootBox=" + rootLeft + "," + rootTop + "-" + rootRight + "," + rootBottom);
        }
        if (!clips.push(canvas.save())) {
            return false;
        }
        for (int i = 0; i < segCount; i++) {

            float l = segLeft[i] - rootLeft - HOLE_PAD_PX;
            float r = segRight[i] - rootLeft + HOLE_PAD_PX;
            canvas.clipOutRect(l, -HOLE_PAD_PX, r, (rootBottom - rootTop) + HOLE_PAD_PX);
        }
        return true;
    }

    static void endClip(Canvas canvas) {
        int saveCount = clips.pop();
        if (saveCount < 0 || canvas == null) {
            return;
        }
        canvas.restoreToCount(saveCount);
    }
}
