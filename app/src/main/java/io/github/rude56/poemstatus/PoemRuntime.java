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
 * SystemUI 内部的绘制与动画执行层。
 *
 * 帧内硬约束：零分配、零日志、零 IO、零 View 树扫描；位置由 {@code frameTimeNanos}
 * 直接算出，掉帧就跳到最新位置不补帧；只 invalidate 文字新旧矩形的并集。
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
    /** 上下浮动幅度：只有 ±1.5 设备像素，肉眼几乎无感，也不会蹭到状态栏上下沿。 */
    static final float BOB_AMPLITUDE_PX = 1.5f;

    /** 位移小于该值（设备像素）就不绘制 —— 低速时自动降帧。定义在 {@link FramePacer}。 */
    static final float MIN_DRAW_DELTA_PX = FramePacer.MIN_DRAW_DELTA_PX;

    private final Handler handler = new Handler(Looper.getMainLooper());
    /** 每首诗的浮动偏移用独立随机源，避免和播放顺序的随机互相影响。 */
    private final java.util.Random bobRandom = new java.util.Random();

    /** 文本宽度 LRU：key = text|textSize|density。绝不持久化到磁盘。 */
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

    /** 当前生效的洞与模式，由控制器在每次播放前写入。 */
    private List<HoleRects.Hole> configHoles = new ArrayList<>(2);
    /**
     * 遮挡分段用的复用数组（帧内零分配）。
     *
     * 容量必须覆盖 {@link HoleRects#visibleSegments} 的最坏输出：
     * 虫洞切出的每段都可能再被隧道洞切开，上界 {@code (MAX_HOLES + 1)²}。
     * 取小了会静默丢段 —— 丢了段就是「字画出来了却没有遮挡」，而且没有任何日志。
     */
    private final float[] visSegLeft =
            new float[(HoleRects.MAX_HOLES + 1) * (HoleRects.MAX_HOLES + 1)];
    private final float[] visSegRight =
            new float[(HoleRects.MAX_HOLES + 1) * (HoleRects.MAX_HOLES + 1)];

    private Listener listener;
    private int textColor = Color.WHITE;

    // 诊断计数（每 60 秒由控制器汇总一次，不做逐帧日志）
    private long frameCount;
    private long skippedFrames;
    private long totalFrameCostNs;

    void setStatusContainer(View v) {
        // 状态栏窗口被系统重建（换分辨率、SystemUI 重新 inflate）时，
        // 旧的诗句层挂在已经废弃的父容器上：必须摘掉，否则会跟着旧窗口一起泄漏。
        // 这是 removeOverlay() 唯一还需要存在的理由 —— 正常换首不走这里。
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

    /**
     * 设置洞列表。模式是每个洞各自携带的（{@link HoleRects.Hole#mode}），不存在全局模式。
     */
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
     * 把当前洞列表推给诗句层。
     *
     * 必须在两个时机都同步：配置变化时、Overlay 创建/重建时。
     * 全新启动时 {@code refreshCutout()} 首次运行早于 Overlay 创建，那次
     * {@code overlay.setHoles()} 会被跳过；而 {@code refreshCutout()} 又按
     * {@code holesJson} 去重，内容不变就再也不会跑。漏掉的结果是诗句层的洞列表
     * 永远为空：整句连续绘制、不跳过虫洞，而遮挡却按虫洞切出的段去挖 ——
     * 文字直接压在状态栏图标上。
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

    /**
     * 布局变化（旋转 / 状态栏尺寸改变）后刷新一次几何。
     *
     * 旋转后 {@code HolePuncher} 用的原点与宽高会过期，整个遮挡块偏移。
     * 这里补一次，代价只有几次 {@code getLocationOnScreen}，不在帧循环上。
     */
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

    /**
     * 开始一首诗。
     *
     * @param text    实际显示文本（作者/出处已按开关拼接）
     * @param speedPx 基础速度
     */
    boolean start(String text, int speedPx, boolean bob, Listener l) {
        if (statusContainer == null || text == null || text.isEmpty()) {
            return false;
        }
        listener = l;
        if (!ensureOverlayAttached()) {
            return false;
        }
        overlay.begin(text, speedPx, bob, statusWidth(), statusHeight(), textColor, configHoles);
        // 诗句层是纯透明覆盖：只画字，不画任何底色。
        // 原生内容的遮挡统一由 HolePuncher 在内容根上挖洞完成 ——
        // 一次裁剪就能穿透时钟与所有图标（含 Compose 画的）。
        DebugFrames.reset();
        // 内容根每首诗重取一次：SystemUI 会重建状态栏子视图，缓存旧引用会挖不到。
        HolePuncher.bindRoot(ViewTree.findContentRoot(statusContainer));
        HolePuncher.setHoles(configHoles);
        overlay.postFrame();
        return true;
    }

    /**
     * 遮挡策略：只遮住文字真正显示出来的部分，不能拿文字的整体包围盒去遮。
     *
     * 隧道：字在洞内被遮住、屏幕上根本看不见，那一段就不该再遮挡状态栏。
     * 虫洞：字被瞬移到一侧，遮挡要跟着瞬移后的位置走。
     *
     * 坐标系契约：{@code (l,t,r,b)} 是诗句层的本地坐标，
     * 而 {@link HolePuncher} 只接受屏幕坐标（它自己减 {@code rootLeft}），
     * 所以任何一条出口都必须先加上诗句层的屏幕原点。
     *
     * 分段不在这里重算：{@link OverlayView#updateFrame} 已经算过一次，
     * 绘制层直接复用同一份结果（每帧省掉一次完整的虫洞分段扫描）。
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
        // visible <= 0 表示这一帧没有任何可见的字（全在隧道里/已在屏幕外）：
        // 传 0 段即可，HolePuncher 会据此不挖洞并把原生内容完整画回来。
        HolePuncher.updatePoemSegments(visSegLeft, visSegRight, visible, t, b);
    }

    /** 停止动画并移除 Overlay（CLOCK/PAUSED/关闭 都必须走这里）。 */
    void stop() {
        if (overlay != null) {
            overlay.stopFrame();
        }
        // 清掉画布上的「洞」，让原生内容完整重绘回来。
        HolePuncher.clear();
        // 注意：这里刻意不移除 OverlayView。
        //
        // add/removeView 会触发整棵状态栏的 requestLayout + 重新挂载，比一次普通
        // invalidate 贵一个数量级，还会连带重跑 onAttachedToWindow → refreshScreenOrigin
        // → colorSampler.startTracking（取色推送被反复注册/注销），以及重新
        // findContentRoot（递归扫整棵 View 树）。诗句层是纯透明覆盖、不占触摸、
        // 不持有资源，留一个空 View 挂着比反复增删便宜得多；真正的释放发生在
        // SystemUI 自己重建状态栏时（onDetachedFromWindow 会把 attached 复位）。
        if (overlay != null) {
            overlay.running = false;
            // 必须把文字清掉：暂停时诗句很可能正停在状态栏中间，
            // 只停帧回调而不清文字，它会原地留在屏幕上。
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
        // 新建/重新挂载的诗句层是一片空白：洞列表必须在这里补一次（详见 pushHolesToOverlay()）。
        pushHolesToOverlay();
        return true;
    }

    /**
     * 把 OverlayView 挂到状态栏上。
     *
     * 首选作为状态栏窗口的子 View —— 与状态栏同一 surface、同一坐标系；
     * 失败时退回系统级 overlay（SystemUI 已持 SYSTEM_ALERT_WINDOW）。
     */
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

    /**
     * 摘掉诗句层（只在状态栏窗口被系统重建时调用）。
     *
     * 正常换首绝不走这里：{@code addView/removeView} 会让整棵状态栏
     * 重新 requestLayout + 重新挂载，比一次 invalidate 贵一个数量级。
     */
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
     *
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
        /** 文字的固定基准线。上下浮动必须基于它计算，不能基于上一帧的 baseline，
         *  否则每帧都会把偏移量再累加一次，几十帧后就飘出状态栏。 */
        private float baseBaseline;
        /** 这一首诗的固定垂直偏移（上下浮动用）。整首保持不变，帧内只做加法。 */
        private float bobOffsetPx;
        private float currentTop;
        private float currentRight;
        private float currentBottom;

        /**
         * 诗句层在屏幕上的原点（缓存）。
         *
         * 只在布局/旋转时才会变，由 {@link #refreshScreenOrigin()} 在挂载与布局变化时
         * 刷新一次，帧内只读字段 —— 帧内不能调 {@code getLocationOnScreen}。
         */
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
         *
         * 这三个数组由 {@link #updateFrame} 每帧计算一次，然后被绘制层与遮挡层共用 ——
         * 两层各算一遍等于每帧做两次全洞扫描。
         */
        final float[] segScreen = new float[HoleRects.MAX_HOLES + 1];
        final float[] segWidth = new float[HoleRects.MAX_HOLES + 1];
        private final float[] segText = new float[HoleRects.MAX_HOLES + 1];
        /** 本帧有效分段数（0 = 没有虫洞，整条连续绘制）。 */
        int segCount;

        /**
         * 整句离屏时轨道左端的临界值（缓存，帧内只做一次比较）。
         *
         * 见 {@link HoleRects#finishLeft}：虫洞把整句往左瞬移，所以「轨道走完」
         * 比「屏幕上没字了」晚一个洞宽，用 {@code left + width < 0} 会每次换首前空等
         * 「洞宽 ÷ 速度」。
         *
         * 这个值只跟「文字宽度 + 洞宽」有关，两者都在 {@link #begin} 时就确定，
         * 所以在这里算一次即可，绝不进帧循环。
         */
        private float finishLeft;

        /**
         * 开始一首诗。
         *
         * @param holes 本首生效的洞列表。必须显式传入而不能读 {@link #holeList}：
         *              洞是配置变化时才推送的，而 {@code begin} 在推送前就跑了，
         *              读字段会拿到上一首的洞，结束时机（{@link #finishLeft}）就按旧洞宽算。
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
            // 上下浮动：每首随机一个固定偏移（±1.5px），整首保持不变。
            // 不能逐帧摆动，否则一首诗里高度一直在变，很晃眼。
            bobOffsetPx = bob ? ((-1f + 2f * bobRandom.nextFloat()) * BOB_AMPLITUDE_PX) : 0f;
            baseline = baseBaseline + bobOffsetPx;
            currentTop = baseline + fm.ascent;
            currentRight = w + textWidth;
            currentBottom = baseline + fm.descent;
            // 结束判据：虫洞把整句往左瞬移，屏幕上的字比轨道早一个洞宽消失。
            // 每首只算一次（只依赖文字宽度与洞宽），帧内只做一次比较。
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
         * 清空显示（停止播放时调用）。
         *
         * 只把 {@code text} 置空并重绘那一带，不做整树 invalidate ——
         * 暂停/切歌是每首都会发生的事，全量重绘整条状态栏纯属浪费。
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
            // 诗句位置永远由时间与速度直接算出，任何「洞」都不得改写它 ——
            // 在帧里改位置会导致开头缺字、速度突变。洞的效果只在绘制层体现（见 drawTextWithHoles）。
            //
            // 但「什么时候算结束」必须考虑虫洞：虫洞把整句往左瞬移一个洞宽，
            // 屏幕上最后一个字早在一个洞宽时就没了，用 `left + width < 0` 会空等「洞宽 ÷ 速度」。
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
                // 这里必须传「轨道上的原始位置」（诗句层本地坐标）。
                // updateOcclusion 会自己加上缓存的原点换成屏幕坐标；
                // 如果这里先换算一次，原点会被加两遍 ——
                // 表现为遮挡整体偏移一个状态栏原点。
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
         * 按「洞」处理诗句绘制。
         *
         * 诗句位置永远由时间与速度算出，洞绝不参与，所以不会出现开头缺字或速度突变。
         * 两种模式的差别只在绘制：
         * 隧道（火车进隧道）—— 文字位置不动，落进洞里的那部分被遮住看不见，
         * 所以洞宽内的字会「丢」，那正是被洞挡掉的效果。
         * 虫洞（传送门）—— 字到洞口瞬移到另一侧，一个字都不丢；
         * 实现上把洞那段屏幕抽掉（压缩轨道），跨过洞口时屏幕位置从洞右沿跳到洞左沿。
         *
         * 两种模式都不碰状态栏原生内容；洞内不画字，所以自然露出下方的正常状态栏。
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
