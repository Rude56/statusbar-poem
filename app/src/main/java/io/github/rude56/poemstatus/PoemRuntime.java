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

    private final java.util.Random bobRandom = new java.util.Random();

    private final Map<String, Float> measureCache =
            new LinkedHashMap<String, Float>(MAX_MEASURE_CACHE, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Float> eldest) {
                    return size() > MAX_MEASURE_CACHE;
                }
            };

    private View statusContainer;
    private OverlayView overlay;
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
    boolean setStatusContainer(View v) {
        boolean replaced = false;
        if (overlay != null && statusContainer != null && statusContainer != v) {
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
            return 0;
        }
        return v.getHeight() > 0 ? v.getHeight()
                : (int) (28 * v.getResources().getDisplayMetrics().density);
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

        HolePuncher.bindRoot(ViewTree.findContentRoot(statusContainer));
        HolePuncher.setHoles(configHoles);
        overlay.postFrame();
        return true;
    }
    void updateOcclusion(float l, float t, float r, float b) {
        OverlayView o = overlay;
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

    private boolean ensureOverlayAttached() {
        if (overlay != null && overlay.attached) {
            return true;
        }
        if (overlay == null) {
            overlay = new OverlayView(statusContainer.getContext());
        }
        overlay.listener = listener;
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
            lp.gravity = Gravity.TOP | Gravity.START;
            lp.x = loc[0];
            lp.y = loc[1];
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
            wm.addView(v, lp);
            v.attached = true;
            v.windowMode = true;
            v.ownWindowManager = wm;
            windowManager = wm;
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
            startNs = SystemClock.elapsedRealtimeNanos();
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
            startNs = SystemClock.elapsedRealtimeNanos();
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
