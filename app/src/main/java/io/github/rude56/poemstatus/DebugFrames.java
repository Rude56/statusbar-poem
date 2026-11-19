package io.github.rude56.poemstatus;

final class DebugFrames {
    private static boolean remaining = true;
    private DebugFrames() {
    }
    static void reset() {
        remaining = true;
    }

    static boolean pending() {
        return remaining;
    }

    static boolean consume() {
        if (!remaining) {
            return false;
        }
        remaining = false;
        return true;
    }
}
