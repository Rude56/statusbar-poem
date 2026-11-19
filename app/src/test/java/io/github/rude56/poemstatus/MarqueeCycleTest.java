package io.github.rude56.poemstatus;

import org.junit.Test;
import static org.junit.Assert.*;

public class MarqueeCycleTest {
    @Test
    public void periodPreservesPoemAndAppendsExactlyTenSpaces() {
        String poem = "长风破浪会有时，直挂云帆济沧海。 —— 李白《行路难》";
        assertEquals(poem + "          ", MarqueeCycle.text(poem));
        assertEquals(poem.length() + 10, MarqueeCycle.text(poem).length());
        assertEquals(1000L, MarqueeCycle.PAUSE_MILLIS);
    }

    @Test
    public void movesAtConfiguredSpeed() {
        assertEquals(12f, MarqueeCycle.advance(0f, 800f, 120, 100f), 0.001f);
        assertEquals(16f, MarqueeCycle.advance(12f, 800f, 240, 1000f / 60f), 0.001f);
    }

    @Test
    public void landsOnNextCopyWithoutOvershootOrBlankFrame() {
        float width = 1279f;
        float position = 0f;
        int frames = 0;
        while (position < width && frames++ < 10000) {
            float next = MarqueeCycle.advance(position, width, 137, 1000f / 60f);
            assertTrue(next >= position);
            assertTrue(next <= width);
            position = next;
        }
        assertTrue(frames < 10000);
        assertEquals(width, position, 0f);
        assertEquals(0f, width - position, 0f);
    }

    @Test
    public void suspendingAppDoesNotJumpThroughAWholePeriod() {
        assertEquals(12f, MarqueeCycle.advance(0f, 800f, 120, 30000f), 0.001f);
        assertEquals(80f, MarqueeCycle.advance(80f, 800f, 120, -10f), 0f);
    }

    @Test
    public void invalidOrDisabledMotionDoesNotAdvance() {
        assertEquals(0f, MarqueeCycle.advance(0f, 0f, 120, 16f), 0f);
        assertEquals(80f, MarqueeCycle.advance(80f, 800f, 0, 16f), 0f);
        assertEquals(80f, MarqueeCycle.advance(80f, 800f, -1, 16f), 0f);
    }
}
