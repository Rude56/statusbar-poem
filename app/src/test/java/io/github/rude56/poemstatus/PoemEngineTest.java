package io.github.rude56.poemstatus;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PoemEngineTest {

    private static final class FakeHost implements PoemEngine.Host {
        int startCount;
        int stopCount;
        int restoreCount;
        int scheduleCount;
        int cancelCount;
        boolean frameActive;
        int lastClockSeconds = -1;
        final List<PoemEngine.State> states = new ArrayList<>();
        final List<String> events = new ArrayList<>();
        PoemItem lastPoem;
        String lastText;
        int lastSpeed;

        @Override
        public void startCrawl(PoemItem poem, String displayText, int speedPx) {
            startCount++;
            events.add("start");
            lastPoem = poem;
            lastText = displayText;
            lastSpeed = speedPx;
        }

        @Override
        public void stopCrawl() {
            stopCount++;
            events.add("stop");
        }

        @Override
        public void restoreModules() {
            restoreCount++;
            events.add("restore");
        }

        @Override
        public void scheduleClock(int seconds) {
            scheduleCount++;
            events.add("clock");
            lastClockSeconds = seconds;
        }

        @Override
        public void cancelClock() {
            cancelCount++;
        }

        @Override
        public void setFrameCallbackActive(boolean active) {
            frameActive = active;
        }

        @Override
        public void onStateChanged(PoemEngine.State state) {
            states.add(state);
        }
    }

    private static PoemEngine newEngine(FakeHost host, PoemConfig cfg, List<PoemItem> poems) {
        PoemEngine e = new PoemEngine(host);
        e.configure(cfg, poems);
        return e;
    }

    private static PoemConfig enabledConfig(int clockSeconds) {
        PoemConfig c = new PoemConfig();
        c.enabled = true;
        c.clockSeconds = clockSeconds;
        c.speedPx = 120;
        return c;
    }

    @Test
    public void crawlToClockToCrawlCycle() {
        FakeHost host = new FakeHost();
        PoemConfig cfg = enabledConfig(10);
        PoemEngine e = newEngine(host, cfg, PoemLibrary.enabledOnly(PoemLibrary.defaults()));

        e.tick(false, "");
        assertEquals(PoemEngine.State.CRAWL, e.state());
        assertTrue("爬行阶段必须有帧回调", e.frameCallbackActive());
        assertEquals(1, host.startCount);

        e.onCrawlFinished();
        assertEquals(PoemEngine.State.CLOCK, e.state());
        assertFalse("时间阶段不允许帧回调", e.frameCallbackActive());
        assertTrue("时间阶段前必须恢复系统栏", host.restoreCount >= 1);
        assertEquals(1, host.scheduleCount);
        assertEquals(10, host.lastClockSeconds);

        e.onClockElapsed();
        assertEquals(PoemEngine.State.CRAWL, e.state());
        assertEquals(2, host.startCount);

        int lastRestore = host.events.lastIndexOf("restore");
        int lastStart = host.events.lastIndexOf("start");
        assertTrue("换首前必须已恢复系统栏", lastRestore >= 0 && lastRestore < lastStart);
    }

    @Test
    public void zeroClockStillRestoresThenContinues() {
        FakeHost host = new FakeHost();
        PoemEngine e = newEngine(host, enabledConfig(0), PoemLibrary.enabledOnly(PoemLibrary.defaults()));
        e.tick(false, "");
        int restoreBefore = host.restoreCount;
        e.onCrawlFinished();
        assertEquals(0, host.lastClockSeconds);
        assertTrue(host.restoreCount > restoreBefore);
        assertFalse(e.frameCallbackActive());
        e.onClockElapsed();
        assertEquals(PoemEngine.State.CRAWL, e.state());
    }

    @Test
    public void pauseRestoresAndStopsFrames() {
        FakeHost host = new FakeHost();
        PoemEngine e = newEngine(host, enabledConfig(5), PoemLibrary.enabledOnly(PoemLibrary.defaults()));
        e.tick(false, "");
        assertTrue(e.frameCallbackActive());

        int restoreBefore = host.restoreCount;
        e.tick(true, "screen-off");
        assertEquals(PoemEngine.State.PAUSED, e.state());
        assertFalse(e.frameCallbackActive());
        assertEquals("屏幕熄灭必须恢复系统栏", restoreBefore + 1, host.restoreCount);
        assertTrue(host.stopCount >= 1);
        assertEquals("screen-off", e.lastPauseReason());
    }

    @Test
    public void pauseDuringClockCancelsTimer() {
        FakeHost host = new FakeHost();
        PoemEngine e = newEngine(host, enabledConfig(30), PoemLibrary.enabledOnly(PoemLibrary.defaults()));
        e.tick(false, "");
        e.onCrawlFinished();
        assertEquals(PoemEngine.State.CLOCK, e.state());
        int cancelBefore = host.cancelCount;
        e.tick(true, "landscape");
        assertEquals(PoemEngine.State.PAUSED, e.state());
        assertTrue(host.cancelCount > cancelBefore);
        assertFalse(e.frameCallbackActive());
    }

    @Test
    public void resumeFromPauseStartsAgain() {
        FakeHost host = new FakeHost();
        PoemEngine e = newEngine(host, enabledConfig(5), PoemLibrary.enabledOnly(PoemLibrary.defaults()));
        e.tick(false, "");
        e.tick(true, "whitelist:com.x");
        assertEquals(PoemEngine.State.PAUSED, e.state());
        e.tick(false, "");
        assertEquals(PoemEngine.State.CRAWL, e.state());
        assertEquals(2, host.startCount);
    }

    @Test
    public void disabledModuleNeverStarts() {
        FakeHost host = new FakeHost();
        PoemConfig cfg = enabledConfig(5);
        cfg.enabled = false;
        PoemEngine e = newEngine(host, cfg, PoemLibrary.enabledOnly(PoemLibrary.defaults()));
        e.tick(false, "");
        assertEquals(0, host.startCount);
        assertEquals(PoemEngine.State.PAUSED, e.state());
        assertFalse(e.frameCallbackActive());
    }

    @Test
    public void emptyLibraryNeverStarts() {
        FakeHost host = new FakeHost();
        PoemEngine e = newEngine(host, enabledConfig(5), new ArrayList<PoemItem>());
        e.tick(false, "");
        assertEquals(0, host.startCount);
        assertFalse(e.frameCallbackActive());
    }

    @Test
    public void clockStateHasNoFrameCallbackAfterManyTicks() {
        FakeHost host = new FakeHost();
        PoemEngine e = newEngine(host, enabledConfig(60), PoemLibrary.enabledOnly(PoemLibrary.defaults()));
        e.tick(false, "");
        e.onCrawlFinished();
        for (int i = 0; i < 50; i++) {
            e.tick(false, "");
        }
        assertEquals("时间阶段重复 tick 不应重新开始", 1, host.startCount);
        assertFalse(e.frameCallbackActive());
        assertEquals(PoemEngine.State.CLOCK, e.state());
    }

    @Test
    public void hideAuthorAndSourceChangesDisplayText() {
        FakeHost host = new FakeHost();
        PoemConfig cfg = enabledConfig(5);
        cfg.showAuthor = false;
        cfg.showSource = false;
        PoemEngine e = newEngine(host, cfg, PoemLibrary.enabledOnly(PoemLibrary.defaults()));
        e.tick(false, "");
        assertEquals(e.currentPoem().text, host.lastText);
        assertFalse(host.lastText.contains("——"));
        assertFalse(host.lastText.contains("《"));
    }

    @Test
    public void stopClearsPlayback() {
        FakeHost host = new FakeHost();
        PoemEngine e = newEngine(host, enabledConfig(5), PoemLibrary.enabledOnly(PoemLibrary.defaults()));
        e.tick(false, "");
        e.stop("view-unavailable");
        assertEquals(PoemEngine.State.IDLE, e.state());
        assertFalse(e.frameCallbackActive());
    }
}
