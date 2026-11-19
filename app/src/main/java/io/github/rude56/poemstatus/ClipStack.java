package io.github.rude56.poemstatus;

final class ClipStack {
    static final int MAX_DEPTH = 8;
    private final int[] stack = new int[MAX_DEPTH];
    private int depth;
    int depth() {
        return depth;
    }
    boolean push(int saveCount) {
        if (depth >= MAX_DEPTH) {
            return false;
        }
        stack[depth++] = saveCount;
        return true;
    }

    int pop() {
        if (depth <= 0) {
            return -1;
        }
        return stack[--depth];
    }

    void clear() {
        depth = 0;
    }
}
