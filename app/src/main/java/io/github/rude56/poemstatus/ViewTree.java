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

    static View findByClassNameAny(String className) {
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
                View hit = findByClass((View) o, className, 0);
                if (hit != null) {
                    return hit;
                }
            }
        } catch (Throwable ignored) {

        }
        return null;
    }

    private static View lookupContentRoot(View window) {
        // 必须绑 status_bar_container：Compose 化状态栏的图标由它那一次绘制提交。
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

    /**
     * 这个 View 是不是「整条状态栏」尺寸：占满屏宽、只有状态栏那么高。
     *
     * <p>锁屏顶栏里也有一个 status_bar_container，但它只包住右侧图标区（实测 397px），
     * 用它挖洞会漏掉左侧运营商文字。所以锁屏必须挖整条。
     *
     * <p>判据全部按运行时实测尺寸，不写死任何机型的像素值：
     * 宽度看是否接近满屏，高度看是否在一到两倍状态栏高度之间。
     */
    static boolean isFullBar(View v) {
        if (v == null || v.getWidth() <= 0 || v.getHeight() <= 0) {
            return false;
        }
        int screenW = v.getResources().getDisplayMetrics().widthPixels;
        int barH = statusBarHeightPx(v);
        if (barH <= 0 || barH > v.getHeight() * 4) {
            // 状态栏高度明显不合理时，退化成「矮条」判断：
            // 高度不超过屏高 1/8 且很宽，就当它是顶栏。
            int screenH = v.getResources().getDisplayMetrics().heightPixels;
            boolean wideOnly = screenW <= 0 || v.getWidth() >= screenW * 0.9f;
            return wideOnly && screenH > 0 && v.getHeight() <= screenH / 8;
        }
        boolean wide = screenW <= 0 || v.getWidth() >= screenW * 0.9f;
        return wide && v.getHeight() <= barH * 2;
    }

    static View findOccludeRoot(View host) {
        if (host == null) {
            return null;
        }
        View byId = findByResourceId(host, "status_bar_container");
        if (byId != null && byId.getWidth() > 0 && byId.getHeight() > 0) {
            return byId;
        }
        int bar = statusBarHeightPx(host);
        if (bar > 0) {
            View band = findHeightBand(host, bar, 0);
            if (band != null) {
                return band;
            }
        }
        if (bar > 0 && host.getHeight() > bar * 3) {
            return null;
        }
        return host;
    }

    private static View findHeightBand(View v, int bar, int depth) {
        if (v == null || depth > 4) {
            return null;
        }
        if (!(v instanceof ViewGroup)) {
            return null;
        }
        ViewGroup g = (ViewGroup) v;
        for (int i = 0; i < g.getChildCount(); i++) {
            View c = g.getChildAt(i);
            if (c == null || c.getHeight() <= 0) {
                continue;
            }
            if (Math.abs(c.getHeight() - bar) <= 2 && c.getWidth() >= g.getWidth() - 2) {
                return c;
            }
        }
        for (int i = 0; i < g.getChildCount(); i++) {
            View hit = findHeightBand(g.getChildAt(i), bar, depth + 1);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    /**
     * 状态栏高度（px）。取不到就按密度估算，不要写死某个机型的像素值。
     *
     * <p>写死 112 只在 1440x3200/640dpi 上成立；1080p 机型状态栏大约 66-84px，
     * 1.5K/2K 机型差别更大，写死会让 isFullBar 之类的尺寸判断直接错掉。
     */
    static int statusBarHeightPx(View v) {
        if (v == null) {
            return 0;
        }
        try {
            int id = v.getResources().getIdentifier("status_bar_height", "dimen", "android");
            if (id != 0) {
                int px = v.getResources().getDimensionPixelSize(id);
                if (px > 0) {
                    return px;
                }
            }
        } catch (Throwable ignored) {

        }
        try {
            // 兜底：按 AOSP 默认 24dp 估算，随密度自适应
            return Math.round(24 * v.getResources().getDisplayMetrics().density);
        } catch (Throwable ignored) {
            return 0;
        }
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

    static View findByClassName(View root, String className, int depth) {
        return findByClass(root, className, depth);
    }

    /** 按简单类名查找：不依赖具体包名，跨 ROM 更稳。 */
    static View findBySimpleName(View root, String simpleName, int depth) {
        if (root == null || depth > MAX_DEPTH) {
            return null;
        }
        if (simpleName.equals(root.getClass().getSimpleName())) {
            return root;
        }
        if (root instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) root;
            for (int i = 0; i < g.getChildCount(); i++) {
                View hit = findBySimpleName(g.getChildAt(i), simpleName, depth + 1);
                if (hit != null) {
                    return hit;
                }
            }
        }
        return null;
    }

    /** 在所有窗口根里按简单类名查找。 */
    static View findBySimpleNameAny(String simpleName) {
        for (View root : allWindowRoots()) {
            View hit = findBySimpleName(root, simpleName, 0);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    /**
     * 按简单类名的<b>片段</b>查找（包含即命中）。
     *
     * <p>不同 ROM 会给同一角色加前缀或改后缀，例如
     * {@code KeyguardStatusBarView} / {@code KeyguardStatusBarWindowView}
     * / {@code MiuiKeyguardStatusBarView}。精确匹配会全部落空，
     * 用片段匹配能覆盖这些变体。
     */
    static View findBySimpleNameContains(View root, String fragment) {
        return findBySimpleNameContains(root, fragment, 0);
    }

    private static View findBySimpleNameContains(View root, String fragment, int depth) {
        if (root == null || fragment == null || depth > MAX_DEPTH) {
            return null;
        }
        String simple = root.getClass().getSimpleName();
        if (simple != null && simple.contains(fragment)) {
            return root;
        }
        if (root instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) root;
            for (int i = 0; i < g.getChildCount(); i++) {
                View hit = findBySimpleNameContains(g.getChildAt(i), fragment, depth + 1);
                if (hit != null) {
                    return hit;
                }
            }
        }
        return null;
    }

    /** 在所有窗口根里按类名片段查找。 */
    static View findBySimpleNameContainsAny(String fragment) {
        for (View root : allWindowRoots()) {
            View hit = findBySimpleNameContains(root, fragment, 0);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    /**
     * 在 root 里找「顶部对齐、接近满宽、矮」的那一条 —— 状态栏的几何特征。
     *
     * <p>这是完全基于尺寸的兜底：类名和资源 id 全都对不上时（第三方 ROM 改名、
     * Compose 化后没有 id 等情况），靠几何特征仍然能定位到状态栏那一条。
     */
    static View findTopBand(View root) {
        if (root == null) {
            return null;
        }
        int screenW = root.getResources().getDisplayMetrics().widthPixels;
        int barH = statusBarHeightPx(root);
        return findTopBand(root, screenW, barH, 0);
    }

    private static View findTopBand(View v, int screenW, int barH, int depth) {
        if (v == null || !(v instanceof ViewGroup) || depth > 8) {
            return null;
        }
        ViewGroup g = (ViewGroup) v;
        for (int i = 0; i < g.getChildCount(); i++) {
            View c = g.getChildAt(i);
            if (c == null || c.getHeight() <= 0 || c.getWidth() <= 0) {
                continue;
            }
            if (c.getVisibility() != View.VISIBLE) {
                continue;
            }
            boolean wide = screenW <= 0 || c.getWidth() >= screenW * 0.9f;
            boolean shortEnough = barH > 0 && c.getHeight() <= barH * 2
                    || c.getHeight() <= v.getHeight() / 8;
            if (!wide || !shortEnough) {
                continue;
            }
            // 必须是顶部对齐（状态栏永远贴在 y=0）
            int[] loc = new int[2];
            c.getLocationOnScreen(loc);
            if (loc[1] <= barH / 2 + 1) {
                return c;
            }
        }
        for (int i = 0; i < g.getChildCount(); i++) {
            View hit = findTopBand(g.getChildAt(i), screenW, barH, depth + 1);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    static java.util.List<View> allWindowRoots() {
        java.util.List<View> out = new java.util.ArrayList<>();
        try {
            Class<?> cls = Reflect.findClass("android.view.WindowManagerGlobal", null);
            if (cls == null) {
                return out;
            }
            java.lang.reflect.Method getInstance = Reflect.findMethod(cls, "getInstance");
            if (getInstance == null) {
                return out;
            }
            Object global = getInstance.invoke(null);
            Object roots = Reflect.getField(global, "mViews");
            if (!(roots instanceof java.util.List)) {
                return out;
            }
            for (Object o : (java.util.List<?>) roots) {
                if (o instanceof View) {
                    out.add((View) o);
                }
            }
        } catch (Throwable ignored) {

        }
        return out;
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
