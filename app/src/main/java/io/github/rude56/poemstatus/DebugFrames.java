package io.github.rude56.poemstatus;

/**
 * 诊断闸门：每首诗最多打印<b>一帧</b>「虫洞正在传送」的遮挡详情。
 *
 * <p>「每首一次」是刻意的：既保证每首诗都能被观察，又绝不会刷屏。
 *
 * <p><b>不是「第一帧」</b>：诗句刚进屏幕时还没有任何字穿过虫洞，
 * 那一帧的遮挡与一个普通矩形完全一致，看不出任何问题。
 * 真正的偏差只出现在「有字跨过洞口、分段变成两段」之后 ——
 * 所以闸门必须等到那一刻才消耗（见 {@code HolePuncher.beginClip} 的
 * {@code segCount > 1} 判据），否则每首诗打印的都是没信息量的那一帧。
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
