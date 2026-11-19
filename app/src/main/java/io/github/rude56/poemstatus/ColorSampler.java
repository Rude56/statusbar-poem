package io.github.rude56.poemstatus;

import android.view.View;
import android.widget.TextView;

final class ColorSampler {
    /** 只有 Hook 完全拿不到 tint 时才降级到这个兜底轮询间隔。 */
    private static final long SAMPLE_INTERVAL_MS = 2000L;
    /** 系统图标色切换是带动画的，回调必须合流，否则等于每帧取一次色。 */
    private static final long COALESCE_MS = 250L;
    private static final long DIAG_LOG_MS = 300_000L;
    private final android.os.Handler handler =
            new android.os.Handler(android.os.Looper.getMainLooper());
    private int cachedColor = ColorResolver.DEFAULT_LIGHT_TEXT;
    private String lastDiagnostic = "";
    private ColorResolver.Source lastLoggedSource = null;
    private long lastLogAt;
    private View containerRef;
    private Runnable onChange;
    private java.lang.ref.WeakReference<View> clockRef;
    private java.lang.ref.WeakReference<TextView> textRef;
    private boolean polling;
    private boolean pendingSample;

    int color() {
        return cachedColor;
    }
    boolean sample(View statusContainer, View contentRoot) {
        containerRef = statusContainer;
        int tint = TintState.tint();
        int clockColor = readClockColor(contentRoot);
        int moduleColor = readAnyModuleTextColor(contentRoot);
        ColorResolver.Result r = ColorResolver.resolve(
                tint, clockColor, readLightStatusBars(statusContainer), moduleColor);
        boolean changed = r.color != cachedColor;
        cachedColor = r.color;
        long now = android.os.SystemClock.elapsedRealtime();
        if (r.source != lastLoggedSource || now - lastLogAt > DIAG_LOG_MS) {
            lastLoggedSource = r.source;
            lastLogAt = now;
            if (!r.diagnostic().equals(lastDiagnostic)) {
                lastDiagnostic = r.diagnostic();
                Log.i(lastDiagnostic);
            }
        } else {
            lastDiagnostic = r.diagnostic();
        }
        return changed;
    }

    void onSystemTintChanged() {
        if (pendingSample) {
            return;
        }
        View container = containerRef;
        if (container == null || !container.isAttachedToWindow()) {
            return;
        }
        pendingSample = true;
        handler.postDelayed(sampleRunnable, COALESCE_MS);
    }

    private final Runnable sampleRunnable = new Runnable() {
        @Override
        public void run() {
            pendingSample = false;
            View container = containerRef;
            if (container == null || !container.isAttachedToWindow()) {
                return;
            }
            View root = ViewTree.findContentRoot(container);
            if (root == null) {
                return;
            }
            if (sample(container, root) && onChange != null) {
                onChange.run();
            }
        }
    };

    private static Boolean readLightStatusBars(View statusContainer) {
        Boolean hooked = TintState.lightStatusBars();
        if (hooked != null) {
            return hooked;
        }
        try {
            if (statusContainer == null) {
                return null;
            }
            android.view.WindowInsetsController wic = statusContainer.getWindowInsetsController();
            if (wic == null) {
                return null;
            }
            int appearance = wic.getSystemBarsAppearance();
            if (appearance == 0) {

                return null;
            }
            return (appearance & TintState.APPEARANCE_LIGHT_STATUS_BARS) != 0;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private int readClockColor(View contentRoot) {
        try {
            View clock = clockRef == null ? null : clockRef.get();
            if (clock == null) {
                clock = ViewTree.findClock(contentRoot);
                if (clock != null) {
                    clockRef = new java.lang.ref.WeakReference<>(clock);
                }
            }
            if (clock instanceof TextView) {
                return ((TextView) clock).getCurrentTextColor();
            }
        } catch (Throwable ignored) {

        }
        return ColorResolver.ABSENT;
    }

    private int readAnyModuleTextColor(View contentRoot) {
        try {
            TextView hit = textRef == null ? null : textRef.get();
            if (hit == null || !hit.isAttachedToWindow()) {
                hit = ViewTree.findFirstText(contentRoot);
                if (hit != null) {
                    textRef = new java.lang.ref.WeakReference<>(hit);
                }
            }
            if (hit != null) {
                return hit.getCurrentTextColor();
            }
        } catch (Throwable ignored) {

        }
        return ColorResolver.ABSENT;
    }

    void startTracking(Runnable changed) {
        onChange = changed;
        TintState.setListener(this::onSystemTintChanged);
        if (TintState.lightStatusBars() == null && TintState.tint() == 0) {
            startFallbackPolling();
        }
    }

    void stopTracking() {
        TintState.setListener(null);
        onChange = null;
        pendingSample = false;
        stopFallbackPolling();
    }
    private void startFallbackPolling() {
        if (polling) {
            return;
        }
        polling = true;
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!polling) {
                    return;
                }
                View container = containerRef;
                if (container == null) {
                    polling = false;
                    return;
                }
                if (TintState.lightStatusBars() != null || TintState.tint() != 0) {
                    polling = false;
                    return;
                }
                if (sample(container, ViewTree.findContentRoot(container)) && onChange != null) {
                    onChange.run();
                }
                handler.postDelayed(this, SAMPLE_INTERVAL_MS);
            }
        }, SAMPLE_INTERVAL_MS);
    }

    private void stopFallbackPolling() {
        polling = false;
        handler.removeCallbacksAndMessages(null);
    }
}
