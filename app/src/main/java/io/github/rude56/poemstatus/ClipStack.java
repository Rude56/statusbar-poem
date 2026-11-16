package io.github.rude56.poemstatus;

/** 画布裁剪的 save 计数栈。View.draw 可能重入，单一计数会被内层抹掉导致裁剪永久残留。 */
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
