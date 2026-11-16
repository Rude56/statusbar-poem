package io.github.rude56.poemstatus;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/** 状态栏 View 树的只读查找工具；全是低频路径（每首诗一次 / 取色兜底），绝不进帧循环。 */
final class ViewTree {

    /** 递归深度上限，挡住异常深的 View 树。 */
    private static final int MAX_DEPTH = 24;

    private ViewTree() {
    }

    /**
     * 挖洞用的内容根：必须在 Compose 画布的祖先层（{@code status_bar_container}）挖，
     * 在 {@code status_bar_contents} 上裁剪管不到时钟与 WiFi/电量；结果缓存到窗口重建才失效。
     */
    static View findContentRoot(View window) {
        if (window == null) {
            return null;
        }
        // 每首诗开始都会调，getIdentifier + findViewById 不便宜；窗口重建时 window 引用变化自动失效。
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
