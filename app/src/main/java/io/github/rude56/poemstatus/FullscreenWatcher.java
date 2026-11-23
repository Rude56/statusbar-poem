package io.github.rude56.poemstatus;

import android.content.Context;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;

/**
 * 全屏检测：状态栏不可见 = 全屏。
 *
 * <p>判据来自实测（MEIZU 20 Pro / Android 17），三阶段采样对比：
 * <pre>
 * 场景          getCurrentWindowMetrics()   StatusBarWindowView.getRootWindowInsets()
 * 桌面/锁屏      sbVis=true  sbTop=112        sbVis=false sbTop=0
 * 游戏全屏       sbVis=false sbTop=0          sbVis=false sbTop=0
 * 下滑唤出栏     sbVis=true  sbTop=112        sbVis=false sbTop=0
 * </pre>
 *
 * <p>右边那一路是上一版的错误做法：StatusBarWindowView 是状态栏 insets 的
 * <b>提供者</b>而不是消费者，它读自己给出去的 inset，值恒为 0 且一旦变 false
 * 就再也不更新 —— 于是经历一次锁屏/全屏后就被永久判成全屏，诗句再也不出来。
 *
 * <p>左边那一路是 SystemUI 自己窗口的消费方视角，
 * {@code WindowManager.getCurrentWindowMetrics().getWindowInsets()} 三阶段全部跟随，
 * 且下滑临时唤出时立刻回到 true。
 *
 * <p>规则（与用户确认过）：
 * <ul>
 *   <li>状态栏隐藏 = 全屏 = 暂停</li>
 *   <li>不要求导航栏隐藏</li>
 *   <li>横屏本身不算全屏</li>
 *   <li>临时唤出状态栏 → 状态栏可见 → 恢复播放（不做延迟复核）</li>
 * </ul>
 */
final class FullscreenWatcher {
    /** 读不到时返回 null，调用方应当当作「没有全屏」，绝不误暂停。 */
    private static final int UNKNOWN = -1;
    private static final int SHOWN = 0;
    private static final int HIDDEN = 1;

    private static int lastState = UNKNOWN;
    private static Context appContext;

    private FullscreenWatcher() {
    }

    static void init(Context ctx) {
        if (ctx != null) {
            appContext = ctx.getApplicationContext() == null ? ctx : ctx.getApplicationContext();
        }
    }

    /**
     * 兜底拿 context：正常路径由 init() 注入；
     * 若注入没发生（hook 没触发等），就用反射拿 SystemUI 的 Application，
     * 避免全屏检测永久返回 null 导致该暂停时不暂停。
     */
    private static Context context() {
        Context ctx = appContext;
        if (ctx != null) {
            return ctx;
        }
        try {
            Class<?> at = Class.forName("android.app.ActivityThread");
            java.lang.reflect.Method current = at.getDeclaredMethod("currentApplication");
            current.setAccessible(true);
            Object app = current.invoke(null);
            if (app instanceof Context) {
                appContext = (Context) app;
                return appContext;
            }
        } catch (Throwable ignored) {

        }
        return null;
    }

    /**
     * 状态栏是否被隐藏（即处于全屏）。
     *
     * @return null 表示暂时读不到
     */
    static Boolean statusBarHidden() {
        Context ctx = context();
        if (ctx == null) {
            return null;
        }
        try {
            WindowManager wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
            if (wm == null) {
                return null;
            }
            WindowMetrics metrics = wm.getCurrentWindowMetrics();
            if (metrics == null) {
                return null;
            }
            WindowInsets insets = metrics.getWindowInsets();
            if (insets == null) {
                return null;
            }
            return !insets.isVisible(WindowInsets.Type.statusBars());
        } catch (Throwable t) {
            return null;
        }
    }

    /** 状态变化时才返回新值，否则返回 null；给日志用，避免刷屏。 */
    static Boolean changed() {
        Boolean hidden = statusBarHidden();
        if (hidden == null) {
            return null;
        }
        int now = hidden ? HIDDEN : SHOWN;
        if (now == lastState) {
            return null;
        }
        lastState = now;
        return hidden;
    }

    /** 一行诊断。 */
    static String describe() {
        Boolean hidden = statusBarHidden();
        return hidden == null ? "unknown" : (hidden ? "fullscreen" : "normal");
    }
}
