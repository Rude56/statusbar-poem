package io.github.rude56.poemstatus;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.Choreographer;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class PoemRuntime {
    interface Listener {
        void onFrame(float left, float top, float right, float bottom);
        void onFinished();
        void onOverlayAttached();
        void onOverlayDetached();
    }

    private static final int MAX_MEASURE_CACHE = 64;

    static final float BOB_AMPLITUDE_PX = 1.5f;

    static final float MIN_DRAW_DELTA_PX = FramePacer.MIN_DRAW_DELTA_PX;

    static final float FORCE_ELEVATION_PX = 50f;

    private final java.util.Random bobRandom = new java.util.Random();

    private final Map<String, Float> measureCache =
            new LinkedHashMap<String, Float>(MAX_MEASURE_CACHE, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Float> eldest) {
                    return size() > MAX_MEASURE_CACHE;
                }
            };

    private View statusContainer;
    private View occludeHost;
    private boolean forceWindowMode;
    private OverlayView overlay;

    View overlayView() {
        return overlay;
    }
    private WindowManager windowManager;

    private List<HoleRects.Hole> configHoles = new ArrayList<>(2);

    private final float[] visSegLeft =
            new float[(HoleRects.MAX_HOLES + 1) * (HoleRects.MAX_HOLES + 1)];
    private final float[] visSegRight =
            new float[(HoleRects.MAX_HOLES + 1) * (HoleRects.MAX_HOLES + 1)];

    private Listener listener;
    private int textColor = Color.WHITE;

    private long frameCount;
    private long skippedFrames;
    private long totalFrameCostNs;
    void setOccludeHost(View v) {
        occludeHost = v;
    }

    /**
     * 记录「锁屏需要窗口模式」这件事，但<b>不</b>拆挂载。
     *
     * <p>挂载方式只由 attachOverlayView 决定（优先 addView 进容器，失败才退窗口）。
     * 这里若 removeOverlay，解锁动画期间 keyguard 状态会来回跳几次，
     * 每跳一次就摘掉再加回诗句层 —— 那正是「解锁一闪一闪」。
     *
     * @return 永远 false：调用方不需要为此暂停状态机。
     */
    boolean setForceWindowMode(boolean force) {
        forceWindowMode = force;
        return false;
    }

    /**
     * 切换诗句层的挂载容器。
     *
     * <p>只有「旧容器还有诗句层挂着，且新容器不同」时才拆。锁屏↔解锁会换宿主，
     * 但若诗句层已经不在旧容器里（例如还在窗口里），就不要再摘一次。
     */
    boolean setStatusContainer(View v) {
        boolean replaced = false;
        if (overlay != null && overlay.attached && statusContainer != null
                && statusContainer != v && overlay.getParent() == statusContainer) {
            removeOverlay();
            replaced = true;
        }
        statusContainer = v;
        return replaced;
    }

    long frameCount() {
        return frameCount;
    }
    long skippedFrames() {
        return skippedFrames;
    }

    long avgFrameCostUs() {
        return frameCount == 0 ? 0 : (totalFrameCostNs / frameCount) / 1000L;
    }
    void resetStats() {
        frameCount = 0;
        skippedFrames = 0;
        totalFrameCostNs = 0;
    }
    void setHoleConfig(List<HoleRects.Hole> holes) {
        configHoles.clear();
        if (holes != null) {
            for (HoleRects.Hole h : holes) {

                if (h != null && h.valid() && h.enabled
                        && configHoles.size() < HoleRects.MAX_HOLES) {
                    configHoles.add(h);
                }
            }
        }
        HolePuncher.setHoles(configHoles);
        pushHolesToOverlay();
    }

    private void pushHolesToOverlay() {
        OverlayView o = overlay;
        if (o != null) {
            o.setHoles(configHoles);
        }
    }

    void setTextColor(int color) {
        textColor = color;
        if (overlay != null) {
            overlay.applyTextColor(color);
        }
    }
    boolean isRunning() {
        return overlay != null && overlay.running;
    }

    void onLayoutChanged() {
        HolePuncher.refreshBounds();
        OverlayView o = overlay;
        if (o != null) {
            o.viewW = statusWidth();
            o.viewH = statusHeight();
            o.refreshScreenOrigin();
            if (o.attached) {
                o.invalidate();
            }
        }
    }

    private int statusWidth() {
        View v = statusContainer;
        if (v == null) {
            return 0;
        }
        return v.getWidth() > 0 ? v.getWidth()
                : v.getResources().getDisplayMetrics().widthPixels;
    }
    private int statusHeight() {
        View v = statusContainer;
        if (v == null) {
            return lastGoodBarHeight > 0 ? lastGoodBarHeight : 0;
        }
        int h = v.getHeight();
        int sb = statusBarHeight(v);
        int result;
        if (sb > 0 && (h <= 0 || h > sb)) {
            result = sb;
        } else if (h > 0) {
            result = h;
        } else {
            result = 0;
        }
        if (result > 0) {
            lastGoodBarHeight = result;
            return result;
        }
        // 取不到时按 AOSP 默认 24dp 估算，随密度自适应，不写死像素值。
        return lastGoodBarHeight > 0
                ? lastGoodBarHeight
                : Math.round(24 * v.getResources().getDisplayMetrics().density);
    }

    /** 最近一次量到的合理状态栏高度，避免容器重建瞬间量到 0 导致文字跳变。 */
    private int lastGoodBarHeight;

    private static int statusBarHeight(View v) {
        try {
            int id = v.getResources().getIdentifier("status_bar_height", "dimen", "android");
            if (id != 0) {
                int px = v.getResources().getDimensionPixelSize(id);
                if (px > 0) {
                    return px;
                }
            }
        } catch (Throwable ignored) {

        }
        return 0;
    }

    boolean start(String text, int speedPx, boolean bob, Listener l) {
        if (statusContainer == null || text == null || text.isEmpty()) {
            return false;
        }
        listener = l;
        if (!ensureOverlayAttached()) {
            return false;
        }
        overlay.begin(text, speedPx, bob, statusWidth(), statusHeight(), textColor, configHoles);

        DebugFrames.reset();

        View src = occludeHost;
        if (src == null) {
            HolePuncher.bindRoot(null);
            overlay.postFrame();
            return true;
        }
        // src 本身已经是整条状态栏、而且不是诗句层的祖先时，就直接用 src。
        // 锁屏的 KeyguardStatusBarView 属于这种：它整条 1440x112，
        // 但里面那个 status_bar_container 只包右侧图标区，往里拆会漏掉运营商文字。
        //
        // 解锁的 StatusBarWindowView 虽然也是 1440x112，但它是诗句层的父级，
        // 必须继续走 findOccludeRoot 拆到 status_bar_container。
        View occludeRoot;
        if (ViewTree.isFullBar(src) && !HolePuncher.isAncestorOfOverlay(src, overlay)) {
            occludeRoot = src;
        } else {
            occludeRoot = ViewTree.findOccludeRoot(src);
            if (occludeRoot == null) {
                occludeRoot = src;
            }
        }
        // 挖洞根一旦是诗句层的祖先，clipOutRect 就会把诗句一起裁掉，
        // 只能靠补画打补丁，等于每帧画两遍。宁可放弃挖洞也不能这样。
        if (HolePuncher.isAncestorOfOverlay(occludeRoot, overlay)) {
            Log.w("POEM occlude root rejected (诗句层祖先)="
                    + occludeRoot.getClass().getSimpleName());
            HolePuncher.bindRoot(null);
            overlay.postFrame();
            return true;
        }
        HolePuncher.bindRoot(occludeRoot);
        dumpViewChain(occludeRoot);
        dumpMountTree();
        Log.diag("POEM occlude " + HolePuncher.describe());
        HolePuncher.setHoles(configHoles);
        overlay.postFrame();
        return true;
    }
    void updateOcclusion(float l, float t, float r, float b) {
        OverlayView o = overlay;
        if (o != null && !o.windowMode && o.getParent() instanceof ViewGroup) {
            keepOnTop((ViewGroup) o.getParent());
        }
        final int originX = o == null ? 0 : o.screenOriginX;
        final int barW = o == null ? 0 : (o.getWidth() > 0 ? o.getWidth() : o.viewW);
        final int visible = HoleRects.visibleSegments(configHoles,
                o == null ? null : o.segScreen, o == null ? null : o.segWidth,
                o == null ? 0 : o.segCount,
                l, r, originX, barW,
                visSegLeft, visSegRight);

        HolePuncher.updatePoemSegments(visSegLeft, visSegRight, visible, t, b);
    }

    void stop() {
        if (overlay != null) {
            overlay.stopFrame();
        }

        HolePuncher.clear();

        if (overlay != null) {
            overlay.running = false;
            overlay.clearText();
        }
    }

    void restoreModules() {
        HolePuncher.clear();
    }

    private void raiseAboveSiblings() {
        OverlayView o = overlay;
        if (o == null || !o.attached || o.windowMode) {
            return;
        }
        ViewGroup g = o.getParent() instanceof ViewGroup ? (ViewGroup) o.getParent() : null;
        if (g == null) {
            return;
        }
        if (g.getChildCount() > 0 && g.getChildAt(g.getChildCount() - 1) != o) {
            o.bringToFront();
            g.invalidate();
        }
        if (o.getElevation() < FORCE_ELEVATION_PX) {
            o.setElevation(FORCE_ELEVATION_PX);
            o.setTranslationZ(FORCE_ELEVATION_PX);
        }
    }

    private void dumpViewChain(View v) {
        if (!Log.debug()) {
            return;
        }
        if (v == null) {
            Log.diag("POEM chain v=null");
            return;
        }
        StringBuilder sb = new StringBuilder("POEM chain");
        View c = v;
        int depth = 0;
        while (c != null && depth < 8) {
            sb.append(" <- ").append(c.getClass().getSimpleName())
                    .append("[vis=").append(c.getVisibility())
                    .append(",shown=").append(c.isShown())
                    .append(",w=").append(c.getWidth())
                    .append(",h=").append(c.getHeight())
                    .append("]");
            if (!(c.getParent() instanceof View)) {
                break;
            }
            c = (View) c.getParent();
            depth++;
        }
        Log.diag(sb.toString());
        ViewGroup g = v instanceof ViewGroup ? (ViewGroup) v : null;
        if (g != null) {
            StringBuilder kids = new StringBuilder("POEM kids n=" + g.getChildCount());
            int n = Math.min(g.getChildCount(), 12);
            for (int i = 0; i < n; i++) {
                View k = g.getChildAt(i);
                kids.append(" | ").append(i).append(":")
                        .append(k == null ? "null" : k.getClass().getName())
                        .append(k == null ? "" : ("[vis=" + k.getVisibility()
                                + ",h=" + k.getHeight() + "]"));
            }
            Log.diag(kids.toString());
        }
    }

    private void dumpMountTree() {
        if (!Log.debug()) {
            return;
        }
        OverlayView o = overlay;
        if (o == null || !(o.getParent() instanceof ViewGroup)) {
            Log.diag("POEM mountTree no parent");
            return;
        }
        ViewGroup g = (ViewGroup) o.getParent();
        StringBuilder sb = new StringBuilder("POEM mountTree "
                + g.getClass().getName() + " n=" + g.getChildCount());
        int n = Math.min(g.getChildCount(), 16);
        for (int i = 0; i < n; i++) {
            View k = g.getChildAt(i);
            sb.append(" | ").append(i).append(":")
                    .append(k == null ? "null" : k.getClass().getName())
                    .append(k == null ? "" : ("[vis=" + k.getVisibility()
                            + ",h=" + k.getHeight() + "]"));
        }
        Log.diag(sb.toString());
    }

    /**
     * 保证诗句层在最上面。
     *
     * <p>每帧调用，所以必须极便宜：只在真的被挤下去时才 bringToFront。
     * bringToFront 会重排整个父容器的子 View 并触发布局，绝不能每帧都做。
     */
    private void keepOnTop(ViewGroup g) {
        OverlayView o = overlay;
        if (o == null || !o.attached || o.windowMode || g == null) {
            return;
        }
        if (o.getParent() != g) {
            return;
        }
        final int n = g.getChildCount();
        if (n > 0 && g.getChildAt(n - 1) == o) {
            return;
        }
        o.bringToFront();
    }

    private boolean ensureOverlayAttached() {
        if (overlay != null && overlay.attached) {
            return true;
        }
        if (overlay == null) {
            overlay = new OverlayView(statusContainer.getContext());
        }
        overlay.listener = listener;
        Log.diag("POEM attach force=" + forceWindowMode
                + " parent=" + statusContainer.getClass().getSimpleName());
        if (!attachOverlayView(overlay)) {
            overlay = null;
            return false;
        }

        pushHolesToOverlay();
        return true;
    }
    private boolean attachOverlayView(OverlayView v) {
        View parent = statusContainer;
        if (parent instanceof ViewGroup) {
            try {
                ((ViewGroup) parent).addView(v, new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                v.attached = true;
                raiseAboveSiblings();
                Log.diag("POEM overlay attached as child of "
                        + parent.getClass().getSimpleName());
                return true;
            } catch (Throwable t) {
                Log.w("POEM overlay addView failed: " + t.getClass().getSimpleName());
            }
        }
        try {
            Context ctx = statusContainer.getContext();
            WindowManager wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
            if (wm == null) {
                return false;
            }
            int[] loc = new int[2];
            statusContainer.getLocationOnScreen(loc);
            int w = statusWidth();
            int h = statusHeight();
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    w, h, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT);
            if (forceWindowMode) {
                lp.flags |= WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON;
            }
            lp.gravity = Gravity.TOP | Gravity.START;
            lp.x = loc[0];
            lp.y = loc[1];
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
            wm.addView(v, lp);
            v.attached = true;
            v.windowMode = true;
            v.ownWindowManager = wm;
            windowManager = wm;
            Log.diag("POEM wm attached w=" + w + " h=" + h + " x=" + loc[0] + " y=" + loc[1]);
            return true;
        } catch (Throwable t) {
            Log.w("POEM wm overlay failed: " + t.getClass().getSimpleName());
            return false;
        }
    }
    private void detachOverlayView(OverlayView v) {
        try {
            if (v.windowMode) {
                WindowManager wm = v == overlay ? windowManager : v.ownWindowManager;
                if (wm != null) {
                    wm.removeViewImmediate(v);
                }
            } else if (v.getParent() instanceof ViewGroup) {
                ((ViewGroup) v.getParent()).removeView(v);
            }
        } catch (Throwable ignored) {

        }
        v.attached = false;
        v.windowMode = false;
        v.ownWindowManager = null;
    }

    private void removeOverlay() {
        if (overlay == null) {
            return;
        }
        detachOverlayView(overlay);
        overlay.running = false;
        if (listener != null) {
            listener.onOverlayDetached();
        }
    }
    private float measure(Paint paint, String text) {
        String key = text + '|' + paint.getTextSize() + '|'
                + statusContainer.getResources().getDisplayMetrics().density;
        Float cached = measureCache.get(key);
        if (cached != null) {
            return cached;
        }
        float w = paint.measureText(text);
        measureCache.put(key, w);
        return w;
    }
    final class OverlayView extends View implements Choreographer.FrameCallback {

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint.FontMetrics fm = new Paint.FontMetrics();
        private final int[] locTmp = new int[2];

        Listener listener;
        boolean attached;
        boolean windowMode;

        WindowManager ownWindowManager;
        boolean running;
        int viewW;
        int viewH;

        private String text = "";
        private float textWidth;
        private int speed = PoemConfig.SPEED_DEFAULT;
        private boolean bobEnabled;
        private long startNs;
        private long nextTargetNs;
        private long frameIntervalNs;
        private float lastLeft;
        private float lastTop;
        private float lastRight;
        private float lastBottom;
        private float currentLeft;
        private float baseline;

        private float baseBaseline;

        private float bobOffsetPx;
        private float currentTop;
        private float currentRight;
        private float currentBottom;

        int screenOriginX;
        int screenOriginY;

        void refreshScreenOrigin() {
            getLocationOnScreen(locTmp);
            screenOriginX = locTmp[0];
            screenOriginY = locTmp[1];
        }
        OverlayView(Context c) {
            super(c);
            paint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL));
            paint.setTextSize(13f * c.getResources().getDisplayMetrics().scaledDensity);
            paint.setColor(Color.WHITE);
            paint.setDither(false);
            paint.setFilterBitmap(false);

            setWillNotDraw(false);
            setLayerType(LAYER_TYPE_NONE, null);
            setClickable(false);
            setFocusable(false);
        }

        private final List<HoleRects.Hole> holeList = new ArrayList<>(2);

        final float[] segScreen = new float[HoleRects.MAX_HOLES + 1];
        final float[] segWidth = new float[HoleRects.MAX_HOLES + 1];
        private final float[] segText = new float[HoleRects.MAX_HOLES + 1];
        int segCount;
        private float finishLeft;

        void begin(String value, int speedPx, boolean bob, int w, int h, int color,
                   List<HoleRects.Hole> holes) {
            text = value;
            speed = Math.max(PoemConfig.SPEED_MIN, speedPx);
            frameIntervalNs = FramePacer.intervalNsForSpeed(speed);
            bobEnabled = bob;
            viewW = w;
            viewH = h;
            paint.setColor(color);
            textWidth = PoemRuntime.this.measure(paint, value);
            paint.getFontMetrics(fm);
            startNs = System.nanoTime();
            nextTargetNs = startNs;
            lastLeft = w;
            lastTop = 0f;
            lastRight = w;
            lastBottom = 0f;
            currentLeft = w;
            baseBaseline = (h - (fm.ascent + fm.descent)) / 2f;

            bobOffsetPx = bob ? ((-1f + 2f * bobRandom.nextFloat()) * BOB_AMPLITUDE_PX) : 0f;
            baseline = baseBaseline + bobOffsetPx;
            currentTop = baseline + fm.ascent;
            currentRight = w + textWidth;
            currentBottom = baseline + fm.descent;
            finishLeft = HoleRects.finishLeft(holes, textWidth);
            refreshScreenOrigin();
        }

        void applyTextColor(int color) {
            if (paint.getColor() == color) {
                return;
            }
            paint.setColor(color);
            if (attached) {
                invalidate();
            }
        }

        void clearText() {
            if (text == null || text.isEmpty()) {
                return;
            }
            text = "";
            textWidth = 0f;
            segCount = 0;
            if (attached) {
                invalidate();
            }
        }

        void postFrame() {
            if (running) {
                return;
            }
            running = true;
            startNs = System.nanoTime();
            nextTargetNs = startNs;
            frameIntervalNs = FramePacer.intervalNsForSpeed(speed);
            Choreographer.getInstance().postFrameCallback(this);
        }
        void stopFrame() {
            running = false;
            Choreographer.getInstance().removeFrameCallback(this);
        }
        @Override
        public void doFrame(long frameTimeNanos) {
            if (!running || !attached) {
                return;
            }
            long t0 = SystemClock.elapsedRealtimeNanos();
            updateFrame(frameTimeNanos);
            totalFrameCostNs += SystemClock.elapsedRealtimeNanos() - t0;
            if (running) {
                scheduleNext(frameTimeNanos);
            }
        }

        private void scheduleNext(long frameTimeNanos) {
            nextTargetNs += frameIntervalNs;
            if (nextTargetNs <= frameTimeNanos) {

                nextTargetNs = frameTimeNanos + frameIntervalNs;
                skippedFrames++;
            }
            long delayNs = nextTargetNs - frameTimeNanos;
            long delayMs = delayNs <= 0 ? 0 : (delayNs + 999_999L) / 1_000_000L;
            Choreographer.getInstance().postFrameCallbackDelayed(this, delayMs);
        }

        private void updateFrame(long frameTimeNanos) {
            long elapsed = frameTimeNanos - startNs;
            if (elapsed < 0) {
                elapsed = 0;
            }
            float traveled = speed * (elapsed / 1_000_000_000f);
            float left = viewW - traveled;
            float width = textWidth;

            boolean finished = left < finishLeft;
            if (!finished && Math.abs(left - currentLeft) < MIN_DRAW_DELTA_PX) {
                skippedFrames++;
                return;
            }
            float base = baseBaseline + (bobEnabled ? bobOffsetPx : 0f);
            float top = base + fm.ascent;
            float bottom = base + fm.descent;
            float right = left + width;
            lastLeft = currentLeft;
            lastTop = currentTop;
            lastRight = currentRight;
            lastBottom = currentBottom;
            currentLeft = left;
            baseline = base;
            currentTop = top;
            currentRight = right;
            currentBottom = bottom;

            frameCount++;

            if (wormholeSegmentsReady()) {
                segCount = HoleRects.wormholeSegments(holeList, currentLeft, currentRight,
                        screenOriginX, segScreen, segText, segWidth, segScreen.length);
            } else {
                segCount = 0;
            }
            if (listener != null) {

                listener.onFrame(left, top, right, bottom);
            }

            int dl = (int) Math.floor(Math.min(lastLeft, currentLeft)) - 2;
            int dr = (int) Math.ceil(Math.max(lastRight, currentRight)) + 2;
            if (dl < 0) {
                dl = 0;
            }
            if (dr > viewW) {
                dr = viewW;
            }
            if (dr > dl) {
                invalidate(dl, 0, dr, viewH);
            }

            if (finished) {
                running = false;
                Choreographer.getInstance().removeFrameCallback(this);
                if (listener != null) {
                    listener.onFinished();
                }
            }
        }
        private boolean wormholeSegmentsReady() {
            for (int i = 0; i < holeList.size(); i++) {
                HoleRects.Hole h = holeList.get(i);
                if (h != null && h.valid() && h.enabled && h.isWormhole()) {
                    return true;
                }
            }
            return false;
        }

        void setHoles(List<HoleRects.Hole> list) {
            holeList.clear();
            if (list != null) {
                for (HoleRects.Hole h : list) {

                    if (h != null && h.valid() && h.enabled
                            && holeList.size() < HoleRects.MAX_HOLES) {
                        holeList.add(h);
                    }
                }
            }
            if (attached) {
                invalidate();
            }
        }

        private void drawTextWithHoles(Canvas canvas) {
            if (holeList.isEmpty()) {
                canvas.drawText(text, currentLeft, baseline, paint);
                return;
            }

            final int saveCount = canvas.save();

            for (int i = 0; i < holeList.size(); i++) {
                HoleRects.Hole h = holeList.get(i);
                if (h == null || !h.valid() || !h.enabled || h.isWormhole()) {
                    continue;
                }
                canvas.clipOutRect(h.left - screenOriginX, 0f, h.right - screenOriginX, viewH);
            }

            if (segCount > 0) {
                for (int i = 0; i < segCount; i++) {
                    float w = segWidth[i];
                    if (w <= 0f) {
                        continue;
                    }
                    canvas.save();
                    canvas.clipRect(segScreen[i], 0f, segScreen[i] + w, viewH);
                    canvas.translate(segScreen[i] - segText[i], 0f);
                    canvas.drawText(text, currentLeft, baseline, paint);
                    canvas.restore();
                }
            } else {

                canvas.drawText(text, currentLeft, baseline, paint);
            }

            canvas.restoreToCount(saveCount);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (text == null || text.isEmpty()) {
                return;
            }
            drawTextWithHoles(canvas);
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            attached = true;
            refreshScreenOrigin();
            if (listener != null) {
                listener.onOverlayAttached();
            }
        }

        @Override
        protected void onDetachedFromWindow() {
            running = false;
            attached = false;
            Choreographer.getInstance().removeFrameCallback(this);
            super.onDetachedFromWindow();
        }
    }
}
