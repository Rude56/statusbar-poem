package io.github.rude56.poemstatus;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FramePacerTest {
    @Test
    public void fpsFollowsSpeedDividedByEight() {
        assertEquals(30, FramePacer.fpsForSpeed(240));
        assertEquals(35, FramePacer.fpsForSpeed(280));
        assertEquals(60, FramePacer.fpsForSpeed(480));
    }

    @Test
    public void fpsIsClampedBetweenThirtyAndSixty() {
        assertEquals(FramePacer.MIN_FPS, FramePacer.fpsForSpeed(40));
        assertEquals(FramePacer.MIN_FPS, FramePacer.fpsForSpeed(0));
        assertEquals(FramePacer.MIN_FPS, FramePacer.fpsForSpeed(-5));
        assertEquals(FramePacer.MAX_FPS, FramePacer.fpsForSpeed(480));
        assertEquals(FramePacer.MAX_FPS, FramePacer.fpsForSpeed(6000));
    }

    @Test
    public void intervalMatchesFps() {
        assertEquals(1_000_000_000L / 30, FramePacer.intervalNsForSpeed(40));
        assertEquals(1_000_000_000L / 60, FramePacer.intervalNsForSpeed(480));
        assertEquals(1_000_000_000L / 35, FramePacer.intervalNsForSpeed(280));
    }

    @Test
    public void fasterSpeedNeverWakesLessOften() {
        long prev = FramePacer.intervalNsForSpeed(FramePacer.MIN_FPS);
        for (int speed = PoemConfig.SPEED_MIN; speed <= PoemConfig.SPEED_MAX; speed++) {
            long cur = FramePacer.intervalNsForSpeed(speed);
            assertTrue("speed=" + speed + " 的间隔不应大于更慢速度的间隔", cur <= prev);
            prev = cur;
        }
    }

    @Test
    public void speedRangeCoversWholeClampBand() {
        assertEquals(40, PoemConfig.SPEED_MIN);
        assertEquals(480, PoemConfig.SPEED_MAX);
        assertTrue("默认速度必须落在范围内",
                PoemConfig.SPEED_DEFAULT >= PoemConfig.SPEED_MIN
                        && PoemConfig.SPEED_DEFAULT <= PoemConfig.SPEED_MAX);
    }
}
