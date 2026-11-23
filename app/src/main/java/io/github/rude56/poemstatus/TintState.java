package io.github.rude56.poemstatus;

final class TintState {
    private static volatile int tint;
    private static volatile Boolean lightStatusBars;
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

            Log.w("POEM tint listener: " + t.getClass().getSimpleName());
        }
    }

    static int tint() {
        int t = tint;

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
    static void setAppearance(int appearance) {
        boolean light = (appearance & APPEARANCE_LIGHT_STATUS_BARS) != 0;
        Boolean old = lightStatusBars;
        if (old != null && old == light) {
            return;
        }
        lightStatusBars = light;

        Log.diag("POEM appearance light=" + light + " raw=" + appearance);
        notifyChanged();
    }

    static void set(int color) {
        if (color == 0 || color == tint) {
            return;
        }
        tint = color;
        notifyChanged();
    }
    static final int APPEARANCE_LIGHT_STATUS_BARS = 8;
}
