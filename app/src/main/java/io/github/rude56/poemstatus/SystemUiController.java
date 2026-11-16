package io.github.rude56.poemstatus;

import android.content.BroadcastReceiver;
import android.content.ComponentCallbacks;
import android.database.ContentObserver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.PowerManager;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import io.github.libxposed.api.XposedInterface;

/**
 * SystemUI 进程内的总控。
 *
 * <p>职责：
 * <ul>
 *   <li>Hook {@code StatusBarWindowView.onAttachedToWindow}，拿到状态栏内容 View；</li>
 *   <li>低频拉配置（首次 + 内容变化时才重新解析，失败保留上一份有效配置）；</li>
 *   <li>判定暂停条件（熄屏 / 横屏 + 横屏暂停 / 白名单命中 / 视图不可用 / 熔断）；</li>
 *   <li>驱动 {@link PoemEngine} 与 {@link PoemRuntime}。</li>
 * </ul>
 *
 * <p>省电：所有状态回调只置脏 + post 一次合并处理；爬行时只有按速度自适应的帧回调，
 * 时间阶段只有 1 个 postDelayed，关闭/暂停态没有任何定时器。
 */
final class SystemUiController {

    private static final long FALLBACK_POLL_MS = 2000L;
    /** 前台不是本 App 时的配置轮询间隔：没人看连接状态，没必要每 2 秒跨进程读一次。 */
    private static final long IDLE_POLL_MS = 30000L;
    private static final long STAT_LOG_INTERVAL_MS = 60000L;
    /**
     * 心跳间隔。
     *
     * <p>心跳原本是为了兜住「暂停条件变化没有事件通知」—— 最典型的是旋转：
     * 以前旋转没有任何回调，只能靠 3 秒一次的心跳去 {@code getRotation()} 才发觉得到。
     * 现在注册了 {@link ComponentCallbacks#onConfigurationChanged}，旋转会主动置脏，
     * 于是心跳可以放宽到 8 秒，只是作为「事件丢了」的最后一道保险。
     */
    private static final long HEARTBEAT_MS = 8000L;
    /**
     * 已关闭模块时的心跳间隔。
     *
     * <p>关着的时候唯一可能变的就是「用户把开关打开了」，而那一定会触发
     * ContentObserver；心跳只是丢了事件时的保险，没必要 8 秒一次。
     */
    private static final long IDLE_HEARTBEAT_MS = 30000L;

    private static SystemUiController instance;

    /** 框架入口；所有 Hook 都通过它注册。 */
    private final XposedInterface xposed;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final PoemEngine engine;
    private final PoemRuntime runtime = new PoemRuntime();
    private final ColorSampler colorSampler = new ColorSampler();
    private final FeatureGuard guard = new FeatureGuard();
    private WeakReference<View> statusRef = new WeakReference<>(null);
    private Context context;
    private ForegroundTracker foreground;
    private PoemConfig config = new PoemConfig();
    private List<PoemItem> enabledPoems = new ArrayList<>();
    private String lastConfigJson = "";
    private boolean configLoadedOnce;
    private long lastConfigAttempt;
    private boolean configFetchInFlight;
    private boolean screenInteractive = true;
    private boolean landscape;
    /** 锁屏判定结果缓存；只在屏幕/锁屏状态变化时刷新，不做轮询。 */
    private Boolean lockscreenState = null;
    private String foregroundPackage = "";
    /** 上一次真正重建洞配置时的 holesJson；用于跳过 tick 里的无谓重建。 */
    private String lastHolesJson = null;
    /** 上一次重建时的状态栏高度；布局变化（旋转）时要重建。 */
    private int lastHoleBarHeight = -1;
    private boolean dirty;
    private long lastStatLogAt;
    private int lastStatusBarWidth;
    private int lastStatusBarHeight;
    private String lastLoggedState = "";
    private String lastTickPauseReason = "";
    private ContentObserver configObserver;
    private boolean configObserverFailed = true;
    private boolean configDirty = true;

    private final Runnable tickRunnable = new Runnable() {
        @Override
        public void run() {
            dirty = false;
            tick();
        }
    };

    private final Runnable clockRunnable = new Runnable() {
        @Override
        public void run() {
            engine.onClockElapsed();
        }
    };

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String a = intent == null ? null : intent.getAction();
            if (Intent.ACTION_SCREEN_OFF.equals(a)) {
                screenInteractive = false;
            } else if (Intent.ACTION_SCREEN_ON.equals(a) || Intent.ACTION_USER_PRESENT.equals(a)) {
                screenInteractive = true;
            }
            // 亮屏/解锁/锁屏都要重算一次锁屏状态（这是事件驱动，不是轮询）。
            if (Intent.ACTION_SCREEN_OFF.equals(a) || Intent.ACTION_SCREEN_ON.equals(a)
                    || Intent.ACTION_USER_PRESENT.equals(a)) {
                lockscreenState = null;
                refreshLockscreenState();
            }
            // 熄屏要「撤掉所有定时器」，亮屏要「把它们装回来」。
            // screen-off 分支里 scheduleHeartbeat() 会直接返回，所以这里统一调一次即可。
            scheduleHeartbeat();
            // 熄屏期间降级的前台轮询也要立刻恢复/停止。
            syncForegroundPolling();
            markDirty();
        }
    };

    /**
     * 旋转等配置变化：立刻置脏，代替「靠心跳定时 query 一次 rotation」。
     * 关掉「横屏暂停」时尤其重要 —— 旋转后必须重新绑定内容根原点。
     */
    private final ComponentCallbacks configCallbacks = new ComponentCallbacks() {
        @Override
        public void onConfigurationChanged(Configuration newConfig) {
            markDirty();
        }

        @Override
        public void onLowMemory() {
            // 不关心。
        }
    };

    private SystemUiController(XposedInterface xposed) {
        this.xposed = xposed;
        // 熔断必须能被看到：没有这条日志时，「遮挡再也不生效」是完全静默的。
        guard.setReporter(Log::w);
        engine = new PoemEngine(new EngineHost());
    }

    static void install(XposedInterface xposed, ClassLoader classLoader) {
        if (instance != null) {
            return;
        }
        instance = new SystemUiController(xposed);
        instance.hook(FeatureAudit.SCOPE_PACKAGE, classLoader);
    }

    private void hook(String packageName, ClassLoader cl) {
        try {
            Class<?> cls = Reflect.findClass(
                    "com.android.systemui.statusbar.window.StatusBarWindowView", cl);
            if (cls == null) {
                Log.w("POEM hook: StatusBarWindowView not found (cl="
                        + (cl == null ? "null" : "ok") + ")");
                return;
            }
            Log.i("POEM hook class=" + cls.getName() + " cl=" + cls.getClassLoader());
            java.lang.reflect.Method onAttached = Reflect.findMethod(cls, "onAttachedToWindow");
            if (onAttached == null) {
                Log.w("POEM hook: onAttachedToWindow not found");
                return;
            }
            xposed.hook(onAttached).intercept(chain -> {
                Object result = chain.proceed();
                try {
                    onStatusBarAttached((View) chain.getThisObject());
                } catch (Throwable t) {
                    Log.w("POEM onAttached hook: " + t.getClass().getSimpleName());
                }
                return result;
            });
            Log.i("POEM hook installed scope=" + packageName);
        } catch (Throwable t) {
            Log.w("POEM hook failed: " + t.getClass().getSimpleName());
        }
        hookStatusBarTint(cl);
    }

    /**
     * 抓状态栏外观位（诗句颜色的唯一来源）。
     *
     * <p>Hook {@code LightBarControllerImpl.onStatusBarAppearanceChanged}：
     * 系统把「当前应用背景该配浅字还是深字」算好后一定会走这里，
     * 我们从 {@code AppearanceRegion.getAppearance()} 把结果抄下来。
     * 这样切换深浅色应用时诗句跟着变，<b>不依赖任何亮度推断</b>，
     * 也不需要定时轮询 —— 系统变了会推给 {@link TintState}。
     *
     * <p>同时在 {@code applyIconTint} 后兜一份 tint：某些 ROM 上它是有效的。
     */
    private void hookStatusBarTint(ClassLoader cl) {
        hookAppearanceRegions(cl);
        hookContentRootDraw(cl);
        try {
            Class<?> cls = Reflect.findClass(
                    "com.android.systemui.statusbar.phone.DarkIconDispatcherImpl", cl);
            if (cls == null) {
                Log.w("POEM tint hook: DarkIconDispatcherImpl not found");
                return;
            }
            java.lang.reflect.Method applyIconTint = Reflect.findMethod(cls, "applyIconTint");
            if (applyIconTint == null) {
                Log.w("POEM tint hook: applyIconTint not found");
                return;
            }
            xposed.hook(applyIconTint).intercept(chain -> {
                Object result = chain.proceed();
                try {
                    Object v = Reflect.getField(chain.getThisObject(), "mIconTint");
                    if (v instanceof Integer) {
                        TintState.set((Integer) v);
                    }
                } catch (Throwable ignored) {
                    // 拿不到就用外观位。
                }
                return result;
            });
            Log.i("POEM tint hook=on");
        } catch (Throwable t) {
            Log.w("POEM tint hook failed: " + t.getClass().getSimpleName());
        }
    }

    /**
     * Hook {@code View.draw(Canvas)}，在<b>内容根</b>上挖洞。
     *
     * <p>之前的做法是逐个 View 调 alpha，但本机状态栏是 Compose 绘制的：
     * WiFi/电量/网速 画在同一块画布上，改单个 View 的 alpha 要么无效、
     * 要么被系统改回去（表现为「闪一下就回来」），时分秒更是无法单独隐藏。
     *
     * <p>现在只在内容根上挖一个洞：clip 是画布状态，会随 save/restore 传给所有子孙，
     * 所以时钟、图标、通知点无论原生还是 Compose 画的，<b>一次裁剪全部穿透</b>。
     *
     * <p>注意：{@code Clock} 自己并没有重写 {@code onDraw}（实测 NoSuchMethodError），
     * {@code TextView.draw} 也解析不到精确签名，{@code View} 才是 {@code draw} 的定义处，
     * 因此这里遍历 {@code View} 的 {@code draw} 重载来挂。
     */
    private void hookContentRootDraw(ClassLoader cl) {
        try {
            java.lang.reflect.Method target = null;
            for (java.lang.reflect.Method m : View.class.getDeclaredMethods()) {
                if (!"draw".equals(m.getName())) {
                    continue;
                }
                Class<?>[] ps = m.getParameterTypes();
                if (ps.length == 1 && ps[0] == android.graphics.Canvas.class) {
                    target = m;
                    break;
                }
            }
            if (target == null) {
                HolePuncher.installed = false;
                Log.w("POEM hole hook: View.draw(Canvas) not found");
                return;
            }
            final java.lang.reflect.Method drawMethod = target;
            xposed.hook(drawMethod).intercept(chain -> {
                Object self = chain.getThisObject();
                if (!HolePuncher.isRoot((View) self)) {
                    return chain.proceed();
                }
                android.graphics.Canvas canvas = (android.graphics.Canvas) chain.getArg(0);
                // 必须记住 beginClip 的返回值：只有真的挖了洞才允许 endClip。
                // 无条件调 endClip 会弹掉别人压进去的那一层 —— 画布状态一旦错配，
                // 裁剪就会永久留在内容根上（图标缺一块，重启才恢复）。
                boolean clipped = false;
                try {
                    clipped = HolePuncher.beginClip(canvas, (View) self);
                } catch (Throwable ignored) {
                    // 挖洞失败就正常画，不影响状态栏本身。
                }
                try {
                    return chain.proceed();
                } finally {
                    if (clipped) {
                        try {
                            HolePuncher.endClip(canvas);
                        } catch (Throwable ignored) {
                            // 同上。
                        }
                    }
                }
            });
            HolePuncher.installed = true;
            Log.i("POEM hole hook=on via View.draw");
        } catch (Throwable t) {
            HolePuncher.installed = false;
            Log.w("POEM hole hook failed: " + t.getClass().getSimpleName());
        }
    }

    /**
     * 直接抄系统算好的状态栏外观。
     *
     * <p>{@code onStatusBarAppearanceChanged(AppearanceRegion[], ...)} 的每个区域都带
     * {@code appearance} 位，{@code APPEARANCE_LIGHT_STATUS_BARS} 置位表示「浅色背景 + 深色字」。
     * 只读参数、不改系统行为、不增加任何定时器。
     */
    private void hookAppearanceRegions(ClassLoader cl) {
        try {
            Class<?> cls = Reflect.findClass(
                    "com.android.systemui.statusbar.phone.LightBarControllerImpl", cl);
            if (cls == null) {
                Log.w("POEM appearance hook: LightBarControllerImpl not found");
                return;
            }
            java.lang.reflect.Method m = Reflect.findMethod(cls, "onStatusBarAppearanceChanged",
                    "com.android.internal.view.AppearanceRegion[]",
                    boolean.class.getName(), boolean.class.getName(), boolean.class.getName());
            if (m == null) {
                // 兜底：不同 ROM 的签名略有差异（数组类型名、参数个数）。
                // 旧 API 的 findAndHookMethod 会替我们做这件事，现代 API 不再提供，
                // 所以这里自己退一步：按「名字 + 4 个参数且首参是数组」找。
                m = Reflect.findMethodLoose(cls, "onStatusBarAppearanceChanged", 4, true);
            }
            if (m == null) {
                Log.w("POEM appearance hook: onStatusBarAppearanceChanged not found");
                return;
            }
            xposed.hook(m).intercept(chain -> {
                Object result = chain.proceed();
                try {
                    Object[] regions = (Object[]) chain.getArg(0);
                    if (regions != null && regions.length > 0) {
                        int appearance = 0;
                        for (Object r : regions) {
                            if (r == null) {
                                continue;
                            }
                            Object v = Reflect.call(r, "getAppearance");
                            if (v instanceof Integer) {
                                appearance |= (Integer) v;
                            }
                        }
                        TintState.setAppearance(appearance);
                    }
                } catch (Throwable ignored) {
                    // 拿不到就继续用别的方式。
                }
                return result;
            });
            Log.i("POEM appearance hook=on");
        } catch (Throwable t) {
            Log.w("POEM appearance hook failed: " + t.getClass().getSimpleName());
        }
    }

    private void onStatusBarAttached(View window) {
        // 基准必须是状态栏窗口本身：它的高度就是状态栏条带高度（本机 112px），
        // 宽高在 attach 之后才有效，所以内容 View 只用于找层次，不用于取尺寸。
        statusRef = new WeakReference<>(window);
        runtime.setStatusContainer(window);
        Context ctx = window.getContext();
        boolean first = context == null;
        context = ctx;
        Log.i("POEM view=" + window.getClass().getSimpleName() + " "
                + window.getWidth() + "x" + window.getHeight());
        if (first) {
            registerScreenReceiver();
            registerForegroundTracker();
            registerConfigObserver();
            registerConfigCallbacks();
        }
        updateOrientation(window);
        refreshCutout(window);
        startHeartbeat();
        markDirty();
        handler.postDelayed(tickRunnable, 200L);
    }

    /** 旋转 / 分辨率变化：主动置脏，替代心跳里的周期查询。 */
    private void registerConfigCallbacks() {
        try {
            context.registerComponentCallbacks(configCallbacks);
        } catch (Throwable t) {
            Log.w("POEM config callbacks failed: " + t.getClass().getSimpleName());
        }
    }

    /** 前台包名跟踪：任务栈监听优先，失败才用低频兜底。 */
    private void registerForegroundTracker() {
        try {
            foreground = new ForegroundTracker(context, pkg -> {
                foregroundPackage = pkg;
                // 前台切换是「白名单是否暂停」的唯一依据：必须留一条日志，
                // 否则白名单失效时完全无从判断（是没解析出来，还是没命中）。
                if (Log.allow("foreground", 5000L)) {
                    Log.i("CONF foreground=" + pkg
                            + (config.isWhitelisted(pkg) ? " (whitelisted -> pause)" : ""));
                }
                markDirty();
            });
            foregroundPackage = "";
            foreground.start(!config.whitelist.isEmpty());
        } catch (Throwable t) {
            Log.w("POEM foreground tracker failed: " + t.getClass().getSimpleName());
        }
    }

    /**
     * 让前台轮询跟上「白名单是否非空」与「屏幕是否亮着」。
     *
     * <p>熄屏后前台是谁毫无意义，但旧实现只按「白名单非空」决定轮询，
     * 于是黑屏时仍每 5 秒反射查一次前台应用 —— 一晚上几千次无谓唤醒。
     */
    private void syncForegroundPolling() {
        ForegroundTracker fg = foreground;
        if (fg != null) {
            fg.syncPolling(screenInteractive);
        }
    }

    private void registerScreenReceiver() {
        try {
            IntentFilter f = new IntentFilter();
            f.addAction(Intent.ACTION_SCREEN_OFF);
            f.addAction(Intent.ACTION_SCREEN_ON);
            f.addAction(Intent.ACTION_USER_PRESENT);
            context.registerReceiver(screenReceiver, f, Context.RECEIVER_NOT_EXPORTED);
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            screenInteractive = pm == null || pm.isInteractive();
            refreshLockscreenState();
            registerKeyguardCallback();
        } catch (Throwable t) {
            Log.w("POEM screen receiver failed: " + t.getClass().getSimpleName());
        }
    }

    /**
     * 锁屏状态变化回调。
     *
     * <p>只注册广播是不够的：解锁（KEYCODE_MENU / 上滑）不一定伴随
     * ACTION_USER_PRESENT，模块会一直停在 paused。这里挂 KeyguardManager 的
     * 状态回调，锁屏一出现/消失就重算一次，仍然是事件驱动、零轮询。
     */
    private void registerKeyguardCallback() {
        try {
            android.app.KeyguardManager km =
                    (android.app.KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
            if (km == null) {
                return;
            }
            km.addKeyguardLockedStateListener(context.getMainExecutor(), locked -> {
                lockscreenState = locked;
                Log.i("POEM keyguard resolve locked=" + locked);
                markDirty();
            });
            Log.i("POEM keyguard listener=on");
        } catch (Throwable t) {
            // 老系统没有 addKeyguardLockedStateListener：退回广播 + tick 时重算。
            Log.w("POEM keyguard listener failed: " + t.getClass().getSimpleName());
        }
    }

    /**
     * 合并处理触发：把 tick 排进主线程消息队列（同一时刻最多一个）。
     *
     * <p>同时挂一个低频「心跳」：只有模块开启或还没成功拉到配置时才会续期，
     * 用来兜住「ContentObserver / 配置变化回调都没送到」的极端情况。
     * 心跳间隔 {@link #HEARTBEAT_MS}，且拉配置本身还有分档节流，
     * 所以实际 IPC 频率远低于心跳频率；模块关闭时心跳自动停止、零唤醒。
     */
    private void markDirty() {
        if (dirty) {
            return;
        }
        dirty = true;
        handler.removeCallbacks(tickRunnable);
        handler.post(tickRunnable);
    }

    private final Runnable heartbeatRunnable = new Runnable() {
        @Override
        public void run() {
            markDirty();
            scheduleHeartbeat();
        }
    };

    /**
     * 决定心跳是否续期，并算出这次该睡多久。
     *
     * <p><b>这里原来只有一个 8 秒的固定值，是全天候唤醒的来源</b>：
     * 熄屏后屏幕是黑的，诗句早该停下，但心跳仍然每 8 秒把 SystemUI 主线程
     * 叫起来一次 —— 一晚上 8 小时就是 3600 次无意义的唤醒，
     * 而每次唤醒都会顺带跑一遍 tick（读配置判定、方向检测、可见性检测）。
     *
     * <p>现在分三档：
     * <ul>
     *   <li>熄屏 → <b>完全不续期</b>。亮屏有广播，任务栈变化有监听，
     *       配置变化有 ContentObserver，不存在「只能靠心跳才发现」的事件；</li>
     *   <li>模块关闭 → 30 秒。这时唯一还可能变的就是「用户打开了开关」，
     *       而那个动作会触发 ContentObserver，心跳只是最后一道保险；</li>
     *   <li>正常播放 → 8 秒（保持原值，用来兜住丢了的事件）。</li>
     * </ul>
     */
    private void scheduleHeartbeat() {
        handler.removeCallbacks(heartbeatRunnable);
        if (!screenInteractive) {
            // 熄屏：零定时器。亮屏广播会重新把心跳拉起来（见 screenReceiver）。
            return;
        }
        final long interval;
        if (config.enabled || !configLoadedOnce) {
            interval = HEARTBEAT_MS;
        } else {
            interval = IDLE_HEARTBEAT_MS;
        }
        handler.postDelayed(heartbeatRunnable, interval);
    }

    private void startHeartbeat() {
        handler.removeCallbacks(heartbeatRunnable);
        handler.postDelayed(heartbeatRunnable, HEARTBEAT_MS);
    }

    /**
     * 观测状态栏「是否真的可见」，只在变化时打一条日志。
     *
     * <p><b>为什么不直接拿它当暂停条件</b>：全屏沉浸式应用（视频、游戏、全屏阅读）
     * 里状态栏被隐藏，此时诗句仍在以 30fps 滚动、仍在 {@code clipOutRect} 挖洞 ——
     * 这是目前最大的省电缺口，本该直接暂停。
     *
     * <p>但 {@code isShown()} 在「下拉通知面板」时也会翻成 false（面板盖住了状态栏），
     * 那时诗句其实还该继续。两种情形单看这一个布尔值分不开，
     * 而区分它们需要 {@code StatusBarState} 之类的系统内部状态 —— 风险明显更高。
     *
     * <p>所以先只采集：等实测确认「全屏应用」与「拉面板」下这几个值的组合确实不同，
     * 再决定用哪一组当暂停条件。在此之前绝不能让一个没验证过的条件去控制播放。
     */
    private void noteStatusBarVisibility(View container) {
        boolean shown = container.isShown();
        int windowVis = container.getWindowVisibility();
        int vis = container.getVisibility();
        if (shown == lastBarShown && windowVis == lastBarWindowVisibility && vis == lastBarVisibility) {
            return;
        }
        lastBarShown = shown;
        lastBarWindowVisibility = windowVis;
        lastBarVisibility = vis;
        Log.i("CONF bar visible shown=" + shown
                + " windowVis=" + windowVis + " vis=" + vis);
    }

    private boolean lastBarShown = true;
    private int lastBarWindowVisibility = -1;
    private int lastBarVisibility = -1;

    /** 一次合并处理：读配置 → 判定暂停 → 驱动状态机。 */
    private void tick() {
        View container = statusRef.get();
        if (container == null || !container.isAttachedToWindow()) {
            engine.pause("view-unavailable");
            return;
        }
        // onAttachedToWindow 时视图还没 layout（宽高都是 0）：必须等布局完成再扫描，
        // 否则叶子模块全部被过滤掉，遮挡会完全失效。
        if (container.getWidth() <= 0 || container.getHeight() <= 0) {
            handler.removeCallbacks(tickRunnable);
            handler.postDelayed(tickRunnable, 120L);
            return;
        }
        runtime.setStatusContainer(container);
        maybeReloadConfig();
        updateOrientation(container);
        noteStatusBarVisibility(container);
        // 锁屏状态可能在这期间变过（配置切换 / 任务切换），tick 时顺手重算一次。
        if (lockscreenState == null) {
            refreshLockscreenState();
        }
        engine.configure(config, enabledPoems);
        maybeLogStats();
        refreshCutout(container);

        String pauseReason = pauseReason();
        if (pauseReason != null) {
            // 只在「暂停原因变化」时打日志：暂停态心跳仍会跑，
            // 每次打一条会让 logd 常驻写入，白白耗电。
            if (!pauseReason.equals(lastTickPauseReason)) {
                lastTickPauseReason = pauseReason;
                Log.d("POEM tick pause=" + pauseReason + " enabled=" + config.enabled
                        + " screen=" + screenInteractive + " locked=" + lockscreenState
                        + " landscape=" + landscape
                        + " fg=" + foregroundPackage);
            }
            engine.tick(true, pauseReason);
        } else {
            lastTickPauseReason = "";
            engine.tick(false, "");
        }
    }

    private String pauseReason() {
        if (!config.enabled) {
            return "disabled";
        }
        if (!screenInteractive) {
            return "screen-off";
        }
        if (lockscreenPauseActive()) {
            return "lockscreen";
        }
        if (landscape && config.landscapePause) {
            return "landscape";
        }
        if (config.isWhitelisted(foregroundPackage)) {
            return "whitelist:" + foregroundPackage;
        }
        return null;
    }

    /**
     * 锁屏判定。优先用 SystemUI 自己的状态（类名反射，进程内、零 IPC），
     * 拿不到才退回 KeyguardManager.isKeyguardLocked()。
     *
     * <p>只在 tick 里调用（配置/广播/任务变化触发），不做轮询。
     */
    private boolean lockscreenPauseActive() {
        if (!config.lockscreenPause) {
            return false;
        }
        Boolean locked = lockscreenState;
        return locked != null && locked;
    }

    /** 屏幕亮/灭、锁屏状态变化时刷新一次锁屏判定。 */
    private void refreshLockscreenState() {
        if (!config.lockscreenPause) {
            lockscreenState = Boolean.FALSE;
            return;
        }
        Boolean locked = null;
        if (context != null) {
            try {
                android.app.KeyguardManager km =
                        (android.app.KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
                if (km != null) {
                    locked = km.isKeyguardLocked();
                    Log.i("POEM keyguard locked=" + locked
                            + " secure=" + km.isKeyguardSecure());
                }
            } catch (Throwable ignored) {
                locked = null;
            }
        }
        lockscreenState = locked == null ? Boolean.FALSE : locked;
    }

    /**
     * 方向与布局尺寸变化检测。
     *
     * <p>状态栏宽高变化（旋转、折叠屏切换、显示尺寸调整）以后会走到
     * {@link PoemRuntime#onLayoutChanged()}，重新绑定内容根的屏幕矩形 ——
     * 以前只在每首诗开始绑一次，关掉「横屏暂停」时旋转后的遮挡会整体偏移。
     */
    private void updateOrientation(View container) {
        try {
            int w = container.getWidth();
            int h = container.getHeight();
            boolean resized = false;
            if (w != lastStatusBarWidth || h != lastStatusBarHeight) {
                resized = lastStatusBarWidth > 0 && lastStatusBarHeight > 0;
                lastStatusBarWidth = w;
                lastStatusBarHeight = h;
            }
            Context ctx = container.getContext();
            WindowManager wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
            Display d = wm == null ? null : wm.getDefaultDisplay();
            boolean nowLandscape = false;
            if (d != null) {
                int rotation = d.getRotation();
                nowLandscape = rotation == android.view.Surface.ROTATION_90
                        || rotation == android.view.Surface.ROTATION_270;
            }
            boolean rotated = nowLandscape != landscape;
            if (rotated) {
                landscape = nowLandscape;
            }
            if (rotated || resized) {
                // 布局真的变了：重算内容根原点 + 诗句层尺寸，再重建洞配置。
                runtime.onLayoutChanged();
                lastHolesJson = null;
                refreshCutout(container);
            }
        } catch (Throwable ignored) {
            // 方向读取失败不影响播放。
        }
    }

    /**
     * 洞配置。
     *
     * <p>解析 {@code holesJson}，喂给诗句层与挖洞层。本方法会被心跳 tick 反复调用，
     * 所以必须去重 —— 否则每个心跳都会重新解析、给每个洞 new int[]、
     * 最后 invalidate 整个诗句层。
     */
    private void refreshCutout(View container) {
        final int barH = container.getHeight() > 0 ? container.getHeight() : 112;
        final String holesKey = config.holesJson == null ? "" : config.holesJson;

        if (holesKey.equals(lastHolesJson) && barH == lastHoleBarHeight) {
            return;
        }
        lastHolesJson = holesKey;
        lastHoleBarHeight = barH;

        List<HoleRects.Hole> holes = config.holes();
        runtime.setHoleConfig(holes);
        if (!holes.isEmpty()) {
            Log.i("POEM holes n=" + holes.size() + " list=" + HoleRects.encode(holes));
        }
    }

    /**
     * 配置读取。省电策略：
     * <ul>
     *   <li>正常路径：App 保存后 notifyChange → 这里的 ContentObserver 收到一次 → 立即读一次；</li>
     *   <li>观察者注册失败才退回 2 秒轮询，且只在「前台是本 App」时才这么勤；</li>
     *   <li>读取失败（App 未启动 / Provider 不存在）保留上一份有效配置，不崩溃。</li>
     * </ul>
     */
    private void maybeReloadConfig() {
        long now = System.currentTimeMillis();
        if (configFetchInFlight) {
            return;
        }
        // 观察者可用的正常路径：内容变化时 configDirty=true，立刻读一次。
        // 下面的轮询只是「观察者没送到」时的兜底，因此节奏必须分档 ——
        // 之前是无条件每 2 秒跑一次跨进程 Binder call，即便观察者明明在工作。
        // 这有两个真实代价：
        //   ① SystemUI 每 2 秒醒一次去读配置；
        //   ② 更糟的是，如果本 App 进程已经死了，这次 call 会<b>它整个拉起来</b>，
        //      等于每 2 秒唤醒一个本该休眠的应用进程。
        final long pollInterval;
        if (!screenInteractive) {
            // 熄屏：不轮询，只处理「确实有变更通知」的情况。
            //
            // 以前熄屏后仍按 30 秒 / 2 秒的节奏跨进程 call 一次 ——
            // 而这次 call 会<b>把本 App 进程整个拉起来</b>（如果它已经死了）。
            // 一晚上 8 小时 = 960 次跨进程唤醒，只为读一份不可能变的配置。
            // 熄屏期间用户不会改配置；就算改了，ContentObserver 也会送到。
            pollInterval = Long.MAX_VALUE;
        } else if (configObserverFailed || foregroundPackage.isEmpty()) {
            // 观察者注册失败，或者还判断不出前台是谁：保守地保持原来的节奏。
            pollInterval = FALLBACK_POLL_MS;
        } else if (FeatureAudit.SELF_PACKAGE.equals(foregroundPackage)) {
            // 用户正开着本 App —— 概览页的「连接自检」卡片要靠这个心跳刷新，
            // 慢了会显得迟钝，所以这里保持 2 秒。
            pollInterval = FALLBACK_POLL_MS;
        } else {
            // 前台明确是别的应用：没有人在看连接状态，降到 30 秒。
            pollInterval = IDLE_POLL_MS;
        }
        boolean needRefresh = configDirty
                || !configLoadedOnce
                || now - lastConfigAttempt >= pollInterval;
        if (!needRefresh) {
            return;
        }
        lastConfigAttempt = now;
        configDirty = false;
        configFetchInFlight = true;
        ConfigFetcher.fetchAsync(context, json -> {
            configFetchInFlight = false;
            if (json == null) {
                // 保留旧配置；下次仍会重试。
                if (!configLoadedOnce) {
                    configDirty = true;
                }
                return;
            }
            if (json.equals(lastConfigJson)) {
                return;
            }
            lastConfigJson = json;
            PoemConfig parsed = ConfigCodec.decode(json);
            config = parsed;
            enabledPoems = PoemLibrary.enabledOnly(PoemLibrary.load(parsed.poemsSeeded, parsed.poemsJson));
            configLoadedOnce = true;
            Log.i("CONF apply ver=" + parsed.configRevision
                    + " enabled=" + parsed.enabled
                    + " poems=" + enabledPoems.size()
                    + " speed=" + parsed.speedPx
                    + " clock=" + parsed.clockSeconds);
            if (foreground != null) {
                foreground.start(!parsed.whitelist.isEmpty());
                foreground.syncPolling(screenInteractive);
            }
            markDirty();
        });
    }

    /** 注册配置变更观察者（App notifyChange 后立即收到，零轮询）。 */
    private void registerConfigObserver() {
        try {
            android.net.Uri uri = android.net.Uri.parse("content://" + FeatureAudit.PROVIDER_AUTHORITY + "/config");
            configObserver = new ContentObserver(handler) {
                @Override
                public void onChange(boolean selfChange) {
                    configDirty = true;
                    markDirty();
                }
            };
            context.getContentResolver().registerContentObserver(uri, true, configObserver);
            configObserverFailed = false;
            Log.i("CONF observer=on");
        } catch (Throwable t) {
            configObserverFailed = true;
            Log.i("CONF push=off fallback=poll");
        }
    }

    private void maybeLogStats() {
        long now = System.currentTimeMillis();
        if (now - lastStatLogAt < STAT_LOG_INTERVAL_MS) {
            return;
        }
        lastStatLogAt = now;
        if (runtime.frameCount() > 0) {
            // 统计只在有帧时打（暂停/时间阶段完全不产生日志），
            // 且整行 < 80 字符 —— 这是唯一保留的周期性日志。
            Log.i("POEM stat frames=" + runtime.frameCount()
                    + " skip=" + runtime.skippedFrames()
                    + " avgMs=" + (runtime.avgFrameCostUs() / 1000f));
            runtime.resetStats();
        }
    }

    /** 供 Engine 使用的副作用出口。 */
    private final class EngineHost implements PoemEngine.Host {
        @Override
        public void startCrawl(PoemItem poem, String displayText, int speedPx) {
            View container = statusRef.get();
            if (container == null) {
                engine.pause("view-unavailable");
                return;
            }
            runtime.setStatusContainer(container);
            boolean ok = false;
            try {
                ok = runtime.start(displayText, speedPx, config.bobEnabled, runtimeListener);
            } catch (Throwable t) {
                Log.w("POEM start failed: " + t.getClass().getSimpleName());
            }
            if (!ok) {
                Log.w("POEM start aborted (overlay unavailable)");
                engine.pause("overlay");
                return;
            }
            // 取色：直接读原生状态栏正在用的文字色（不做任何反色推断）。
            View root = ViewTree.findContentRoot(container);
            colorSampler.sample(container, root);
            // 每首都要重新挂上推送：stopCrawl 里会 stopTracking()，
            // 而诗句层现在是复用的、不会重新挂载，onOverlayAttached 只发生一次 ——
            // 如果只在那里注册，第一首之后颜色就再也不跟随深浅色应用了。
            // startTracking 是幂等的（只是写一个字段 + 两次 volatile 读）。
            colorSampler.startTracking(tintApplier);
            runtime.setTextColor(colorSampler.color());
            // 每首一行：24 小时约 6600 首 → 6600 行，且带全文（几十字）。
            // 这是 logd 最大的单一来源，限频到 2 分钟一行；
            // 连「洞根 / 洞数」也并进来，避免同一时刻打两条。
            if (Log.allow("poem-start", 120_000L)) {
                Log.i("POEM start id=" + poem.id + " chars=" + poem.charCount
                        + " root=" + HolePuncher.rootName() + " holes=" + HolePuncher.holeCount()
                        + " text=" + displayText + " speed=" + speedPx
                        + " h=" + container.getHeight());
            } else if (Log.allow("poem-start-brief", 30_000L)) {
                Log.i("POEM start id=" + poem.id + " chars=" + poem.charCount
                        + " root=" + HolePuncher.rootName() + " holes=" + HolePuncher.holeCount()
                        + " speed=" + speedPx + " h=" + container.getHeight());
            }
        }

        @Override
        public void stopCrawl() {
            runtime.stop();
            colorSampler.stopTracking();
            handler.removeCallbacks(clockRunnable);
        }

        @Override
        public void restoreModules() {
            runtime.restoreModules();
        }

        @Override
        public void scheduleClock(int seconds) {
            handler.removeCallbacks(clockRunnable);
            // 每首都会走这里（含 clockSeconds=0 时立刻回调）——
            // 不限频的话光这一句就每天几千行。
            if (Log.allow("clock-hold", 120_000L)) {
                Log.i("POEM clock hold " + seconds + "s");
            }
            if (seconds <= 0) {
                handler.post(clockRunnable);
            } else {
                handler.postDelayed(clockRunnable, seconds * 1000L);
            }
        }

        @Override
        public void cancelClock() {
            handler.removeCallbacks(clockRunnable);
        }

        @Override
        public void setFrameCallbackActive(boolean active) {
            if (!active && Log.allow("paused", 120_000L)) {
                Log.i("POEM paused restore=ok state="
                        + engine.state().name().toLowerCase(java.util.Locale.ROOT));
            }
        }

        @Override
        public void onStateChanged(PoemEngine.State state) {
            String s = state.name();
            if (s.equals(lastLoggedState)) {
                return;
            }
            lastLoggedState = s;
            // IDLE / CRAWL / CLOCK 是每首都循环一遍的常态，逐次打日志等于
            // 每 13 秒 3 行、每天 2 万行。暂停才是需要立刻看见的异常状态，
            // 所以只有 PAUSED 无条件打，其余限频。
            if (state == PoemEngine.State.PAUSED || Log.allow("state", 300_000L)) {
                Log.i("POEM state=" + s.toLowerCase(java.util.Locale.ROOT));
            }
        }
    }

    /**
     * 每帧遮挡回调的复用入口。
     *
     * <p>之前写的是 {@code guard.guard("occlude", () -> runtime.updateOcclusion(...))} ——
     * 那个 lambda 每次调用都会捕获 4 个 float 并<b>分配一个新对象</b>，
     * 30 次/秒 × 常年累月是一笔纯浪费（而且帧内零分配的约束本来就应该遵守）。
     * 现在把坐标写进字段、Body 只创建一次。
     */
    private float frameL, frameT, frameR, frameB;

    private final FeatureGuard.Body occludeBody = () -> runtime.updateOcclusion(
            frameL, frameT, frameR, frameB);

    /**
     * 播放监听器：<b>只创建一次</b>。
     *
     * <p>以前每首诗都 {@code new RuntimeListener()}。虽然频率很低（一首次），
     * 但它挂在诗句层上、每帧被回调，完全没有必要反复分配。
     */
    private final RuntimeListener runtimeListener = new RuntimeListener();

    /**
     * 取色推送的落点：<b>只创建一次</b>。
     *
     * <p>以前这里是 {@code () -> runtime.setTextColor(...)} 内联 lambda。
     * 它是「不捕获外部变量」的 lambda，JVM 会缓存在调用点，
     * 但 App 侧与 SystemUI 侧的 lambda 缓存行为在不同 ART 版本上并不一致；
     * 而这个对象每首都会传给 {@code startTracking}，写成字段是零成本的确定性做法。
     */
    private final Runnable tintApplier = () -> runtime.setTextColor(colorSampler.color());

    private final class RuntimeListener implements PoemRuntime.Listener {        @Override
        public void onFrame(float left, float top, float right, float bottom) {
            frameL = left;
            frameT = top;
            frameR = right;
            frameB = bottom;
            guard.guardFast("occlude", occludeBody);
        }

        @Override
        public void onFinished() {
            engine.onCrawlFinished();
        }

        @Override
        public void onOverlayAttached() {
            // 推送式取色：系统改变状态栏外观时会回调，不需要任何定时器。
            // 挡位只有一个 —— 更新画笔颜色，绝不做遮挡计算。
            colorSampler.startTracking(tintApplier);
        }

        @Override
        public void onOverlayDetached() {
            colorSampler.stopTracking();
        }
    }
}
