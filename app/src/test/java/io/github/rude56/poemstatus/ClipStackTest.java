package io.github.rude56.poemstatus;

import org.junit.Test;

import static org.junit.Assert.*;

/** 画布裁剪栈：push/pop 必须严格后进先出。View.draw 可能重入，单一计数会被内层抹掉，裁剪会永久残留。 */
public class ClipStackTest {

    @Test
    public void pushPopIsStrictlyNested() {
        ClipStack s = new ClipStack();
        assertEquals(0, s.depth());
        assertTrue(s.push(10));
        assertTrue(s.push(20));
        assertEquals(2, s.depth());
        assertEquals("后进先出", 20, s.pop());
        assertEquals(10, s.pop());
        assertEquals(0, s.depth());
    }

    /** 空栈出栈必须安全返回 -1（调用方据此不 restore）。 */
    @Test
    public void popOnEmptyIsSafe() {
        ClipStack s = new ClipStack();
        assertEquals(-1, s.pop());
        assertEquals(-1, s.pop());
        assertEquals(0, s.depth());
    }

    /** 重入场景：两次 push、两次 pop，两个保存点都必须拿得回来。 */
    @Test
    public void reentrantPushKeepsOuterSavePoint() {
        ClipStack s = new ClipStack();
        assertTrue(s.push(100));    // 外层 beginClip
        assertTrue(s.push(200));    // 内层重入 beginClip
        assertEquals("内层先还原", 200, s.pop());
        assertEquals("外层保存点必须还在", 100, s.pop());
        assertEquals("栈必须干净", 0, s.depth());
    }

    /** 深度超限时 push 返回 false，调用方据此放弃这一层裁剪（而不是破坏栈）。 */
    @Test
    public void exceedingDepthIsRejectedWithoutCorruption() {
        ClipStack s = new ClipStack();
        for (int i = 0; i < ClipStack.MAX_DEPTH; i++) {
            assertTrue("第" + i + "层应成功", s.push(i));
        }
        assertFalse("超限必须拒绝", s.push(999));
        assertEquals("拒绝后深度不变", ClipStack.MAX_DEPTH, s.depth());
        // 仍然能按 LIFO 全部取回。
        for (int i = ClipStack.MAX_DEPTH - 1; i >= 0; i--) {
            assertEquals(i, s.pop());
        }
        assertEquals(0, s.depth());
    }

    /** clear() 丢弃所有保存点（停止播放时用），之后 pop 返回 -1。 */
    @Test
    public void clearDropsEverything() {
        ClipStack s = new ClipStack();
        s.push(1);
        s.push(2);
        s.clear();
        assertEquals(0, s.depth());
        assertEquals(-1, s.pop());
        // clear 之后仍可继续正常使用。
        assertTrue(s.push(7));
        assertEquals(7, s.pop());
    }

    /** 反复 push/pop 不得残留状态（帧循环里每秒跑 30 次，泄漏会累积）。 */
    @Test
    public void repeatedCyclesLeaveNoResidue() {
        ClipStack s = new ClipStack();
        for (int i = 0; i < 10_000; i++) {
            assertTrue(s.push(i));
            assertEquals(i, s.pop());
        }
        assertEquals(0, s.depth());
    }
}
