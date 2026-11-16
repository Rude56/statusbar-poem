package io.github.rude56.poemstatus;

import java.util.Locale;

/**
 * 诗句文字颜色的决策逻辑（纯逻辑，不触碰 Android API）：颜色完全来自系统已算好的结果，不做亮度推断。
 * 优先级 TINT → CLOCK → APPEARANCE → MODULE_TEXT → DEFAULT；只在低频路径采样。
 */
public final class ColorResolver {

    public static final int DEFAULT_LIGHT_TEXT = 0xFFFFFFFF;
    public static final int DEFAULT_DARK_TEXT = 0xFF000000;

    /** 颜色值的「读不到」哨兵：0 表示全透明，不可能是合法文字色。 */
    public static final int ABSENT = 0;

    public enum Source {TINT, CLOCK, APPEARANCE, MODULE_TEXT, DEFAULT}

    /** 一次决策的完整结果。 */
    public static final class Result {
        public final int color;
        public final Source source;
        public final boolean lightBackground;

        public Result(int color, Source source, boolean lightBackground) {
            this.color = color;
            this.source = source;
            this.lightBackground = lightBackground;
        }

        /** 诊断行格式：{@code POEM-COLOR src=tint bg=dark light=false fg=#FFFFFFFF} */
        public String diagnostic() {
            return "POEM-COLOR src=" + source.name().toLowerCase(Locale.ROOT)
                    + " bg=" + (lightBackground ? "light" : "dark")
                    + " light=" + lightBackground
                    + " fg=#" + String.format("%08X", color);
        }
    }

    private ColorResolver() {
    }

    /** 颜色决策：{@code iconTint} 为 {@link #ABSENT} 表示还没拿到，{@code lightStatusBars} 为 {@code null} 同理。 */
    public static Result resolve(int iconTint, int clockTextColor,
                                 Boolean lightStatusBars, int moduleTextColor) {
        if (isUsableTextColor(iconTint)) {
            return new Result(iconTint, Source.TINT, isLightBackgroundFor(iconTint));
        }
        if (isUsableTextColor(clockTextColor)) {
            return new Result(clockTextColor, Source.CLOCK, isLightBackgroundFor(clockTextColor));
        }
        if (lightStatusBars != null) {
            boolean light = lightStatusBars;
            return new Result(light ? DEFAULT_DARK_TEXT : DEFAULT_LIGHT_TEXT,
                    Source.APPEARANCE, light);
        }
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

    /** 由「系统当前使用的文字色」反推背景明暗：系统给浅色背景配深色字，反之亦然。 */
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
