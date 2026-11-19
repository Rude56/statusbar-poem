package io.github.rude56.poemstatus;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

final class ViewTree {
    private static final int MAX_DEPTH = 24;
    private ViewTree() {
    }
    static View findContentRoot(View window) {
        if (window == null) {
            return null;
        }

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
        // 挖洞根必须是 Compose 画布的祖先，绑到 status_bar_contents 管不到图标。
        for (String id : new String[]{"status_bar_container",
                "status_bar_launch_animation_container"}) {
            View v = findByResourceId(window, id);
            if (v != null && v.getWidth() > 0 && v.getHeight() > 0) {
                return v;
            }
        }

        View compose = findOutermostComposeView(window, 0);
        if (compose != null) {
            return compose;
        }
        return window;
    }

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
