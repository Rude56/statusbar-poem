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
            Log.i("POEM tint hook=on");
        } catch (Throwable t) {
            Log.w("POEM tint hook failed: " + t.getClass().getSimpleName());
        }
    }

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

                boolean clipped = false;
                try {
                    clipped = HolePuncher.beginClip(canvas, (View) self);
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
            Log.i("POEM hole hook=on via View.draw");
        } catch (Throwable t) {
            HolePuncher.installed = false;
            Log.w("POEM hole hook failed: " + t.getClass().getSimpleName());
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
            Log.i("POEM appearance hook=on");
        } catch (Throwable t) {
            Log.w("POEM appearance hook failed: " + t.getClass().getSimpleName());
        }
    }

    private void onStatusBarAttached(View window) {

        statusRef = new WeakReference<>(window);

        if (runtime.setStatusContainer(window)) {
            engine.pause("view-replaced");
        }
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
                    Log.i("CONF foreground=" + pkg
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
                Log.i("POEM keyguard resolve locked=" + locked);
                markDirty();
            });
            Log.i("POEM keyguard listener=on");
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
        Log.i("CONF bar visible shown=" + shown
                + " windowVis=" + windowVis + " vis=" + vis);
    }

    private boolean lastBarShown = true;
    private int lastBarWindowVisibility = -1;
    private int lastBarVisibility = -1;
    private void tick() {
        View container = statusRef.get();
        if (container == null || !container.isAttachedToWindow()) {
            engine.pause("view-unavailable");
            return;
        }

        if (container.getWidth() <= 0 || container.getHeight() <= 0) {
            handler.removeCallbacks(tickRunnable);
            handler.postDelayed(tickRunnable, 120L);
            return;
        }
        runtime.setStatusContainer(container);
        maybeReloadConfig();
        updateOrientation(container);
        noteStatusBarVisibility(container);
        if (lockscreenState == null) {
            refreshLockscreenState();
        }
        engine.configure(config, enabledPoems);
        maybeLogStats();
        refreshCutout(container);
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
    private boolean lockscreenPauseActive() {
        if (!config.lockscreenPause) {
            return false;
        }
        Boolean locked = lockscreenState;
        return locked != null && locked;
    }

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
            enabledPoems = PoemLibrary.enabledOnly(PoemLibrary.load(parsed.poemsSeeded, parsed.poemsJson));
            configLoadedOnce = true;
            Log.i("CONF apply ver=" + parsed.configRevision
                    + " enabled=" + parsed.enabled
                    + " poems=" + enabledPoems.size()
                    + " speed=" + parsed.speedPx
                    + " clock=" + parsed.clockSeconds);
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

            Log.i("POEM stat frames=" + runtime.frameCount()
                    + " skip=" + runtime.skippedFrames()
                    + " avgMs=" + (runtime.avgFrameCostUs() / 1000f));
            runtime.resetStats();
        }
    }

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
            View root = ViewTree.findContentRoot(container);
            colorSampler.sample(container, root);
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
                Log.i("POEM state=" + s.toLowerCase(java.util.Locale.ROOT));
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
