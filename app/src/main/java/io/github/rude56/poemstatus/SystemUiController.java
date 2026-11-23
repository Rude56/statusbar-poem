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
import android.view.ViewGroup;
import android.view.WindowManager;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import io.github.libxposed.api.XposedInterface;

final class SystemUiController {
    private static final long FALLBACK_POLL_MS = 2000L;
    private static final long IDLE_POLL_MS = 30000L;
    private static final long STAT_LOG_INTERVAL_MS = 60000L;
    private static final long HEARTBEAT_MS = 8000L;
    private static final long IDLE_HEARTBEAT_MS = 30000L;
    private static final long CONFIG_RETRY_BASE_MS = 8000L;
    private static final long CONFIG_RETRY_MAX_MS = 60000L;

    private static SystemUiController instance;

    private final XposedInterface xposed;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final PoemEngine engine;
    private final PoemRuntime runtime = new PoemRuntime();
    private final ColorSampler colorSampler = new ColorSampler();
    private final FeatureGuard guard = new FeatureGuard();
    private WeakReference<View> statusRef = new WeakReference<>(null);
    private WeakReference<View> lockRef = new WeakReference<>(null);
    private Context context;
    private ForegroundTracker foreground;
    private PoemConfig config = new PoemConfig();
    private List<PoemItem> enabledPoems = new ArrayList<>();
    private String lastConfigJson = "";
    private boolean configLoadedOnce;
    private long lastConfigAttempt;
    private boolean configFetchInFlight;
    private int configFetchFailures;
    private boolean screenInteractive = true;
    private boolean landscape;
    private Boolean lockscreenState = null;
    private String foregroundPackage = "";
    private String lastHolesJson = null;
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

            if (Intent.ACTION_SCREEN_OFF.equals(a) || Intent.ACTION_SCREEN_ON.equals(a)
                    || Intent.ACTION_USER_PRESENT.equals(a)) {
                lockscreenState = null;
                refreshLockscreenState();
            }

            scheduleHeartbeat();

            syncForegroundPolling();
            markDirty();
        }
    };

    private final ComponentCallbacks configCallbacks = new ComponentCallbacks() {
        @Override
        public void onConfigurationChanged(Configuration newConfig) {
            markDirty();
        }

        @Override
        public void onLowMemory() {

        }
    };

    private SystemUiController(XposedInterface xposed) {
        this.xposed = xposed;

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
            Class<?> cls = findStatusBarWindowClass(cl);
            if (cls == null) {
                Log.w("POEM hook: 状态栏窗口类未找到 (cl="
                        + (cl == null ? "null" : "ok") + ")");
                hookLockWindow(cl);
                hookStatusBarTint(cl);
                return;
            }
            Log.diag("POEM hook class=" + cls.getName() + " cl=" + cls.getClassLoader());
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
            Log.diag("POEM hook installed scope=" + packageName);
        } catch (Throwable t) {
            Log.w("POEM hook failed: " + t.getClass().getSimpleName());
        }
        hookLockWindow(cl);
        hookStatusBarTint(cl);
    }

    /**
     * 找状态栏窗口类。
     *
     * <p>AOSP 13+ 是 {@code statusbar.window.StatusBarWindowView}，
     * 更早的版本在 {@code statusbar.phone.StatusBarWindowView}，
     * 部分 ROM 会改名或挪包。逐个候选尝试，都不中再按类名片段在已加载类里兜底。
     */
    private Class<?> findStatusBarWindowClass(ClassLoader cl) {
        for (String name : new String[]{
                "com.android.systemui.statusbar.window.StatusBarWindowView",
                "com.android.systemui.statusbar.phone.StatusBarWindowView",
                "com.android.systemui.statusbar.StatusBarWindowView"}) {
            Class<?> cls = Reflect.findClass(name, cl);
            if (cls != null) {
                return cls;
            }
        }
        return null;
    }

    private void hookLockWindow(ClassLoader cl) {
        for (final String name : new String[]{
                "com.android.systemui.shade.NotificationShadeWindowView",
                "com.android.systemui.statusbar.phone.NotificationShadeWindowView",
                "com.android.keyguard.KeyguardRootView"}) {
            try {
                Class<?> cls = Reflect.findClass(name, cl);
                if (cls == null) {
                    continue;
                }
                java.lang.reflect.Method m = Reflect.findMethod(cls, "onAttachedToWindow");
                if (m == null) {
                    continue;
                }
                xposed.hook(m).intercept(chain -> {
                    Object result = chain.proceed();
                    try {
                        View v = (View) chain.getThisObject();
                        lockRef = new WeakReference<>(v);
                        markDirty();
                    } catch (Throwable ignored) {

                    }
                    return result;
                });
                Log.diag("POEM lock hook=" + name);
                return;
            } catch (Throwable t) {
                Log.w("POEM lock hook failed: " + t.getClass().getSimpleName());
            }
        }
    }

    private View activeHost() {
        View lock = lockedHost();
        if (lock != null) {
            return lock;
        }
        return statusRef.get();
    }

    /** 锁屏且未开启锁屏暂停时返回锁屏挂载容器，否则 null。 */
    private View lockedHost() {
        Boolean locked = lockscreenState;
        if (locked == null || !locked || config.lockscreenPause) {
            lockRef = new WeakReference<>(null);
            return null;
        }
        View lock = lockRef.get();
        if (lock == null || !lock.isShown() || !lock.isAttachedToWindow()) {
            lock = findLockMountHost();
            lockRef = new WeakReference<>(lock);
        }
        return lock != null && lock.isAttachedToWindow() ? lock : null;
    }

    /**
     * 锁屏挖洞根：整条锁屏顶栏（1440x112），不是里面某个子容器。
     *
     * <p>实测结构（MEIZU Android 17）：
     * <pre>
     * SceneWindowRootView (1440x3200)          ← 诗句层挂在这里（第 9 个子 View）
     *   └─ ComposeView -> AndroidComposeView -> AndroidViewsHandler
     *         └─ ViewFactoryHolder (1440x112)
     *               └─ KeyguardStatusBarView (1440x112)   ← 锁屏图标本体
     *                     └─ status_bar_container (约397px 宽)  ← 只包右侧图标
     * </pre>
     *
     * <p>挖 status_bar_container 会漏掉左侧运营商文字（实测就是这么回事），
     * 所以锁屏必须挖整条 KeyguardStatusBarView。
     *
     * <p>关键约束：挖洞根必须是诗句层的<b>兄弟子树</b>，不能是它的祖先。
     * 若用 SceneWindowRootView 本身，clipOutRect 会把诗句一起裁掉，
     * 只能靠「抑制自己 + 根上补画」，同一帧画两遍 → 残影与卡顿。
     */
    private View lockOccludeRoot() {
        View lock = lockedHost();
        if (lock == null) {
            return null;
        }
        View statusBar = findKeyguardStatusBar(lock);
        if (statusBar == null) {
            Log.diag("POEM lockRoot=none (KeyguardStatusBarView 未找到)");
            return null;
        }
        if (HolePuncher.isAncestorOfOverlay(statusBar, overlayRef())) {
            Log.w("POEM lockRoot rejected: 挖洞根是诗句层祖先");
            return null;
        }
        return statusBar;
    }

    /**
     * 锁屏顶栏图标所在的整条 View。
     *
     * <p>类名在不同 ROM 上不一样，按「可靠度递减」多路尝试：
     * <ol>
     *   <li>KeyguardStatusBarView（AOSP 13+ 与多数 ROM）</li>
     *   <li>类名里含 KeyguardStatusBar / KeyguardStatus 的任意 View</li>
     *   <li>ViewFactoryHolder（Compose 化的锁屏顶栏，部分 ROM 把图标直接放这里）</li>
     *   <li>资源 id status_bar_container / keyguard_status_bar，且尺寸是整条</li>
     *   <li>按尺寸在锁屏根里找「整条、顶部对齐」的候选</li>
     * </ol>
     * 全部要求 attached 且尺寸合理，避免拿到已销毁的旧实例。
     */
    private View findKeyguardStatusBar(View root) {
        // 1. 精确类名
        View v = ViewTree.findBySimpleName(root, "KeyguardStatusBarView", 0);
        if (v == null) {
            v = ViewTree.findBySimpleNameAny("KeyguardStatusBarView");
        }
        if (v != null && v.isAttachedToWindow() && ViewTree.isFullBar(v)) {
            return v;
        }
        View exact = v;
        // 2. 类名包含 KeyguardStatusBar（不同 ROM 可能加前缀/后缀）
        View loose = ViewTree.findBySimpleNameContains(root, "KeyguardStatusBar");
        if (loose == null) {
            loose = ViewTree.findBySimpleNameContainsAny("KeyguardStatusBar");
        }
        if (loose != null && loose.isAttachedToWindow() && ViewTree.isFullBar(loose)) {
            return loose;
        }
        // 3. Compose 容器
        View holder = ViewTree.findBySimpleName(root, "ViewFactoryHolder", 0);
        if (holder != null && holder.isAttachedToWindow() && ViewTree.isFullBar(holder)) {
            return holder;
        }
        // 4. 资源 id（不同 ROM 命名不同，逐个试）
        for (String id : new String[]{"status_bar_container", "keyguard_status_bar",
                "keyguard_status_bar_container"}) {
            View byId = ViewTree.findByResourceId(root, id);
            if (byId != null && byId.isAttachedToWindow() && ViewTree.isFullBar(byId)) {
                return byId;
            }
        }
        // 5. 尺寸兜底：锁屏根里找顶部对齐、整条宽的矮 View
        View band = ViewTree.findTopBand(root);
        if (band != null && band.isAttachedToWindow()) {
            return band;
        }
        // 6. 精确类名但不满足整条尺寸（老 ROM），只要挂着就用
        if (exact != null && exact.isAttachedToWindow() && exact.getHeight() > 0) {
            return exact;
        }
        if (loose != null && loose.isAttachedToWindow() && loose.getHeight() > 0) {
            return loose;
        }
        return null;
    }

    private View overlayRef() {
        return runtime.overlayView();
    }


    private View findLockMountHost() {
        View keyguard = findKeyguardRoot();
        if (keyguard != null) {
            View parent = keyguard.getParent() instanceof View
                    ? (View) keyguard.getParent() : null;
            if (parent instanceof ViewGroup && parent.isShown()) {
                Log.diag("POEM lockMount=parent of Keyguard "
                        + parent.getClass().getSimpleName());
                return parent;
            }
            if (parent == null) {
                Log.diag("POEM lockMount=Keyguard itself");
                return keyguard;
            }
        }
        View shade = findShadeWindow();
        if (shade != null && shade.isShown()) {
            Log.diag("POEM lockMount=Shade shown");
            return shade;
        }
        if (shade != null) {
            View p = shade.getParent() instanceof View ? (View) shade.getParent() : null;
            if (p != null && p.isShown()) {
                Log.diag("POEM lockMount=parent " + p.getClass().getSimpleName());
                return p;
            }
        }
        return null;
    }

    /** 锁屏挖洞根：真正的 keyguard 内容容器。 */
    private View findKeyguardRoot() {
        View v = ViewTree.findBySimpleNameAny("KeyguardRootView");
        if (v != null && v.isAttachedToWindow()) {
            return v;
        }
        View lock = lockRef.get();
        if (lock instanceof ViewGroup) {
            View inner = ViewTree.findBySimpleName(lock, "KeyguardRootView", 0);
            if (inner != null && inner.isAttachedToWindow()) {
                return inner;
            }
        }
        View shade = findShadeWindow();
        if (shade instanceof ViewGroup) {
            View inner = ViewTree.findBySimpleName(shade, "KeyguardRootView", 0);
            if (inner != null && inner.isAttachedToWindow()) {
                return inner;
            }
        }
        return null;
    }

    private View findShadeWindow() {
        View hit = searchShadeFromAllRoots();
        if (hit != null) {
            return hit;
        }
        View status = statusRef.get();
        if (status == null) {
            return null;
        }
        for (String name : new String[]{
                "com.android.systemui.shade.NotificationShadeWindowView",
                "com.android.systemui.statusbar.phone.NotificationShadeWindowView",
                "com.android.keyguard.KeyguardRootView"}) {
            View v = ViewTree.findByClassName(status, name, 0);
            if (v != null) {
                return v;
            }
        }
        return null;
    }

    private View searchShadeFromAllRoots() {
        try {
            Class<?> cls = Reflect.findClass("android.view.WindowManagerGlobal", null);
            if (cls == null) {
                return null;
            }
            java.lang.reflect.Method getInstance = Reflect.findMethod(cls, "getInstance");
            if (getInstance == null) {
                return null;
            }
            Object global = getInstance.invoke(null);
            Object roots = Reflect.getField(global, "mViews");
            if (!(roots instanceof java.util.List)) {
                return null;
            }
            for (Object o : (java.util.List<?>) roots) {
                if (!(o instanceof View)) {
                    continue;
                }
                View rv = (View) o;
                String simple = rv.getClass().getSimpleName();
                if (simple.contains("NotificationShade") || simple.contains("KeyguardRoot")) {
                    return rv;
                }
                View inner = ViewTree.findByClassName(rv,
                        "com.android.systemui.shade.NotificationShadeWindowView", 0);
                if (inner == null) {
                    inner = ViewTree.findByClassName(rv,
                            "com.android.systemui.statusbar.phone.NotificationShadeWindowView", 0);
                }
                if (inner != null) {
                    return inner;
                }
            }
        } catch (Throwable ignored) {

        }
        return null;
    }

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

                }
                return result;
            });
            Log.diag("POEM tint hook=on");
        } catch (Throwable t) {
            Log.w("POEM tint hook failed: " + t.getClass().getSimpleName());
        }
    }

    private final java.util.Set<Class<?>> hookedClasses = new java.util.HashSet<>();

    private void hookContentRootDraw(ClassLoader cl) {
        HolePuncher.setRootHook(this::hookChainFor);
    }

    private void hookChainFor(View v) {
        Class<?> c = v.getClass();
        while (c != null && c != Object.class) {
            if (hasOwnDispatchDraw(c)) {
                if (isBaseFrameworkClass(c)) {
                    // 挖洞根本身是框架基类（例如裸 FrameLayout），自己没重写 dispatchDraw，
                    // 只能拦到 ViewGroup.dispatchDraw —— 那是全进程所有 ViewGroup 的基类入口。
                    //
                    // 这里**仍然挂**（不挂就完全挖不了洞），因为：
                    // 1. 拦截体第一件事就是 HolePuncher.isRootFast(self) 实例判断，无关实例立即放行；
                    // 2. 实测该机型就是这条路径，改动它等于让「解锁后遮住图标」直接失效。
                    //
                    // 告警只在「首次挂上该类」时打一次，否则每次 bindRoot 都会刷屏。
                    if (!hookedClasses.contains(c)) {
                        Log.w("POEM hole hook 打在全进程基类 " + c.getName()
                                + "（挖洞根=" + v.getClass().getSimpleName()
                                + "，换机型若卡顿先查此处）");
                    }
                }
                hookDrawClass(c);
                return;
            }
            c = c.getSuperclass();
        }
    }

    /** 框架基类：挂它们的 dispatchDraw 会影响全进程所有同类 View。 */
    private static boolean isBaseFrameworkClass(Class<?> c) {
        if (c == null) {
            return true;
        }
        String name = c.getName();
        if (name == null) {
            return true;
        }
        return "android.view.ViewGroup".equals(name)
                || "android.view.View".equals(name)
                || "android.widget.FrameLayout".equals(name)
                || "android.widget.LinearLayout".equals(name)
                || "android.widget.RelativeLayout".equals(name)
                || "android.widget.ScrollView".equals(name);
    }

    private static boolean hasOwnDispatchDraw(Class<?> c) {
        try {
            for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
                if (!"dispatchDraw".equals(m.getName())) {
                    continue;
                }
                Class<?>[] ps = m.getParameterTypes();
                if (ps.length == 1 && ps[0] == android.graphics.Canvas.class) {
                    return true;
                }
            }
        } catch (Throwable ignored) {

        }
        return false;
    }

    private void hookDrawClass(Class<?> owner) {
        if (!hookedClasses.add(owner)) {
            return;
        }
        java.lang.reflect.Method target = null;
        for (java.lang.reflect.Method m : owner.getDeclaredMethods()) {
            if (!"dispatchDraw".equals(m.getName())) {
                continue;
            }
            Class<?>[] ps = m.getParameterTypes();
            if (ps.length == 1 && ps[0] == android.graphics.Canvas.class) {
                target = m;
                break;
            }
        }
        if (target == null) {
            hookedClasses.remove(owner);
            return;
        }
        if (!hookDrawMethod(owner, target, "dispatchDraw")) {
            hookedClasses.remove(owner);
        }
    }

    /**
     * 给「绘制入口」挂 clipOutRect。
     *
     * <p>不论是 dispatchDraw 还是 draw，拦截方式一致：进入前按诗句位置挖洞，
     * 退出前恢复。只对 {@link HolePuncher#isRoot} 认定的那个实例生效，
     * 所以即使挂的是共用类，非挖洞根实例只是多一次引用比较。
     */
    private boolean hookDrawMethod(Class<?> owner, java.lang.reflect.Method method, String what) {
        try {
            xposed.hook(method).intercept(chain -> {
                Object self = chain.getThisObject();
                // 热路径：先用 isRootFast 做整数比较挡掉绝大多数实例。
                // 万一挖洞根是框架基类（见 hookChainFor 的告警），这里会被
                // 全进程每个该类型 View 每次绘制调用一次，必须尽量便宜。
                if (!(self instanceof View) || !HolePuncher.isRootFast((View) self)) {
                    return chain.proceed();
                }
                View rootView = (View) self;
                android.graphics.Canvas canvas = (android.graphics.Canvas) chain.getArg(0);

                boolean clipped = false;
                try {
                    clipped = HolePuncher.beginClip(canvas, rootView);
                } catch (Throwable ignored) {

                }
                try {
                    return chain.proceed();
                } finally {
                    if (clipped) {
                        try {
                            HolePuncher.endClip(canvas);
                        } catch (Throwable ignored) {

                        }
                    }
                }
            });
            HolePuncher.installed = true;
            Log.diag("POEM hole hook=" + what + " on " + owner.getName());
            return true;
        } catch (Throwable t) {
            Log.w("POEM hole hook " + what + " on " + owner.getSimpleName()
                    + " failed: " + t.getClass().getSimpleName());
            return false;
        }
    }

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

                }
                return result;
            });
            Log.diag("POEM appearance hook=on");
        } catch (Throwable t) {
            Log.w("POEM appearance hook failed: " + t.getClass().getSimpleName());
        }
    }

    private void onStatusBarAttached(View window) {

        statusRef = new WeakReference<>(window);
        Context ctx = window.getContext();
        boolean first = context == null;
        context = ctx;
        LogSink.init(ctx);
        FullscreenWatcher.init(ctx);
        Log.diag("POEM view=" + window.getClass().getSimpleName() + " "
                + window.getWidth() + "x" + window.getHeight());
        if (first) {
            registerScreenReceiver();
            registerForegroundTracker();
            registerConfigObserver();
            registerConfigCallbacks();
        }
        updateOrientation(window);
        refreshCutout(window);

        scheduleHeartbeat();
        markDirty();
        handler.postDelayed(tickRunnable, 200L);
    }
    private void registerConfigCallbacks() {
        try {
            context.registerComponentCallbacks(configCallbacks);
        } catch (Throwable t) {
            Log.w("POEM config callbacks failed: " + t.getClass().getSimpleName());
        }
    }

    private void registerForegroundTracker() {
        try {
            foreground = new ForegroundTracker(context, pkg -> {
                foregroundPackage = pkg;

                if (Log.allow("foreground", 5000L)) {
                    Log.diag("CONF foreground=" + pkg
                            + (config.isWhitelisted(pkg) ? " (whitelisted -> pause)" : ""));
                }
                markDirty();
            });
            foregroundPackage = "";
            foreground.updateConfig(!config.whitelist.isEmpty(), config.enabled);
        } catch (Throwable t) {
            Log.w("POEM foreground tracker failed: " + t.getClass().getSimpleName());
        }
    }

    private void syncForegroundPolling() {
        ForegroundTracker fg = foreground;
        if (fg != null) {
            fg.updateConfig(!config.whitelist.isEmpty(), config.enabled);
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

    private void registerKeyguardCallback() {
        try {
            android.app.KeyguardManager km =
                    (android.app.KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
            if (km == null) {
                return;
            }
            km.addKeyguardLockedStateListener(context.getMainExecutor(), locked -> {
                lockscreenState = locked;
                Log.diag("POEM keyguard resolve locked=" + locked);
                markDirty();
            });
            Log.diag("POEM keyguard listener=on");
        } catch (Throwable t) {

            Log.w("POEM keyguard listener failed: " + t.getClass().getSimpleName());
        }
    }

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

    private void scheduleHeartbeat() {
        handler.removeCallbacks(heartbeatRunnable);
        if (!screenInteractive) {
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
        Log.diag("CONF bar visible shown=" + shown
                + " windowVis=" + windowVis + " vis=" + vis);
    }

    private boolean lastBarShown = true;
    private int lastBarWindowVisibility = -1;
    private int lastBarVisibility = -1;
    private void resampleColor() {
        View status = statusRef.get();
        if (status == null || !status.isAttachedToWindow()) {
            return;
        }
        if (!runtime.isRunning()) {
            return;
        }
        View root = ViewTree.findContentRoot(status);
        if (root == null) {
            return;
        }
        if (colorSampler.sample(status, root)) {
            runtime.setTextColor(colorSampler.color());
        }
    }

    private void tick() {
        try {
            tickInner();
        } catch (Throwable t) {
            // 任何异常都不能让心跳停掉：心跳一停，状态栏就永远没诗了。
            Log.w("POEM tick threw: " + t.getClass().getSimpleName()
                    + " @ " + t.getStackTrace().length);
            try {
                if (t.getStackTrace().length > 0) {
                    StackTraceElement top = t.getStackTrace()[0];
                    Log.w("POEM tick at " + top.getClassName() + "." + top.getMethodName()
                            + ":" + top.getLineNumber());
                }
            } catch (Throwable ignored) {

            }
            markDirty();
        }
    }

    private void tickInner() {
        refreshScreenInteractive();
        refreshLockscreenState();
        View container = activeHost();
        if (container == null || !container.isAttachedToWindow()) {
            engine.pause("view-unavailable");
            return;
        }

        if (container.getWidth() <= 0 || container.getHeight() <= 0) {
            handler.removeCallbacks(tickRunnable);
            handler.postDelayed(tickRunnable, 120L);
            return;
        }
        boolean lockedNow = lockscreenState != null && lockscreenState;
        if (Log.allow("mode-diag", 8_000L)) {
            Log.diag("POEM mode locked=" + lockedNow
                    + " lp=" + config.lockscreenPause);
        }
        // 只置标志，不拆挂载（拆挂载 = 解锁一闪一闪）。
        runtime.setForceWindowMode(lockedNow && !config.lockscreenPause);
        // 诗句层挂在哪里只由「在锁屏 / 不在锁屏」决定，不跟 activeHost 走。
        // 否则锁屏 ↔ 解锁时容器来回换，每换一次就摘掉再加回来。
        View mountHost = lockedNow ? container : statusRef.get();
        if (mountHost == null) {
            mountHost = container;
        }
        if (runtime.setStatusContainer(mountHost)) {
            engine.pause("view-replaced");
            handler.post(tickRunnable);
            return;
        }
        View lockRoot = lockOccludeRoot();
        runtime.setOccludeHost(lockRoot != null ? lockRoot : statusRef.get());
        maybeReloadConfig();
        updateOrientation(container);
        noteStatusBarVisibility(container);
        engine.configure(config, enabledPoems);
        maybeLogStats();
        refreshCutout(container);
        resampleColor();
        String pauseReason = pauseReason();
        if (pauseReason != null) {
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
        if (Log.allow("tick-diag", 8_000L)) {
            View host = activeHost();
            View st = statusRef.get();
            String geo = "n/a";
            if (st != null) {
                int[] loc = new int[2];
                st.getLocationOnScreen(loc);
                android.graphics.Rect vis = new android.graphics.Rect();
                boolean hasVis = st.getGlobalVisibleRect(vis);
                geo = "y=" + loc[1]
                        + " ty=" + st.getTranslationY()
                        + " alpha=" + st.getAlpha()
                        + " winVis=" + st.getWindowVisibility()
                        + " vis=" + st.getVisibility()
                        + " gVis=" + hasVis + ":" + vis.height()
                        + " sway=" + statusBarInsetTop(st);
            }
            Log.diag("POEM diag screen=" + screenInteractive
                    + " locked=" + lockscreenState
                    + " host=" + (host == null ? "null" : host.getClass().getSimpleName())
                    + " hostShown=" + (host != null && host.isShown())
                    + " hostW=" + (host == null ? 0 : host.getWidth())
                    + " hostH=" + (host == null ? 0 : host.getHeight())
                    + " state=" + engine.state().name().toLowerCase(java.util.Locale.ROOT)
                    + " reason=" + (pauseReason == null ? "none" : pauseReason)
                    + " st[" + geo + "]");
            Log.diag("POEM probe " + FullscreenProbe.probe(context, st));
            Log.diag("POEM chain " + FullscreenProbe.chain(st));
        }
        // 全屏只是「状态栏看不见」，与锁屏是两回事：锁屏也算看不见，但诗句要显示。
        Boolean fullscreen = FullscreenWatcher.changed();
        if (fullscreen != null) {
            Log.i("POEM fullscreen " + (fullscreen ? "on" : "off")
                    + " (" + FullscreenWatcher.describe() + ")");
        }
    }

    private static int statusBarInsetTop(View v) {
        try {
            android.view.WindowInsets insets = v.getRootWindowInsets();
            if (insets == null) {
                return -1;
            }
            return insets.getInsets(android.view.WindowInsets.Type.statusBars()).top;
        } catch (Throwable ignored) {
            return -2;
        }
    }

    private String pauseReason() {
        if (!config.enabled) {
            return "disabled";
        }
        if (!screenInteractive) {
            return "screen-off";
        }
        boolean lockedNow = lockscreenState != null && lockscreenState;
        if (config.lockscreenPause && lockedNow) {
            return "lockscreen";
        }
        // 全屏 = 状态栏被系统藏起来 = 没有地方放诗句。
        //
        // 判据用 SystemUI 自己窗口的 getCurrentWindowMetrics().getWindowInsets()，
        // 实测三阶段全部跟随（桌面 true / 全屏 false / 下滑唤出立刻 true）。
        // 绝不能用 StatusBarWindowView.getRootWindowInsets() —— 它是 insets 提供方，
        // 读到的恒为 0 且一旦 false 就永不更新，会把诗句永久卡死。
        //
        // 锁屏不适用此判据，由上面的 lockscreenPause 单独控制。
        if (!lockedNow) {
            Boolean fullscreen = FullscreenWatcher.statusBarHidden();
            if (fullscreen != null && fullscreen) {
                return "fullscreen";
            }
        }
        if (landscape && config.landscapePause) {
            return "landscape";
        }
        if (config.isWhitelisted(foregroundPackage)) {
            return "whitelist:" + foregroundPackage;
        }
        return null;
    }
    private void refreshScreenInteractive() {
        if (context == null) {
            return;
        }
        boolean on = screenInteractive;
        try {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                on = pm.isInteractive();
                if (!on) {
                    android.hardware.display.DisplayManager dm =
                            (android.hardware.display.DisplayManager)
                                    context.getSystemService(Context.DISPLAY_SERVICE);
                    android.view.Display d = dm == null
                            ? null : dm.getDisplay(android.view.Display.DEFAULT_DISPLAY);
                    if (d != null && d.getState() != android.view.Display.STATE_OFF) {
                        on = true;
                    }
                }
            }
            View status = statusRef.get();
            if (!on && status != null && status.isAttachedToWindow()
                    && status.getWindowVisibility() == View.VISIBLE) {
                on = true;
            }
        } catch (Throwable ignored) {

        }
        if (on != screenInteractive) {
            screenInteractive = on;
        }
    }

    private void refreshLockscreenState() {
        Boolean locked = null;
        if (context != null) {
            try {
                android.app.KeyguardManager km =
                        (android.app.KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
                if (km != null) {
                    boolean uiLocked = km.isKeyguardLocked() || km.isDeviceLocked();
                    View status = statusRef.get();
                    boolean barHidden = status != null && !status.isShown();
                    locked = uiLocked || barHidden;
                    Log.diag("POEM keyguard locked=" + locked
                            + " ui=" + uiLocked
                            + " secure=" + km.isKeyguardSecure());
                }
            } catch (Throwable ignored) {
                locked = null;
            }
        }
        lockscreenState = locked == null ? Boolean.FALSE : locked;
    }

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
                runtime.onLayoutChanged();
                lastHolesJson = null;
                refreshCutout(container);
            }
        } catch (Throwable ignored) {

        }
    }

    private void refreshCutout(View container) {
        // 容器高度取实测值；取不到时用系统状态栏高度，再不行按密度估算，
        // 绝不写死某个机型的像素值（112 只对 1440x3200/640dpi 成立）。
        int barH = container.getHeight();
        if (barH <= 0) {
            barH = ViewTree.statusBarHeightPx(container);
        }
        if (barH <= 0) {
            barH = Math.round(24 * container.getResources().getDisplayMetrics().density);
        }
        final String holesKey = config.holesJson == null ? "" : config.holesJson;

        if (holesKey.equals(lastHolesJson) && barH == lastHoleBarHeight) {
            return;
        }
        lastHolesJson = holesKey;
        lastHoleBarHeight = barH;
        List<HoleRects.Hole> holes = config.holes();
        runtime.setHoleConfig(holes);
        if (!holes.isEmpty()) {
            Log.diag("POEM holes n=" + holes.size() + " list=" + HoleRects.encode(holes));
        }
    }

    private void maybeReloadConfig() {
        long now = System.currentTimeMillis();
        if (configFetchInFlight) {
            return;
        }

        final long pollInterval;
        if (!screenInteractive) {

            pollInterval = Long.MAX_VALUE;
        } else if (configObserverFailed || foregroundPackage.isEmpty()) {

            pollInterval = FALLBACK_POLL_MS;
        } else if (FeatureAudit.SELF_PACKAGE.equals(foregroundPackage)) {

            pollInterval = FALLBACK_POLL_MS;
        } else {
            pollInterval = IDLE_POLL_MS;
        }
        boolean needRefresh = configDirty
                || !configLoadedOnce
                || now - lastConfigAttempt >= pollInterval;
        if (!needRefresh) {
            return;
        }

        if (configFetchFailures > 0
                && now - lastConfigAttempt < configRetryDelayMs(configFetchFailures)) {
            return;
        }
        lastConfigAttempt = now;
        configDirty = false;
        configFetchInFlight = true;
        ConfigFetcher.fetchAsync(context, json -> {
            configFetchInFlight = false;
            if (json == null) {

                if (configFetchFailures < Integer.MAX_VALUE) {
                    configFetchFailures++;
                }
                return;
            }
            configFetchFailures = 0;
            if (json.equals(lastConfigJson)) {
                return;
            }
            lastConfigJson = json;
            PoemConfig parsed = ConfigCodec.decode(json);
            config = parsed;
            Log.setDebug(parsed.debugLog);
            enabledPoems = PoemLibrary.enabledOnly(PoemLibrary.load(parsed.poemsSeeded, parsed.poemsJson));
            configLoadedOnce = true;
            Log.diag("CONF apply ver=" + parsed.configRevision
                    + " enabled=" + parsed.enabled
                    + " poems=" + enabledPoems.size()
                    + " speed=" + parsed.speedPx
                    + " clock=" + parsed.clockSeconds
                    + " lockPause=" + parsed.lockscreenPause
                    + " debug=" + parsed.debugLog);
            if (foreground != null) {
                foreground.updateConfig(!parsed.whitelist.isEmpty(), parsed.enabled);
                foreground.syncPolling(screenInteractive);
            }
            markDirty();
        });
    }

    static long configRetryDelayMs(int failures) {
        if (failures <= 0) {
            return 0L;
        }
        int shift = Math.min(failures - 1, 3);
        long delay = CONFIG_RETRY_BASE_MS << shift;
        return Math.min(delay, CONFIG_RETRY_MAX_MS);
    }

    private void registerConfigObserver() {
        try {
            android.net.Uri uri = android.net.Uri.parse("content://" + FeatureAudit.PROVIDER_AUTHORITY + "/config");
            configObserver = new ContentObserver(handler) {
                @Override
                public void onChange(boolean selfChange) {
                    configDirty = true;

                    configFetchFailures = 0;
                    markDirty();
                }
            };
            context.getContentResolver().registerContentObserver(uri, true, configObserver);
            configObserverFailed = false;
            Log.diag("CONF observer=on");
        } catch (Throwable t) {
            configObserverFailed = true;
            Log.diag("CONF push=off fallback=poll");
        }
    }
    private void maybeLogStats() {
        long now = System.currentTimeMillis();
        if (now - lastStatLogAt < STAT_LOG_INTERVAL_MS) {
            return;
        }
        lastStatLogAt = now;
        if (runtime.frameCount() > 0) {

            Log.diag("POEM stat frames=" + runtime.frameCount()
                    + " skip=" + runtime.skippedFrames()
                    + " avgMs=" + (runtime.avgFrameCostUs() / 1000f));
            runtime.resetStats();
        }
        Log.flushIfDebug();
    }

    private final class EngineHost implements PoemEngine.Host {
        @Override
        public void startCrawl(PoemItem poem, String displayText, int speedPx) {
            View container = activeHost();
            if (container == null) {
                engine.pause("view-unavailable");
                return;
            }
            runtime.setStatusContainer(container);
            View statusWin = statusRef.get();
            View lockOcclude = lockOccludeRoot();
            boolean locked = lockscreenState != null && lockscreenState;
            Log.diag("POEM startOcclude lock=" + (lockOcclude == null
                    ? "null" : lockOcclude.getClass().getName())
                    + " host=" + container.getClass().getSimpleName());
            if (locked && lockOcclude == null) {
                runtime.setOccludeHost(null);
            } else {
                runtime.setOccludeHost(lockOcclude != null ? lockOcclude : statusWin);
            }
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
            View colorRoot = statusWin != null ? statusWin : container;
            View root = ViewTree.findContentRoot(colorRoot);
            colorSampler.sample(colorRoot, root);
            colorSampler.startTracking(tintApplier);
            runtime.setTextColor(colorSampler.color());

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

            if (state == PoemEngine.State.PAUSED || Log.allow("state", 300_000L)) {
                Log.diag("POEM state=" + s.toLowerCase(java.util.Locale.ROOT));
            }
        }
    }

    private float frameL, frameT, frameR, frameB;

    private final FeatureGuard.Body occludeBody = () -> runtime.updateOcclusion(
            frameL, frameT, frameR, frameB);

    private final RuntimeListener runtimeListener = new RuntimeListener();

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

            colorSampler.startTracking(tintApplier);
        }

        @Override
        public void onOverlayDetached() {
            colorSampler.stopTracking();
        }
    }
}
