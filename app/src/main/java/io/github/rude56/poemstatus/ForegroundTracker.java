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

/**
 * 前台应用跟踪（白名单用）。
 *
 * <p>省电做法：注册 SystemUI 本来就会用的 {@code ITaskStackListener}，
 * 只在任务栈真正变化时回调一次，不做周期轮询。
 * 注册失败才退回「白名单非空 + 只在开启时才跑」的低频兜底轮询。
 *
 * <p><b>为什么解析前台包名要写这么多候选</b>：{@code ActivityTaskManager.getTasks}
 * 的签名在版本之间反复变过（`(int)` / `(int, boolean)` /
 * `(int, boolean, boolean)` / `(int, boolean, boolean, int)`），
 * 写死某一个重载在部分 ROM 上会直接 {@code NoSuchMethodException}，
 * 而 {@code ActivityManager.getRunningTasks} 对非自家进程又常常返回空表 ——
 * 两条路一起失败时 `topPackage()` 返回空串，白名单<b>静默失效</b>
 * （表现为「加了白名单应用、在那儿也照滚」）。
 *
 * <p>所以这里改成：把服务上所有叫 {@code getTasks} 的方法都试一遍，
 * 再补 {@code getFocusedRootTaskInfo} / {@code getAllRootTaskInfos}，
 * 最后才退回 {@code getRunningTasks}。任何一个成功就返回。
 * 全部失败会打一条诊断日志，而不是什么都不说。
 */
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
    private boolean fallback;
    private Object listenerProxy;
    /** 已经报过「解析不出来」的日志，避免每个任务栈变化都刷一条。 */
    private boolean reportedEmpty;
    /** 兜底轮询是否启用（只有白名单非空时）。 */
    private boolean polling;
    /** 有一次被节流挡下、需要补跑的刷新。 */
    private boolean pendingRefresh;

    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            refreshAsync();
        }
    };

    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            refreshAsync();
            // 只在「白名单启用」期间续期，由 start()/stopPolling() 控制开关。
            if (polling) {
                main.postDelayed(this, FALLBACK_POLL_MS);
            }
        }
    };

    ForegroundTracker(Context ctx, Listener l) {
        this.context = ctx;
        this.listener = l;
    }

    String current() {
        return current;
    }

    boolean isFallback() {
        return fallback;
    }

    /**
     * 开始跟踪。
     *
     * <p><b>白名单启用时始终保留一个低频兜底轮询</b>，即便任务栈监听注册成功。
     *
     * <p>原因是实测教训：本机 ROM 上 {@code registerTaskStackListener} 会返回成功，
     * 但回调<b>一次都不会来</b>（切到白名单应用后前台值一直停在启动时的那个包名）。
     * 只信事件会让白名单「注册成功但完全失效」，且没有任何报错。
     *
     * <p>代价可控：轮询跑在后台线程，一次只是几次反射读字段；
     * 而且只在<b>白名单非空</b>时才启动 —— 用户没配白名单就零开销。
     * 任务栈监听仍然保留，它让常见场景（切应用）响应更快。
     *
     * @param whitelistActive 白名单是否非空；为空时完全不轮询
     */
    void start(boolean whitelistActive) {
        if (!registered) {
            registered = true;
            registered = tryRegisterTaskStackListener();
            fallback = !registered;
            if (!registered) {
                Log.i("CONF push=off fallback=poll reason=task-listener");
            }
        }
        this.whitelistActive = whitelistActive;
        setPolling(whitelistActive);
        refreshAsync();
    }

    /** 白名单是否非空（由配置决定，与屏幕状态无关）。 */
    private boolean whitelistActive;

    /**
     * 开关兜底轮询。
     *
     * <p>单独拆出来是为了让「熄屏」也能把它停掉：以前轮询只跟白名单是否非空有关，
     * 于是<b>熄屏以后仍然每 5 秒</b>去 {@code ActivityTaskManager} 反射查一次前台应用 ——
     * 屏幕都黑了，前台是谁毫无意义，这是纯浪费的唤醒。
     *
     * @param active true 表示现在需要轮询
     */
    void setPolling(boolean active) {
        main.removeCallbacks(pollRunnable);
        polling = active;
        if (active) {
            main.postDelayed(pollRunnable, FALLBACK_POLL_MS);
        }
    }

    /** 当前是否应该轮询：白名单非空<b>且</b>屏幕亮着。 */
    void syncPolling(boolean screenInteractive) {
        setPolling(whitelistActive && screenInteractive);
    }

    void stopPolling() {
        polling = false;
        main.removeCallbacks(pollRunnable);
    }

    /**
     * 刷新前台包名。
     *
     * <p><b>节流不能丢状态</b>：任务栈监听的回调在事件后 250ms 触发，而节流窗口
     * 是 400ms —— 如果上一次刷新刚过去 250ms，这次就会被直接丢掉，
     * 而且<b>没人会再补一次</b>。结果是前台停在一个过时的值上，
     * 白名单看起来「完全不生效」。所以被节流挡住时必须记下「还有一个待刷新」，
     * 到窗口结束后立刻补跑。
     */
    void refreshAsync() {
        if (pendingRefresh) {
            return;
        }
        long now = System.currentTimeMillis();
        long wait = MIN_REFRESH_GAP_MS - (now - lastRefreshAt);
        if (wait > 0) {
            // 还在节流窗口里：排一次补跑，而不是把这次状态更新丢掉。
            pendingRefresh = true;
            main.postDelayed(() -> {
                pendingRefresh = false;
                refreshAsync();
            }, wait);
            return;
        }
        lastRefreshAt = now;
        if (!BUSY.compareAndSet(false, true)) {
            // 后台线程正忙：稍后再来，同样不能丢。
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
                // 拿不到就保持上一次结果。
            } finally {
                BUSY.set(false);
            }
            final String result = pkg;
            if (result == null || result.isEmpty() || result.equals(current)) {
                return;
            }
            main.post(() -> {
                current = result;
                if (listener != null) {
                    listener.onForegroundChanged(result);
                }
            });
        });
    }

    /**
     * 解析当前前台包名；所有候选都失败时返回空串。
     *
     * <p>每个候选都单独 try/catch：某个重载不存在只影响它自己，不影响后面。
     */
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

    /** 路线一：ActivityTaskManager 服务的各种取任务接口。 */
    private String fromActivityTaskManager() {
        Object service = atmService();
        if (service == null) {
            return "";
        }
        // 1) 焦点栈信息：最直接，参数最少。
        for (String m : new String[]{"getFocusedRootTaskInfo", "getFocusedTaskInfo"}) {
            try {
                Object info = service.getClass().getMethod(m).invoke(service);
                String p = packageOf(info);
                if (!p.isEmpty()) {
                    return p;
                }
            } catch (Throwable ignored) {
                // 换下一个。
            }
        }
        // 2) getTasks 的所有重载：签名逐版本变过，别写死。
        //
        // getMethods() 每次都会新建一个 Method[]（几十个元素）并克隆每个参数的
        // 类型数组。以前这里是<b>每 5 秒</b>跑一遍，24 小时几万次反射枚举 ——
        // 全是可缓存的常量。服务类在整个进程生命周期里不会变，缓存一次即可。
        for (Method m : taskMethods(service)) {
            try {
                Object tasks = m.invoke(service, defaultArgs(m.getParameterTypes()));
                String p = firstPackageIn(tasks);
                if (!p.isEmpty()) {
                    return p;
                }
            } catch (Throwable ignored) {
                // 这个重载不可用，换下一个。
            }
        }
        // 3) 全部根任务：通常在 SystemUI 里有权限读。
        for (Method method : rootTaskMethods(service)) {
            try {
                Object list = method.invoke(service, defaultArgs(method.getParameterTypes()));
                String p = firstPackageIn(list);
                if (!p.isEmpty()) {
                    return p;
                }
            } catch (Throwable ignored) {
                // 换下一个。
            }
        }
        return "";
    }

    /** 路线二：老的 ActivityManager 接口。 */
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
            // 无权限 / 被 ROM 裁剪。
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
                // 换下一个。
            }
        }
        return null;
    }

    /**
     * ATM 服务上所有 {@code getTasks} 重载（缓存）。
     *
     * <p>反射枚举 {@code getMethods()} 会分配一个 Method 数组 ——
     * 以前每 5 秒一次。服务类是进程级单例，这份列表一辈子都不用重算。
     */
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

    /** 根任务列表接口（缓存，理由同 {@link #taskMethods}）。 */
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

    /** ATM 服务单例缓存（反射拿一次就够）。 */
    private static volatile Object atmServiceCache;

    /** 给反射调用准备「无害的默认参数」：数字给 0/-1，布尔给 false，其余 null。 */
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

    /** 从 List&lt;任务信息&gt; 里取第一个能解析出包名的。 */
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

    /**
     * 从一个任务信息对象里抠出包名。
     *
     * <p>字段名在不同版本里叫 {@code topActivity} / {@code baseActivity} /
     * {@code realActivity}，有些版本只有 {@code getTopActivity()} 方法。
     */
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
                // 换下一个字段。
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
                // 换下一个方法。
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
                        if (name.startsWith("onTaskStackChanged") || name.startsWith("onTaskCreated")
                                || name.startsWith("onTaskFocusChanged")
                                || name.startsWith("onActivityFocusChanged")
                                || name.startsWith("onTaskMovedToFront")) {
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
            Log.i("CONF task-listener=on");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
