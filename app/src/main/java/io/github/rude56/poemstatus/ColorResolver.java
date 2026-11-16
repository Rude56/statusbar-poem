package io.github.rude56.poemstatus;

import java.util.Locale;

/**
 * 诗句文字颜色的决策逻辑（纯逻辑，不触碰 Android API）。
 *
 * <p><b>颜色完全来自系统已经算好的结果</b>，我们自己不猜、不做亮度推断：
 * <ol>
 *   <li>{@link Source#TINT} 系统给状态栏图标/文字算好的前景色
 *       （{@code DarkIconDispatcherImpl.mIconTint}）—— 最准，深浅色应用自动跟随；</li>
 *   <li>{@link Source#CLOCK} 原生时钟当前的文字色（ROM 保留 TextView 时钟时）；</li>
 *   <li>{@link Source#APPEARANCE} 系统状态栏外观位
 *       （{@code APPEARANCE_LIGHT_STATUS_BARS}）—— 浅色背景配深字；</li>
 *   <li>{@link Source#MODULE_TEXT} 状态栏里任一可见文字控件的当前文字色；</li>
 *   <li>{@link Source#DEFAULT} 以上都拿不到时的兜底浅色。</li>
 * </ol>
 *
 * <p>采样只允许发生在低频路径（每首开始时 / 爬行期间每 300ms），
 * 绝不允许出现在逐帧动画函数里 —— 帧循环只使用这里缓存下来的颜色。
 */
public final class ColorResolver {

    public static final int DEFAULT_LIGHT_TEXT = 0xFFFFFFFF;
    public static final int DEFAULT_DARK_TEXT = 0xFF000000;

    /** 颜色值的「读不到」哨兵：0 表示全透明，不可能是合法文字色。 */
    public static final int ABSENT = 0;

    public enum Source {TINT, CLOCK, APPEARANCE, MODULE_TEXT, DEFAULT}

    /** 一次决策的完整结果，带诊断字段。 */
    public static final class Result {
        public final int color;
        public final Source source;
        public final boolean lightBackground;

        public Result(int color, Source source, boolean lightBackground) {
            this.color = color;
            this.source = source;
            this.lightBackground = lightBackground;
        }

        /** POEM-COLOR src=tint bg=dark light=false fg=#FFFFFFFF */
        public String diagnostic() {
            return "POEM-COLOR src=" + source.name().toLowerCase(Locale.ROOT)
                    + " bg=" + (lightBackground ? "light" : "dark")
                    + " light=" + lightBackground
                    + " fg=#" + String.format("%08X", color);
        }
    }

    private ColorResolver() {
    }

    /**
     * 颜色决策。
     *
     * @param iconTint        系统给状态栏算好的前景色；{@link #ABSENT} 表示还没拿到
     * @param clockTextColor  原生时钟当前文字色；{@link #ABSENT} 表示读不到
     * @param lightStatusBars 系统外观位：true=浅色背景配深色字；{@code null}=还没拿到
     * @param moduleTextColor 状态栏内任一文字控件的当前文字色；{@link #ABSENT} 表示读不到
     */
    public static Result resolve(int iconTint, int clockTextColor,
                                 Boolean lightStatusBars, int moduleTextColor) {
        // 1) 系统算好的前景色：直接拿来用，不做任何反色推断。
        if (isUsableTextColor(iconTint)) {
            return new Result(iconTint, Source.TINT, isLightBackgroundFor(iconTint));
        }
        // 2) 原生时钟正在用的文字色。
        if (isUsableTextColor(clockTextColor)) {
            return new Result(clockTextColor, Source.CLOCK, isLightBackgroundFor(clockTextColor));
        }
        // 3) 系统外观位。
        if (lightStatusBars != null) {
            boolean light = lightStatusBars;
            return new Result(light ? DEFAULT_DARK_TEXT : DEFAULT_LIGHT_TEXT,
                    Source.APPEARANCE, light);
        }
        // 4) 状态栏内其它文字控件。
        if (isUsableTextColor(moduleTextColor)) {
            return new Result(moduleTextColor, Source.MODULE_TEXT,
                    isLightBackgroundFor(moduleTextColor));
        }
        return new Result(DEFAULT_LIGHT_TEXT, Source.DEFAULT, false);
    }

    /** 文字色是否可用：alpha 必须非零。 */
    public static boolean isUsableTextColor(int color) {
        return color != ABSENT && ((color >>> 24) & 0xFF) > 0;
    }

    /**
     * 由「系统当前使用的文字色」直接反推背景明暗：
     * 系统给浅色背景配深色字，给深色背景配浅色字。
     */
    public static boolean isLightBackgroundFor(int textColor) {
        return luminance(textColor) < 0.5f;
    }

    public static float luminance(int color) {
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        return 0.299f * r + 0.587f * g + 0.114f * b;
    }
}
