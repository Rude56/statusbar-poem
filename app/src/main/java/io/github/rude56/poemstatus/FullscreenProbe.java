package io.github.rude56.poemstatus;

import android.content.Context;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;

/**
 * 全屏检测探针：一次把所有候选信号都打出来，用来确定哪个真的会变。
 *
 * <p>上一版失败教训：状态栏窗口（StatusBarWindowView）自己就是状态栏 insets 的
 * <b>提供者</b>，它调 getRootWindowInsets() 拿到的是「别人给我的 inset」，
 * 实测恒为 sbTop=0、且 sbVisible 一旦变 false 就不再更新 —— 读到的是过期值。
 *
 * <p>所以这次同时对比几路信号，看谁真的跟随「状态栏是否可见」变化：
 * <ul>
 *   <li>A. WindowManager.getCurrentWindowMetrics().getWindowInsets() —— SystemUI 自己的窗口</li>
 *   <li>B. 状态栏窗口的 rootWindowInsets（上一版用的，已知会过期，留作对照）</li>
 *   <li>C. View 树的 shown/visibility 链</li>
 *   <li>D. display 的 app 区域高度（状态栏占位是否被算进去）</li>
 * </ul>
 */
final class FullscreenProbe {
    private FullscreenProbe() {
    }

    /** 生成一行诊断；只在 debug 打开时被调用。 */
    static String probe(Context ctx, View statusWindow) {
        StringBuilder sb = new StringBuilder(192);
        sb.append("A[metrics]").append(metrics(ctx));
        sb.append(" B[rootIns]").append(rootInsets(statusWindow));
        sb.append(" D[appArea]").append(appArea(ctx, statusWindow));
        return sb.toString();
    }

    /** A：SystemUI 自己窗口拿到的 insets —— 这是消费方视角，理论上会实时更新。 */
    private static String metrics(Context ctx) {
        if (ctx == null) {
            return "ctx=null";
        }
        try {
            WindowManager wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
            if (wm == null) {
                return "wm=null";
            }
            WindowMetrics m = wm.getCurrentWindowMetrics();
            WindowInsets ins = m.getWindowInsets();
            return "sbVis=" + ins.isVisible(WindowInsets.Type.statusBars())
                    + " sbTop=" + ins.getInsets(WindowInsets.Type.statusBars()).top
                    + " navVis=" + ins.isVisible(WindowInsets.Type.navigationBars())
                    + " frame=" + m.getBounds().width() + "x" + m.getBounds().height();
        } catch (Throwable t) {
            return "err=" + t.getClass().getSimpleName();
        }
    }

    /** B：状态栏窗口的 rootWindowInsets（上一版方案，留作对照）。 */
    private static String rootInsets(View v) {
        if (v == null) {
            return "v=null";
        }
        try {
            WindowInsets ins = v.getRootWindowInsets();
            if (ins == null) {
                return "null";
            }
            return "sbVis=" + ins.isVisible(WindowInsets.Type.statusBars())
                    + " sbTop=" + ins.getInsets(WindowInsets.Type.statusBars()).top;
        } catch (Throwable t) {
            return "err=" + t.getClass().getSimpleName();
        }
    }

    /** D：应用可用区域高度。状态栏被藏起来时，app 区域会变大。 */
    private static String appArea(Context ctx, View v) {
        try {
            int fullH = v == null ? 0 : v.getResources().getDisplayMetrics().heightPixels;
            int screenH = fullH;
            if (ctx != null) {
                WindowManager wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
                if (wm != null) {
                    WindowMetrics m = wm.getCurrentWindowMetrics();
                    screenH = m.getBounds().height();
                }
            }
            return "disp=" + fullH + " win=" + screenH;
        } catch (Throwable t) {
            return "err=" + t.getClass().getSimpleName();
        }
    }

    /** 这个 View 及其祖先链的 shown/vis/alpha，用来确认「状态栏窗口本身」有没有变。 */
    static String chain(View v) {
        if (v == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder(96);
        View p = v;
        for (int i = 0; i < 6 && p != null; i++) {
            sb.append(i == 0 ? "" : "<-")
                    .append(p.getClass().getSimpleName())
                    .append("[s=").append(p.isShown() ? 1 : 0)
                    .append(",v=").append(p.getVisibility())
                    .append(",a=").append(p.getAlpha())
                    .append(",h=").append(p.getHeight())
                    .append(']');
            p = p.getParent() instanceof View ? (View) p.getParent() : null;
        }
        return sb.toString();
    }
}
