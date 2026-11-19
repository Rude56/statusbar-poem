package io.github.rude56.poemstatus;

import java.util.Locale;

public final class ColorResolver {
    public static final int DEFAULT_LIGHT_TEXT = 0xFFFFFFFF;
    public static final int DEFAULT_DARK_TEXT = 0xFF000000;
    public static final int ABSENT = 0;
    public enum Source {TINT, CLOCK, APPEARANCE, MODULE_TEXT, DEFAULT}
    public static final class Result {
        public final int color;
        public final Source source;
        public final boolean lightBackground;
        public Result(int color, Source source, boolean lightBackground) {
            this.color = color;
            this.source = source;
            this.lightBackground = lightBackground;
        }

        public String diagnostic() {
            return "POEM-COLOR src=" + source.name().toLowerCase(Locale.ROOT)
                    + " bg=" + (lightBackground ? "light" : "dark")
                    + " light=" + lightBackground
                    + " fg=#" + String.format("%08X", color);
        }
    }

    private ColorResolver() {
    }

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

    public static boolean isUsableTextColor(int color) {
        return color != ABSENT && ((color >>> 24) & 0xFF) > 0;
    }

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
