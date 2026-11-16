package io.github.rude56.poemstatus;

/**
 * 画布裁剪的 save 计数栈（纯逻辑，可在 JVM 里直接测）。
 *
 * <p><b>为什么需要一个栈而不是一个 int</b>：{@code HolePuncher} 在
 * {@code View.draw()} 里做 {@code save()} / {@code clipOutRect()} / {@code restore()}。
 * 以前只用一个 {@code savedClips} 计数，并且每次 {@code beginClip} 都先把它清零。
 * 一旦 {@code draw} 被<b>重入</b>（同一个 View 在一次绘制里被调用两次，
 * 例如带硬件层的子树、或者某些 ROM 的预绘制流程），内层会把外层的计数抹掉：
 * <pre>
 *   beginClip  → savedClips = 0，然后 = 1
 *   beginClip  → savedClips = 0，然后 = 1   ← 外层的 1 被抹掉
 *   endClip    → restore 一次，savedClips = 0
 *   endClip    → 看到 0，直接 return，<b>不再 restore</b>
 * </pre>
 * 结果就是画布裁剪<b>永久留在</b>了内容根上 —— 状态栏图标被永久挖掉一块，
 * 而且没有任何日志，只能重启 SystemUI 才能恢复。
 *
 * <p>所以改成真正的栈：入栈记下这一层的 saveCount，出栈时还原到该层。
 * 深度固定上限（绘制嵌套不会很深），数组复用、零分配。
 */
final class ClipStack {

    /** 嵌套深度上限。真实绘制嵌套远小于此值，超出时忽略多余层（安全降级为不裁剪）。 */
    static final int MAX_DEPTH = 8;

    private final int[] stack = new int[MAX_DEPTH];
    private int depth;

    /** 当前嵌套层数。 */
    int depth() {
        return depth;
    }

    /**
     * 入栈。
     *
     * @param saveCount 这一层开始时 {@code canvas.save()} 的返回值
     * @return true 表示入栈成功；false 表示已到深度上限（调用方应放弃这一层裁剪）
     */
    boolean push(int saveCount) {
        if (depth >= MAX_DEPTH) {
            return false;
        }
        stack[depth++] = saveCount;
        return true;
    }

    /**
     * 出栈并取出应当 restore 到的 saveCount。
     *
     * @return 需要 {@code canvas.restoreToCount()} 的值；{@code -1} 表示栈已空
     */
    int pop() {
        if (depth <= 0) {
            return -1;
        }
        return stack[--depth];
    }

    /** 清空（停止播放时用；不负责 restore 画布，那必须由绘制路径自己配对完成）。 */
    void clear() {
        depth = 0;
    }
}
