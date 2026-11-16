package io.github.rude56.poemstatus;

/**
 * 诊断闸门：每首诗最多打印一帧「虫洞正在传送」的遮挡详情，既保证每首诗都能被观察，又不会刷屏。
 * 闸门只在「有字跨过洞口、分段变成两段」之后才消耗（见 {@code HolePuncher.beginClip} 的 {@code segCount > 1} 判据）。
 */
final class DebugFrames {

    private static boolean remaining = true;

    private DebugFrames() {
    }

    /** 重置：每首开始时调用，允许再打印一帧。 */
    static void reset() {
        remaining = true;
    }

    /** 只查询是否还允许打印，不消耗。 */
    static boolean pending() {
        return remaining;
    }

    /** 返回 true 表示这一帧可以打印（并就此耗尽本首的名额）。 */
    static boolean consume() {
        if (!remaining) {
            return false;
        }
        remaining = false;
        return true;
    }
}
