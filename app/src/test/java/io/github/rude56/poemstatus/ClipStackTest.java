package io.github.rude56.poemstatus;

import org.junit.Test;

import static org.junit.Assert.*;

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

    @Test
    public void popOnEmptyIsSafe() {
        ClipStack s = new ClipStack();
        assertEquals(-1, s.pop());
        assertEquals(-1, s.pop());
        assertEquals(0, s.depth());
    }

    @Test
    public void reentrantPushKeepsOuterSavePoint() {
        ClipStack s = new ClipStack();
        assertTrue(s.push(100));
        assertTrue(s.push(200));
        assertEquals("内层先还原", 200, s.pop());
        assertEquals("外层保存点必须还在", 100, s.pop());
        assertEquals("栈必须干净", 0, s.depth());
    }
    @Test
    public void exceedingDepthIsRejectedWithoutCorruption() {
        ClipStack s = new ClipStack();
        for (int i = 0; i < ClipStack.MAX_DEPTH; i++) {
            assertTrue("第" + i + "层应成功", s.push(i));
        }
        assertFalse("超限必须拒绝", s.push(999));
        assertEquals("拒绝后深度不变", ClipStack.MAX_DEPTH, s.depth());

        for (int i = ClipStack.MAX_DEPTH - 1; i >= 0; i--) {
            assertEquals(i, s.pop());
        }
        assertEquals(0, s.depth());
    }

    @Test
    public void clearDropsEverything() {
        ClipStack s = new ClipStack();
        s.push(1);
        s.push(2);
        s.clear();
        assertEquals(0, s.depth());
        assertEquals(-1, s.pop());

        assertTrue(s.push(7));
        assertEquals(7, s.pop());
    }

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
