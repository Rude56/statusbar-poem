package io.github.rude56.poemstatus;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/**
 * 状态栏 View 树的只读查找工具。
 *
 * <p>这些方法都是<b>低频</b>路径（每首诗开始时找一次内容根、取色时按弱引用缓存的
 * 时钟 / 文字控件兜底重找），绝不进帧循环。
 *
 * <p>本类的前身是 {@code ModuleOccluder} 里的静态查找方法。那个类实现的是
 * 「逐个 View 改 alpha」的旧遮挡方案，如今唯一的遮挡方式是
 * {@link HolePuncher} 的一次性画布裁剪，旧的 alpha 路径已整体删除 ——
 * 但其中「怎么在 Compose 化的状态栏里找到合适的内容根」这件事仍然有用，
 * 所以单独抽到这里。
 */
final class ViewTree {

    private static final int MAX_DEPTH = 24;

    private ViewTree() {
    }

    /**
     * 给「挖洞」用的内容根。
     *
     * <p><b>必须在 Compose 画布的祖先层挖</b>：实测树是
     * {@code status_bar_container > ComposeView > AndroidComposeView > ... > status_bar_contents}。
     * 时钟和 WiFi/电量 都是由 {@code AndroidComposeView} 那一次绘制提交的，
     * 所以在 {@code status_bar_contents} 上裁剪<b>管不到</b>它们
     * （截图实测：图标照旧显示）。
     *
     * <p>因此选 {@code status_bar_container}：它包住整块 Compose 画布与原生互操作层，
     * 又<b>不含</b>我们自己的诗句层（OverlayView 挂在窗口层，是它的祖辈的兄弟）。
     */
    static View findContentRoot(View window) {
        if (window == null) {
            return null;
        }
        // 缓存：同一个窗口 + 同一个资源 id 解析出来的根在整个生命周期里不变。
        //
        // 这个方法是「每首诗开始时」调一次（正常情况下每天约 6600 次）。
        // 每次都要 getIdentifier（查资源表）+ findViewById（递归遍历子树）——
        // 而窗口被重建时这个缓存会因为 window 引用不同而自动失效。
        View cached = cachedRoot;
        if (cached != null && cachedWindow == window && cached.getParent() != null) {
            return cached;
        }
        View found = lookupContentRoot(window);
        if (found != null) {
            cachedRoot = found;
            cachedWindow = window;
        }
        return found;
    }

    private static View cachedRoot;
    private static View cachedWindow;

    private static View lookupContentRoot(View window) {
        for (String id : new String[]{"status_bar_container",
                "status_bar_launch_animation_container"}) {
            View v = findByResourceId(window, id);
            if (v != null && v.getWidth() > 0 && v.getHeight() > 0) {
                return v;
            }
        }
        // 其它 ROM：找窗口下最外层的 ComposeView。
        View compose = findOutermostComposeView(window, 0);
        if (compose != null) {
            return compose;
        }
        return window;
    }

    /** 找最外层（深度最小）的 ComposeView。 */
    private static View findOutermostComposeView(View v, int depth) {
        if (v == null || depth > MAX_DEPTH) {
            return null;
        }
        if ("ComposeView".equals(v.getClass().getSimpleName())
                && v.getWidth() > 0 && v.getHeight() > 0) {
            return v;
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                View hit = findOutermostComposeView(g.getChildAt(i), depth + 1);
                if (hit != null) {
                    return hit;
                }
            }
        }
        return null;
    }

    static View findByResourceId(View root, String resName) {
        try {
            int id = root.getResources().getIdentifier(resName, "id", "com.android.systemui");
            if (id != 0) {
                return root.findViewById(id);
            }
        } catch (Throwable ignored) {
            // 换下一个候选。
        }
        return null;
    }

    /** 原生时钟控件（取色用）；找不到返回 null。 */
    static View findClock(View root) {
        return findByClass(root, "com.android.systemui.statusbar.policy.Clock", 0);
    }

    private static View findByClass(View root, String className, int depth) {
        if (root == null || depth > MAX_DEPTH) {
            return null;
        }
        if (className.equals(root.getClass().getName())) {
            return root;
        }
        if (root instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) root;
            for (int i = 0; i < g.getChildCount(); i++) {
                View hit = findByClass(g.getChildAt(i), className, depth + 1);
                if (hit != null) {
                    return hit;
                }
            }
        }
        return null;
    }

    /** 状态栏里第一个「文字色可用」的可见 TextView（取色兜底）。 */
    static TextView findFirstText(View root) {
        return findFirstText(root, 0);
    }

    private static TextView findFirstText(View root, int depth) {
        if (root == null || depth > MAX_DEPTH) {
            return null;
        }
        if (root instanceof TextView && root.getVisibility() == View.VISIBLE) {
            TextView tv = (TextView) root;
            if (ColorResolver.isUsableTextColor(tv.getCurrentTextColor())) {
                return tv;
            }
        }
        if (root instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) root;
            for (int i = 0; i < g.getChildCount(); i++) {
                TextView hit = findFirstText(g.getChildAt(i), depth + 1);
                if (hit != null) {
                    return hit;
                }
            }
        }
        return null;
    }
}
