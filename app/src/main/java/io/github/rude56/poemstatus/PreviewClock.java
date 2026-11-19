package io.github.rude56.poemstatus;

public final class PreviewClock {
    public static final long CYCLE_NANOS = 14_000_000_000L;
    private PreviewClock() {
    }
    public static float progressAt(long elapsedNanos, long cycleNanos) {
        if (cycleNanos <= 0L) {
            return 0f;
        }
        long e = elapsedNanos % cycleNanos;
        if (e < 0L) {
            e += cycleNanos;
        }
        return (float) e / (float) cycleNanos;
    }
    public static float cycleEnd(float canvasWidth, float textWidth, float wormholeWidth) {
        if (wormholeWidth <= 0f) {
            return 1f;
        }
        float end = progressAtFinish(canvasWidth, textWidth, wormholeWidth);

        if (end <= 0.02f) {
            return 0.02f;
        }
        return Math.min(end, 1f);
    }

    public static float trackLeft(float canvasWidth, float textWidth, float progress) {
        float span = canvasWidth + textWidth;
        return canvasWidth - span * progress;
    }
    public static float progressAtFinish(float canvasWidth, float textWidth, float wormholeWidth) {
        float span = canvasWidth + textWidth;
        if (span <= 0f) {
            return 1f;
        }
        float finishLeft = -textWidth + wormholeWidth;
        return (canvasWidth - finishLeft) / span;
    }
}
