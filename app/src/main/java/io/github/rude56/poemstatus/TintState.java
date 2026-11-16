package io.github.rude56.poemstatus;

/**
 * 状态栏前景色缓存。
 *
 * <p>诗句颜色必须和原生状态栏一致，但本机 ROM 的状态栏是 Compose 绘制的：
 * {@code DarkIconDispatcherImpl.mIconTint} 只在开机时算一次，之后切到浅色应用
 * 也不会再变（实测恒为白色，而屏幕上的图标已经变黑）。真正随应用背景变化的是
 * {@code LightBarControllerImpl.onStatusBarAppearanceChanged(...)} 收到的
 * {@code AppearanceRegion.appearance} 位 —— 这就是系统对每条状态栏「该用浅字还是深字」
 * 算好的最终答案。
 *
 * <p>所以这里只存<b>系统算好的结果</b>，不做任何亮度推断。
 *
 * <p><b>推送式通知</b>：值真的变了就<b>立刻</b>叫醒订阅者（{@link #setListener}），
 * 于是诗句颜色不再需要定时轮询 —— 切换深浅色应用时是系统推给我们的，
 * 我们不必每 300ms 去问一次。这是取色路径唯一真正的省电点。
 */
final class TintState {

    /** 0 表示还没拿到。ARGB，alpha 恒为 0xFF。 */
    private static volatile int tint;

    /** 系统算好的外观位；null 表示还没拿到。 */
    private static volatile Boolean lightStatusBars;

    /** 值变化时回调（在主线程由系统 Hook 触发）。 */
    interface Listener {
        void onTintChanged();
    }

    private static volatile Listener listener;

    private TintState() {
    }

    static void setListener(Listener l) {
        listener = l;
    }

    private static void notifyChanged() {
        Listener l = listener;
        if (l == null) {
            return;
        }
        try {
            l.onTintChanged();
        } catch (Throwable t) {
            // 订阅者出错不能影响系统自己的状态栏绘制。
            Log.w("POEM tint listener: " + t.getClass().getSimpleName());
        }
    }

    /**
     * 系统给状态栏算好的前景色。
     *
     * <p>只有在它确实会随应用变化时才有意义 —— 本机实测它恒为白色（不会更新），
     * 所以颜色主要靠 {@link #lightStatusBars()}；返回 0 表示这一步没拿到。
     */
    static int tint() {
        int t = tint;
        // 本机实测该值不会更新：一旦发现它和外观位矛盾，就以外观位为准。
        Boolean light = lightStatusBars;
        if (light != null && t != 0) {
            boolean tintIsLight = ColorResolver.luminance(t) > 0.5f;
            if (tintIsLight == light) {
                return 0;
            }
        }
        return t;
    }

    static Boolean lightStatusBars() {
        return lightStatusBars;
    }

    /**
     * 抄下系统算好的外观位。
     *
     * @param appearance {@code APPEARANCE_LIGHT_STATUS_BARS}=8 表示浅色背景配深色字
     */
    static void setAppearance(int appearance) {
        boolean light = (appearance & APPEARANCE_LIGHT_STATUS_BARS) != 0;
        Boolean old = lightStatusBars;
        if (old != null && old == light) {
            return;
        }
        lightStatusBars = light;
        // 只在真正切换时打一行（应用深浅色切换才会出现），不会刷屏。
        Log.i("POEM appearance light=" + light + " raw=" + appearance);
        notifyChanged();
    }
    /** 直接把系统算好的前景色记下来（只有在它确实会更新时才有意义）。 */
    static void set(int color) {
        if (color == 0 || color == tint) {
            return;
        }
        tint = color;
        notifyChanged();
    }

    static final int APPEARANCE_LIGHT_STATUS_BARS = 8;
}
