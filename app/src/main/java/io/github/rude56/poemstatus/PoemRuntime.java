package io.github.rude56.poemstatus;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
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

/**
 * SystemUI 内的绘制与动画执行层。
 * 帧内硬约束：零分配、零日志、零 IO、零 View 树扫描；位置只由 {@code frameTimeNanos} 算出，掉帧不补帧。
 */
final class PoemRuntime {

    interface Listener {
        /** 每帧的遮挡回调（只做矩形比较与必要 invalidate）。 */
        void onFrame(float left, float top, float right, float bottom);

        /** 诗句完全滚出状态栏。 */
        void onFinished();

        void onOverlayAttached();

        void onOverlayDetached();
    }

    private static final int MAX_MEASURE_CACHE = 64;
    /** 上下浮动幅度（设备像素）。 */
    static final float BOB_AMPLITUDE_PX = 1.5f;

    /** 位移小于该值（设备像素）就不绘制：低速自动降帧。 */
    static final float MIN_DRAW_DELTA_PX = FramePacer.MIN_DRAW_DELTA_PX;

    private final Handler handler = new Handler(Looper.getMainLooper());
    /** 每首诗的浮动偏移随机源，与播放顺序的随机源独立。 */
    private final java.util.Random bobRandom = new java.util.Random();

    /** 文本宽度 LRU：key = text|textSize|density，不落盘。 */
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
    private WindowManager.LayoutParams windowParams;

    /** 当前生效的洞与模式，控制器在每次播放前写入。 */
    private List<HoleRects.Hole> configHoles = new ArrayList<>(2);
    /**
     * 遮挡分段复用数组（帧内零分配）。容量必须覆盖 {@link HoleRects#visibleSegments} 的最坏输出
     * {@code (MAX_HOLES + 1)²}：取小了会静默丢段，即「字画出来了却没有遮挡」。
     */
    private final float[] visSegLeft =
            new float[(HoleRects.MAX_HOLES + 1) * (HoleRects.MAX_HOLES + 1)];
    private final float[] visSegRight =
            new float[(HoleRects.MAX_HOLES + 1) * (HoleRects.MAX_HOLES + 1)];

    private Listener listener;
    private int textColor = Color.WHITE;

    // 诊断计数，由控制器每 60 秒汇总一次，不逐帧打日志。
    private long frameCount;
    private long skippedFrames;
    private long totalFrameCostNs;

    void setStatusContainer(View v) {
        // 状态栏窗口被系统重建时，旧诗句层挂在废弃的父容器上，必须摘掉，否则跟着旧窗口一起泄漏。
        if (overlay != null && statusContainer != null && statusContainer != v) {
            removeOverlay();
        }
        statusContainer = v;
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

    /** 设置洞列表。模式由每个洞自带（{@link HoleRects.Hole#mode}），没有全局模式。 */
    void setHoleConfig(List<HoleRects.Hole> holes) {
        configHoles.clear();
        if (holes != null) {
            for (HoleRects.Hole h : holes) {
                if (h != null && h.valid() && configHoles.size() < HoleRects.MAX_HOLES) {
                    configHoles.add(h);
                }
            }
        }
        HolePuncher.setHoles(configHoles);
        pushHolesToOverlay();
    }

    /**
     * 把当前洞列表推给诗句层。配置变化与 Overlay 创建/重建两个时机都必须同步：
     * 漏一次，诗句层洞列表就永远为空（refreshCutout 按 holesJson 去重，不会补跑），文字会直接压在图标上。
     */
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

    /** 布局变化（旋转 / 状态栏尺寸改变）后刷新一次几何，否则 HolePuncher 的原点与宽高过期，遮挡整体偏移。 */
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

    /** 开始一首诗。{@code text} 是实际显示文本（作者/出处已按开关拼接）。 */
    boolean start(String text, int speedPx, boolean bob, Listener l) {
        if (statusContainer == null || text == null || text.isEmpty()) {
            return false;
        }
        listener = l;
        if (!ensureOverlayAttached()) {
            return false;
        }
        overlay.begin(text, speedPx, bob, statusWidth(), statusHeight(), textColor, configHoles);
        // 诗句层纯透明：只画字。原生内容的遮挡统一由 HolePuncher 在内容根上挖洞完成。
        DebugFrames.reset();
        // 内容根每首重取：SystemUI 会重建状态栏子视图，缓存旧引用会挖不到。
        HolePuncher.bindRoot(ViewTree.findContentRoot(statusContainer));
        HolePuncher.setHoles(configHoles);
        overlay.postFrame();
        return true;
    }

    /**
     * 遮挡策略：只遮文字真正可见的段（隧道洞内的段不遮）。{@code (l,t,r,b)} 是诗句层本地坐标，
     * HolePuncher 只收屏幕坐标，所以每个出口都要加 {@code overlay.screenOriginX}；分段复用帧里算好的结果。
     */
    void updateOcclusion(float l, float t, float r, float b) {
        OverlayView o = overlay;
        final int originX = o == null ? 0 : o.screenOriginX;
        final int barW = o == null ? 0 : (o.getWidth() > 0 ? o.getWidth() : o.viewW);
        final int visible = HoleRects.visibleSegments(configHoles,
                o == null ? null : o.segScreen, o == null ? null : o.segWidth,
                o == null ? 0 : o.segCount,
                l, r, originX, barW,
                visSegLeft, visSegRight);
        // visible <= 0：这一帧没有任何可见的字，传 0 段即可，HolePuncher 会把原生内容完整画回来。
        HolePuncher.updatePoemSegments(visSegLeft, visSegRight, visible, t, b);
    }

    /** 停止动画：只 clearText + 停帧 + 清洞，不 removeOverlay（换首不增删 View）。 */
    void stop() {
        if (overlay != null) {
            overlay.stopFrame();
        }
        // 清掉画布上的「洞」，让原生内容完整重绘回来。
        HolePuncher.clear();
        // 刻意不移除 OverlayView：add/removeView 会触发整棵状态栏 requestLayout + 重新挂载，
        // 比一次 invalidate 贵一个数量级；真正的释放发生在 SystemUI 重建状态栏时。
        if (overlay != null) {
            overlay.running = false;
            // 必须清文字：暂停时诗句可能正停在状态栏中间，只停帧会把它留在屏幕上。
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
        // 新建/重新挂载的诗句层是空白的：洞列表必须在这里补一次（见 pushHolesToOverlay）。
        pushHolesToOverlay();
        return true;
    }

    /** 把 OverlayView 挂到状态栏上：首选状态栏窗口的子 View（同 surface 同坐标系），失败退回系统级 overlay。 */
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
        // 兜底：系统级 overlay。
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
            windowParams = lp;
            return true;
        } catch (Throwable t) {
            Log.w("POEM wm overlay failed: " + t.getClass().getSimpleName());
            return false;
        }
    }

    /** 从状态栏上摘掉 OverlayView（只在状态栏窗口被系统重建时调用）。 */
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
            // 移除失败不影响功能，下次挂载会重建。
        }
        v.attached = false;
        v.windowMode = false;
        v.ownWindowManager = null;
    }

    /** 摘掉诗句层：只在状态栏窗口被系统重建时调用，正常换首绝不走这里。 */
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

    /** 缓存测量：首次播放时才用 SystemUI 的真实字体测量。 */
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

    /**
     * 自定义 Overlay：只画一行文字。
     * 帧函数 {@link #doFrame(long)} 内不允许任何分配与采样（单元测试会强制这一点）。
     */
    final class OverlayView extends View implements Choreographer.FrameCallback {

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint.FontMetrics fm = new Paint.FontMetrics();
        private final int[] locTmp = new int[2];

        Listener listener;
        boolean attached;
        boolean windowMode;
        /** 走系统级 overlay 时自己持有的 WindowManager。 */
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
        /** 文字的固定基准线。上下浮动必须基于它算，不能基于上一帧的 baseline，否则偏移会逐帧累加。 */
        private float baseBaseline;
        /** 这一首诗的固定垂直偏移（上下浮动用），整首不变，帧内只做加法。 */
        private float bobOffsetPx;
        private float currentTop;
        private float currentRight;
        private float currentBottom;

        /** 诗句层在屏幕上的原点（缓存）：只在挂载/布局变化时刷新，帧内只读字段，不能调 getLocationOnScreen。 */
        int screenOriginX;
        int screenOriginY;

        /** 刷新缓存的原点。只在挂载 / 布局变化时调用，不进帧循环。 */
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
            // 不给硬件层、不做位图缓存、不启用阴影/模糊/渐变。
            setWillNotDraw(false);
            setLayerType(LAYER_TYPE_NONE, null);
            setClickable(false);
            setFocusable(false);
        }

        /** 洞列表（屏幕坐标）与模式：虫洞模式下洞内不画字。 */
        private final List<HoleRects.Hole> holeList = new ArrayList<>(2);
        /**
         * 虫洞分段输出缓冲：洞数 + 1 段，帧内零分配。
         * 由 {@link #updateFrame} 每帧算一次，绘制层与遮挡层共用（各算一遍等于每帧两次全洞扫描）。
         */
        final float[] segScreen = new float[HoleRects.MAX_HOLES + 1];
        final float[] segWidth = new float[HoleRects.MAX_HOLES + 1];
        private final float[] segText = new float[HoleRects.MAX_HOLES + 1];
        /** 本帧有效分段数（0 = 没有虫洞，整条连续绘制）。 */
        int segCount;

        /**
         * 整句离屏时轨道左端的临界值（缓存，帧内只做一次比较）。必须用 {@link HoleRects#finishLeft}：
         * 虫洞把整句往左瞬移，用 {@code left + width < 0} 会每次换首前空等「洞宽 ÷ 速度」。
         */
        private float finishLeft;

        /**
         * 开始一首诗。{@code holes} 必须显式传入而不能读 {@link #holeList}：洞在配置变化时才推送，
         * 而这里跑在推送前，读字段会拿到上一首的洞，{@link #finishLeft} 就按旧洞宽算。
         */
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
            // 上下浮动：每首随机一个固定偏移，整首不变。不能逐帧摆动，否则高度一直在变、很晃眼。
            bobOffsetPx = bob ? ((-1f + 2f * bobRandom.nextFloat()) * BOB_AMPLITUDE_PX) : 0f;
            baseline = baseBaseline + bobOffsetPx;
            currentTop = baseline + fm.ascent;
            currentRight = w + textWidth;
            currentBottom = baseline + fm.descent;
            // 结束判据用 finishLeft（每首只算一次，只依赖文字宽度与洞宽），帧内只做一次比较。
            finishLeft = HoleRects.finishLeft(holes, textWidth);
            refreshScreenOrigin();
        }

        /** 低频（颜色变化时）更新文字色；不在帧内调用。 */
        void applyTextColor(int color) {
            if (paint.getColor() == color) {
                return;
            }
            paint.setColor(color);
            if (attached) {
                invalidate();
            }
        }

        /**
         * 清空显示（停止播放时调用）：只把 {@code text} 置空并重绘那一带，不做整树 invalidate。
         */
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

        /** 是否已经有帧回调排在队列里（避免重复 post）。 */
        private boolean frameScheduled;

        void postFrame() {
            if (running) {
                return;
            }
            running = true;
            startNs = SystemClock.elapsedRealtimeNanos();
            nextTargetNs = startNs;
            frameIntervalNs = FramePacer.intervalNsForSpeed(speed);
            frameScheduled = true;
            Choreographer.getInstance().postFrameCallback(this);
        }

        void stopFrame() {
            running = false;
            frameScheduled = false;
            Choreographer.getInstance().removeFrameCallback(this);
        }

        @Override
        public void doFrame(long frameTimeNanos) {
            frameScheduled = false;
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
                // 掉帧：直接对齐到当前时间之后，不补帧。
                nextTargetNs = frameTimeNanos + frameIntervalNs;
                skippedFrames++;
            }
            long delayNs = nextTargetNs - frameTimeNanos;
            long delayMs = delayNs <= 0 ? 0 : (delayNs + 999_999L) / 1_000_000L;
            frameScheduled = true;
            Choreographer.getInstance().postFrameCallbackDelayed(this, delayMs);
        }

        /** 帧内核心：只做算术、矩形比较与一次 invalidate。 */
        private void updateFrame(long frameTimeNanos) {
            long elapsed = frameTimeNanos - startNs;
            if (elapsed < 0) {
                elapsed = 0;
            }
            float traveled = speed * (elapsed / 1_000_000_000f);
            float left = viewW - traveled;
            float width = textWidth;
            // 诗句位置永远由时间与速度算出，任何「洞」都不得改写它（否则开头缺字、速度突变），
            // 洞只在绘制层体现（见 drawTextWithHoles）；但结束判据必须用 finishLeft 计入虫洞瞬移。
            boolean finished = left < finishLeft;

            // 位移不足 1 像素就不绘制：低速时自动降帧，肉眼无感，最省电。
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
            // 本帧的虫洞分段只算一次：绘制层与遮挡回调共用同一份结果。
            if (wormholeSegmentsReady()) {
                segCount = HoleRects.wormholeSegments(holeList, currentLeft, currentRight,
                        screenOriginX, segScreen, segText, segWidth, segScreen.length);
            } else {
                segCount = 0;
            }
            if (listener != null) {
                // 必须传「轨道上的原始位置」（诗句层本地坐标）：updateOcclusion 会自己加原点换成屏幕坐标，
                // 这里先换算一次就会加两遍，遮挡整体偏移一个状态栏原点。
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

        /** 是否存在至少一个虫洞（只有虫洞才需要分段绘制）。 */
        private boolean wormholeSegmentsReady() {
            for (int i = 0; i < holeList.size(); i++) {
                HoleRects.Hole h = holeList.get(i);
                if (h != null && h.valid() && h.isWormhole()) {
                    return true;
                }
            }
            return false;
        }

        /** 设置洞列表；每个洞自带模式（虫洞/隧道）。 */
        void setHoles(List<HoleRects.Hole> list) {
            holeList.clear();
            if (list != null) {
                for (HoleRects.Hole h : list) {
                    if (h != null && h.valid() && holeList.size() < HoleRects.MAX_HOLES) {
                        holeList.add(h);
                    }
                }
            }
            if (attached) {
                invalidate();
            }
        }

        /**
         * 按「洞」处理诗句绘制。位置永远由时间与速度算出，洞绝不参与，所以不会开头缺字或速度突变。
         * 隧道：位置不动，洞内不画（那部分字被洞挡掉）；虫洞：把洞那段屏幕抽掉，跨洞口时屏幕位置跳变。
         */
        private void drawTextWithHoles(Canvas canvas) {
            if (holeList.isEmpty()) {
                canvas.drawText(text, currentLeft, baseline, paint);
                return;
            }

            // save/restore 必须严格配对，否则画布状态泄漏（表现为错位与闪烁）。
            final int saveCount = canvas.save();

            // ① 隧道：位置不动，洞内不画。
            for (int i = 0; i < holeList.size(); i++) {
                HoleRects.Hole h = holeList.get(i);
                if (h == null || !h.valid() || h.isWormhole()) {
                    continue;
                }
                canvas.clipOutRect(h.left - screenOriginX, 0f, h.right - screenOriginX, viewH);
            }

            // ② 虫洞：直接复用帧里算好的分段（跨洞口瞬移）。
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
                // 没有虫洞：整条直接画（隧道已在 ① 挖掉）。
                canvas.drawText(text, currentLeft, baseline, paint);
            }

            canvas.restoreToCount(saveCount);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (text == null || text.isEmpty()) {
                return;
            }
            // 只画字，不画任何底色：文字之外完全透明。
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
            frameScheduled = false;
            attached = false;
            Choreographer.getInstance().removeFrameCallback(this);
            super.onDetachedFromWindow();
        }
    }
}
