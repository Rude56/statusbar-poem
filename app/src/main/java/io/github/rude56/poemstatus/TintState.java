package io.github.rude56.poemstatus;

/**
 * 状态栏前景色缓存：只存系统算好的外观位，不做亮度推断，值变化时推送而不轮询。
 * {@code DarkIconDispatcherImpl.mIconTint} 只在开机时算一次，不能作为主来源。
 */
final class TintState {

    /** 0 表示还没拿到；ARGB，alpha 恒为 0xFF。 */
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

    /** 系统算好的前景色；返回 0 表示这一步没拿到，颜色主要靠 {@link #lightStatusBars()}。 */
    static int tint() {
        int t = tint;
        // 该值与外观位矛盾时以外观位为准（它在部分 ROM 上不随应用更新）。
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

    /** 抄下系统算好的外观位：{@code APPEARANCE_LIGHT_STATUS_BARS}=8 表示浅色背景配深色字。 */
    static void setAppearance(int appearance) {
        boolean light = (appearance & APPEARANCE_LIGHT_STATUS_BARS) != 0;
        Boolean old = lightStatusBars;
        if (old != null && old == light) {
            return;
        }
        lightStatusBars = light;
        Log.i("POEM appearance light=" + light + " raw=" + appearance);
        notifyChanged();
    }
    /** 记下系统算好的前景色（只有它确实会更新时才有意义）。 */
    static void set(int color) {
        if (color == 0 || color == tint) {
            return;
        }
        tint = color;
        notifyChanged();
    }

    static final int APPEARANCE_LIGHT_STATUS_BARS = 8;
}
