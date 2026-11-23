package io.github.rude56.poemstatus;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

final class ForegroundTracker {
    private static final long FALLBACK_POLL_MS = 5000L;
    private static final long MIN_REFRESH_GAP_MS = 400L;
    interface Listener {
        void onForegroundChanged(String packageName);
    }
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "poem-fg");
        t.setDaemon(true);
        return t;
    });
    private static final AtomicBoolean BUSY = new AtomicBoolean(false);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Context context;
    private final Listener listener;

    private String current = "";
    private long lastRefreshAt;
    private boolean registered;

    private boolean registrationAttempted;
    private boolean fallback;
    private Object listenerProxy;

    private boolean reportedEmpty;

    private boolean polling;

    private boolean pendingRefresh;

    private boolean whitelistActive;

    private boolean screenInteractive = true;

    private boolean moduleEnabled;

    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (isTrackingNeeded()) {
                refreshAsync();
            }
        }
    };
    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isTrackingNeeded()) {
                polling = false;
                return;
            }
            refreshAsync();
            main.postDelayed(this, FALLBACK_POLL_MS);
        }
    };

    ForegroundTracker(Context ctx, Listener l) {
        this.context = ctx;
        this.listener = l;
    }

    String current() {
        return current;
    }

    private void ensureListenerRegistered() {
        if (registrationAttempted || !isTrackingNeeded()) {
            return;
        }
        registrationAttempted = true;
        registered = tryRegisterTaskStackListener();
        fallback = !registered;
        if (!registered) {
            Log.diag("CONF push=off fallback=poll reason=task-listener");
        }
    }

    void setPolling(boolean active) {
        main.removeCallbacks(pollRunnable);
        polling = active;
        if (active) {
            main.postDelayed(pollRunnable, FALLBACK_POLL_MS);
        }
    }
    void syncPolling(boolean screenInteractive) {
        this.screenInteractive = screenInteractive;
        ensureListenerRegistered();
        updatePolling();
        if (isTrackingNeeded()) {
            refreshAsync();
        }
    }

    void updateConfig(boolean whitelistActive, boolean moduleEnabled) {
        this.whitelistActive = whitelistActive;
        this.moduleEnabled = moduleEnabled;
        ensureListenerRegistered();
        updatePolling();
        if (isTrackingNeeded()) {
            refreshAsync();
        }
    }
    private boolean isTrackingNeeded() {
        return moduleEnabled && whitelistActive && screenInteractive;
    }

    private void updatePolling() {
        setPolling(isTrackingNeeded());
    }

    void refreshAsync() {
        if (!isTrackingNeeded() || pendingRefresh) {
            return;
        }
        long now = System.currentTimeMillis();
        long wait = MIN_REFRESH_GAP_MS - (now - lastRefreshAt);
        if (wait > 0) {

            pendingRefresh = true;
            main.postDelayed(() -> {
                pendingRefresh = false;
                refreshAsync();
            }, wait);
            return;
        }
        lastRefreshAt = now;
        if (!BUSY.compareAndSet(false, true)) {
            pendingRefresh = true;
            main.postDelayed(() -> {
                pendingRefresh = false;
                refreshAsync();
            }, MIN_REFRESH_GAP_MS);
            return;
        }
        IO.execute(() -> {
            String pkg = null;
            try {
                pkg = topPackage();
            } catch (Throwable ignored) {

            } finally {
                BUSY.set(false);
            }
            final String result = pkg;
            if (result == null || result.isEmpty()) {
                return;
            }
            main.post(() -> {
                if (!isTrackingNeeded() || result.equals(current)) {
                    return;
                }
                current = result;
                if (listener != null) {
                    listener.onForegroundChanged(result);
                }
            });
        });
    }

    private String topPackage() {
        String pkg = fromActivityTaskManager();
        if (!pkg.isEmpty()) {
            return pkg;
        }
        pkg = fromActivityManager();
        if (!pkg.isEmpty()) {
            return pkg;
        }
        if (!reportedEmpty) {
            reportedEmpty = true;
            Log.w("CONF foreground unresolved (whitelist will not work)");
        }
        return "";
    }

    private String fromActivityTaskManager() {
        Object service = atmService();
        if (service == null) {
            return "";
        }

        for (String m : new String[]{"getFocusedRootTaskInfo", "getFocusedTaskInfo"}) {
            try {
                Object info = service.getClass().getMethod(m).invoke(service);
                String p = packageOf(info);
                if (!p.isEmpty()) {
                    return p;
                }
            } catch (Throwable ignored) {

            }
        }

        for (Method m : taskMethods(service)) {
            try {
                Object tasks = m.invoke(service, defaultArgs(m.getParameterTypes()));
                String p = firstPackageIn(tasks);
                if (!p.isEmpty()) {
                    return p;
                }
            } catch (Throwable ignored) {
            }
        }
        for (Method method : rootTaskMethods(service)) {
            try {
                Object list = method.invoke(service, defaultArgs(method.getParameterTypes()));
                String p = firstPackageIn(list);
                if (!p.isEmpty()) {
                    return p;
                }
            } catch (Throwable ignored) {

            }
        }
        return "";
    }

    private String fromActivityManager() {
        try {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) {
                return "";
            }
            List<ActivityManager.RunningTaskInfo> tasks = am.getRunningTasks(1);
            if (tasks != null && !tasks.isEmpty()) {
                String p = packageOf(tasks.get(0));
                if (!p.isEmpty()) {
                    return p;
                }
            }
        } catch (Throwable ignored) {

        }
        return "";
    }

    private static Object atmService() {
        Object cached = atmServiceCache;
        if (cached != null) {
            return cached;
        }
        for (String cls : new String[]{
                "android.app.ActivityTaskManager", "android.app.ActivityManager"}) {
            try {
                Class<?> c = Class.forName(cls);
                Object svc = c.getMethod("getService").invoke(null);
                if (svc != null) {
                    atmServiceCache = svc;
                    return svc;
                }
            } catch (Throwable ignored) {

            }
        }
        return null;
    }

    private static Method[] taskMethodsCache;

    private static Method[] taskMethods(Object service) {
        Method[] cached = taskMethodsCache;
        if (cached != null) {
            return cached;
        }
        List<Method> found = new java.util.ArrayList<>(4);
        for (Method m : service.getClass().getMethods()) {
            if ("getTasks".equals(m.getName())) {
                found.add(m);
            }
        }
        Method[] arr = found.toArray(new Method[0]);
        taskMethodsCache = arr;
        return arr;
    }

    private static Method[] rootTaskMethodsCache;

    private static Method[] rootTaskMethods(Object service) {
        Method[] cached = rootTaskMethodsCache;
        if (cached != null) {
            return cached;
        }
        List<Method> found = new java.util.ArrayList<>(2);
        for (Method m : service.getClass().getMethods()) {
            String n = m.getName();
            if ("getAllRootTaskInfos".equals(n) || "getAllRootTaskInfosOnDisplay".equals(n)) {
                found.add(m);
            }
        }
        Method[] arr = found.toArray(new Method[0]);
        rootTaskMethodsCache = arr;
        return arr;
    }

    private static volatile Object atmServiceCache;

    private static Object[] defaultArgs(Class<?>[] types) {
        Object[] args = new Object[types.length];
        for (int i = 0; i < types.length; i++) {
            Class<?> t = types[i];
            if (t == int.class) {
                args[i] = i == 0 ? 1 : 0;
            } else if (t == boolean.class) {
                args[i] = false;
            } else if (t == long.class) {
                args[i] = 0L;
            } else {
                args[i] = null;
            }
        }
        return args;
    }

    private static String firstPackageIn(Object tasks) {
        if (!(tasks instanceof List)) {
            return "";
        }
        List<?> list = (List<?>) tasks;
        for (Object info : list) {
            String p = packageOf(info);
            if (!p.isEmpty()) {
                return p;
            }
        }
        return "";
    }

    private static String packageOf(Object info) {
        if (info == null) {
            return "";
        }
        for (String f : new String[]{"topActivity", "baseActivity", "realActivity"}) {
            try {
                Field field = info.getClass().getField(f);
                Object v = field.get(info);
                if (v instanceof ComponentName) {
                    String p = ((ComponentName) v).getPackageName();
                    if (p != null && !p.isEmpty()) {
                        return p;
                    }
                }
            } catch (Throwable ignored) {

            }
        }
        for (String m : new String[]{"getTopActivity", "getBaseActivity"}) {
            try {
                Object v = info.getClass().getMethod(m).invoke(info);
                if (v instanceof ComponentName) {
                    String p = ((ComponentName) v).getPackageName();
                    if (p != null && !p.isEmpty()) {
                        return p;
                    }
                }
            } catch (Throwable ignored) {

            }
        }
        return "";
    }

    private boolean tryRegisterTaskStackListener() {
        try {
            Class<?> atm = Class.forName("android.app.ActivityTaskManager");
            Method getService = atm.getMethod("getService");
            final Object service = getService.invoke(null);
            if (service == null) {
                return false;
            }
            Class<?> listenerCls = Class.forName("android.app.ITaskStackListener");
            Object proxy = Proxy.newProxyInstance(listenerCls.getClassLoader(),
                    new Class<?>[]{listenerCls}, (p, method, args) -> {
                        String name = method.getName();
                        boolean foregroundEvent = name.startsWith("onTaskStackChanged")
                                || name.startsWith("onTaskCreated")
                                || name.startsWith("onTaskFocusChanged")
                                || name.startsWith("onActivityFocusChanged")
                                || name.startsWith("onTaskMovedToFront");
                        if (foregroundEvent && isTrackingNeeded()) {
                            Log.d("CONF fg-event=" + name);
                            main.removeCallbacks(refreshRunnable);
                            main.postDelayed(refreshRunnable, 250L);
                        }
                        Class<?> rt = method.getReturnType();
                        if (rt == boolean.class) {
                            return false;
                        }
                        if (rt == int.class) {
                            return 0;
                        }
                        return null;
                    });
            Method register = service.getClass().getMethod("registerTaskStackListener", listenerCls);
            register.invoke(service, proxy);
            listenerProxy = proxy;
            Log.diag("CONF task-listener=on");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
