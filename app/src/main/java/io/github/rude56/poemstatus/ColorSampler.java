package io.github.rude56.poemstatus;

import android.view.View;
import android.widget.TextView;

/**
 * 诗句文字取色：<b>直接读取状态栏前景色</b>。
 *
 * <ol>
 *   <li>系统给状态栏图标/文字算好的 tint（{@link TintState}，来自
 *       {@code DarkIconDispatcherImpl.mIconTint}）；</li>
 *   <li>原生时钟 {@code getCurrentTextColor()}；</li>
 *   <li>系统状态栏外观位 {@code APPEARANCE_LIGHT_STATUS_BARS}；</li>
 *   <li>状态栏里任一可见 TextView 的文字色。</li>
 * </ol>
 *
 * <p><b>省电要点</b>：以前这里是个 300ms 的定时器，播放期间每秒醒 3.3 次去
 * 「问一句颜色变了没」。现在改成<b>推送</b> —— {@link TintState} 在系统真正
 * 改变状态栏外观时会回调 {@link #onSystemTintChanged()}，我们只在那一刻重算一次。
 * 于是爬行阶段（本来就有帧回调）不再额外引入任何定时器。
 *
 * <p>唯一的例外是「Hook 没装上」：那时 {@link TintState} 永远不会被填，
 * 推送也就永远不会来。这种情况退化为 2 秒一次的低频兜底轮询
 * （见 {@link #SAMPLE_INTERVAL_MS}），且只在播放期间运行。
 *
 * <p>取色绝不出现在逐帧动画函数里 —— 帧循环只使用这里缓存下来的颜色。
 */
final class ColorSampler {

    /**
     * 兜底轮询间隔：仅用于 {@link TintState} 拿不到任何数据的降级路径。
     *
     * <p>正常情况下这个定时器根本不会启动。
     */
    private static final long SAMPLE_INTERVAL_MS = 2000L;

    /**
     * 推送合流窗口。
     *
     * <p>系统图标色切换是带动画的，{@code applyIconTint} 在动画期间每帧都会调一次。
     * 250ms 足够覆盖整段动画，又明显短于人眼能察觉的延迟。
     */
    private static final long COALESCE_MS = 250L;

    /**
     * 取色诊断日志的最短间隔。
     *
     * <p>以前是 10 秒。但 {@link #sample} 每首诗开始时都会跑一次，
     * 而一首诗大约 13 秒 —— 10 秒的间隔等于「几乎每首打一行」，
     * 24 小时下来 6000+ 行。取色是稳定状态，5 分钟一行足够定位问题。
     */
    private static final long DIAG_LOG_MS = 300_000L;

    private final android.os.Handler handler =
            new android.os.Handler(android.os.Looper.getMainLooper());
    private int cachedColor = ColorResolver.DEFAULT_LIGHT_TEXT;
    private String lastDiagnostic = "";
    private ColorResolver.Source lastLoggedSource = null;
    private long lastLogAt;
    private View containerRef;
    private Runnable onChange;

    /** 上次找到的时钟 / 文字控件（避免每 2 秒重扫整棵树）。 */
    private java.lang.ref.WeakReference<View> clockRef;
    private java.lang.ref.WeakReference<TextView> textRef;

    /** 兜底轮询是否在跑。 */
    private boolean polling;
    /** 是否有一次「待合流」的采样已经排上队。 */
    private boolean pendingSample;

    int color() {
        return cachedColor;
    }

    String lastDiagnostic() {
        return lastDiagnostic;
    }

    /** 采样一次；返回颜色是否发生了变化。 */
    boolean sample(View statusContainer, View contentRoot) {
        containerRef = statusContainer;
        int tint = TintState.tint();
        int clockColor = readClockColor(contentRoot);
        int moduleColor = readAnyModuleTextColor(contentRoot);
        ColorResolver.Result r = ColorResolver.resolve(
                tint, clockColor, readLightStatusBars(statusContainer), moduleColor);
        boolean changed = r.color != cachedColor;
        cachedColor = r.color;
        // 诊断只在「来源变了」且间隔够久时打一条：系统切换图标色是带动画的，
        // 颜色值会连续变化几十次，逐条打日志等于把 logd 写满。
        long now = android.os.SystemClock.elapsedRealtime();
        if (r.source != lastLoggedSource || now - lastLogAt > DIAG_LOG_MS) {
            lastLoggedSource = r.source;
            lastLogAt = now;
            if (!r.diagnostic().equals(lastDiagnostic)) {
                lastDiagnostic = r.diagnostic();
                Log.i(lastDiagnostic);
            }
        } else {
            lastDiagnostic = r.diagnostic();
        }
        return changed;
    }

    /**
     * 系统状态栏外观变了（由 {@link TintState} 推送）。
     *
     * <p><b>必须合流</b>：系统的图标色是带动画的，{@code applyIconTint} 在动画
     * 期间会<b>每帧</b>调一次（实测一次切换能连发几十条），如果每次推送都立刻
     * 重采样，就等于把原来的 300ms 轮询换成了 60 次/秒的轮询 —— 比原来更费电。
     * 所以这里只置一个「待采样」标记，250ms 之后统一取一次最终颜色。
     * 动画结束后的那次采样拿到的就是稳定值。
     */
    void onSystemTintChanged() {
        if (pendingSample) {
            return;
        }
        View container = containerRef;
        if (container == null || !container.isAttachedToWindow()) {
            return;
        }
        pendingSample = true;
        handler.postDelayed(sampleRunnable, COALESCE_MS);
    }

    /** 合流后的实际采样动作。 */
    private final Runnable sampleRunnable = new Runnable() {
        @Override
        public void run() {
            pendingSample = false;
            View container = containerRef;
            if (container == null || !container.isAttachedToWindow()) {
                return;
            }
            View root = ViewTree.findContentRoot(container);
            if (root == null) {
                return;
            }
            if (sample(container, root) && onChange != null) {
                onChange.run();
            }
        }
    };

    /**
     * 系统算好的「状态栏该用深字还是浅字」。
     *
     * <p>优先用 Hook 抄下来的结果（{@link TintState}），它由系统在自己的
     * {@code LightBarControllerImpl} 里算出，切换应用就会更新；
     * 拿不到才退回当前窗口的 {@code getSystemBarsAppearance()}。
     */
    private static Boolean readLightStatusBars(View statusContainer) {
        Boolean hooked = TintState.lightStatusBars();
        if (hooked != null) {
            return hooked;
        }
        try {
            if (statusContainer == null) {
                return null;
            }
            android.view.WindowInsetsController wic = statusContainer.getWindowInsetsController();
            if (wic == null) {
                return null;
            }
            int appearance = wic.getSystemBarsAppearance();
            if (appearance == 0) {
                // 0 既可能是「真正的 0」也可能是「还没算」，当作没拿到。
                return null;
            }
            return (appearance & TintState.APPEARANCE_LIGHT_STATUS_BARS) != 0;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * 原生时钟文字色（低频调用）。
     *
     * <p>结果缓存：{@code findClock()} 是整棵状态栏树的递归 DFS，
     * 而采样是周期性动作 —— 每次都扫全树纯属浪费。
     * 时钟 View 在 SystemUI 生命周期里基本不变，用弱引用缓存即可；
     * 真的被回收了（重新 inflate）下次自然会重新找。
     */
    private int readClockColor(View contentRoot) {
        try {
            View clock = clockRef == null ? null : clockRef.get();
            if (clock == null) {
                clock = ViewTree.findClock(contentRoot);
                if (clock != null) {
                    clockRef = new java.lang.ref.WeakReference<>(clock);
                }
            }
            if (clock instanceof TextView) {
                return ((TextView) clock).getCurrentTextColor();
            }
        } catch (Throwable ignored) {
            // 交给下一级。
        }
        return ColorResolver.ABSENT;
    }

    /**
     * 状态栏内任一可见文字控件的文字色。
     * 某些 ROM 把时钟换成其它控件，此时退到这一级仍然读的是真实在用颜色。
     */
    private int readAnyModuleTextColor(View contentRoot) {
        try {
            TextView hit = textRef == null ? null : textRef.get();
            if (hit == null || !hit.isAttachedToWindow()) {
                hit = ViewTree.findFirstText(contentRoot);
                if (hit != null) {
                    textRef = new java.lang.ref.WeakReference<>(hit);
                }
            }
            if (hit != null) {
                return hit.getCurrentTextColor();
            }
        } catch (Throwable ignored) {
            // 交给下一级。
        }
        return ColorResolver.ABSENT;
    }

    /**
     * 开始跟踪颜色变化。
     *
     * <p>正常路径：注册 {@link TintState} 推送，<b>不启动任何定时器</b>。
     * 只有推送源完全不可用（Hook 没装上、两个外观来源都是 null）时，
     * 才启动 2 秒一次的低频兜底。
     */
    void startTracking(Runnable changed) {
        onChange = changed;
        TintState.setListener(this::onSystemTintChanged);
        if (TintState.lightStatusBars() == null && TintState.tint() == 0) {
            startFallbackPolling();
        }
    }

    /** 停止跟踪：撤销推送与兜底轮询（暂停/停止时必须调用）。 */
    void stopTracking() {
        TintState.setListener(null);
        onChange = null;
        pendingSample = false;
        stopFallbackPolling();
    }

    private void startFallbackPolling() {
        if (polling) {
            return;
        }
        polling = true;
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!polling) {
                    return;
                }
                View container = containerRef;
                if (container == null) {
                    polling = false;
                    return;
                }
                // 兜底期间如果推送源终于有数据了，立刻停掉轮询。
                if (TintState.lightStatusBars() != null || TintState.tint() != 0) {
                    polling = false;
                    return;
                }
                if (sample(container, ViewTree.findContentRoot(container)) && onChange != null) {
                    onChange.run();
                }
                handler.postDelayed(this, SAMPLE_INTERVAL_MS);
            }
        }, SAMPLE_INTERVAL_MS);
    }

    private void stopFallbackPolling() {
        polling = false;
        handler.removeCallbacksAndMessages(null);
    }
}
