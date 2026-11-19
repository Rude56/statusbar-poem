package io.github.rude56.poemstatus;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FramePacerTest {
    @Test
    public void targetFrameIsAbout33ms() {
        assertEquals(30, FramePacer.TARGET_FPS);
        assertEquals(33_333_333L, FramePacer.TARGET_FRAME_NS);
        assertTrue(FramePacer.TARGET_FRAME_NS * 30L <= 1_000_000_000L);
    }
    @Test
    public void intervalGrowsAtLowSpeedAndCapsAtThirtyFps() {

        assertEquals(FramePacer.TARGET_FRAME_NS, FramePacer.intervalNsForSpeed(600));
        assertEquals(FramePacer.TARGET_FRAME_NS, FramePacer.intervalNsForSpeed(120));

        assertEquals(FramePacer.TARGET_FRAME_NS, FramePacer.intervalNsForSpeed(60));
        assertEquals(FramePacer.TARGET_FRAME_NS, FramePacer.intervalNsForSpeed(30));

        assertTrue("低速必须放宽", FramePacer.intervalNsForSpeed(20) >= 50_000_000L);
        assertTrue("低速不得被压回 30fps",
                FramePacer.intervalNsForSpeed(20) > FramePacer.TARGET_FRAME_NS);
    }
    @Test
    public void intervalIsBoundedAndSafeForBadSpeed() {
        assertEquals(FramePacer.MAX_ADAPTIVE_INTERVAL_NS, FramePacer.intervalNsForSpeed(1));
        assertTrue(FramePacer.intervalNsForSpeed(1) <= FramePacer.MAX_ADAPTIVE_INTERVAL_NS);
        assertEquals(FramePacer.TARGET_FRAME_NS, FramePacer.intervalNsForSpeed(0));
        assertEquals(FramePacer.TARGET_FRAME_NS, FramePacer.intervalNsForSpeed(-5));
    }

    @Test
    public void slowerSpeedNeverWakesMoreOften() {
        long prev = FramePacer.intervalNsForSpeed(1);
        for (int speed = 2; speed <= 600; speed++) {
            long cur = FramePacer.intervalNsForSpeed(speed);
            assertTrue("speed=" + speed + " 的间隔不应小于更慢速度的间隔", cur <= prev);
            prev = cur;
        }
    }
}
