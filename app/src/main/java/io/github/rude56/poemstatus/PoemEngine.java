package io.github.rude56.poemstatus;

import java.util.List;

public final class PoemEngine {
    public enum State {IDLE, CRAWL, CLOCK, PAUSED}
    public interface Host {
        void startCrawl(PoemItem poem, String displayText, int speedPx);
        void stopCrawl();
        void restoreModules();
        void scheduleClock(int seconds);

        void cancelClock();

        void setFrameCallbackActive(boolean active);

        void onStateChanged(State state);
    }

    private final Host host;
    private PoemConfig config = new PoemConfig();
    private List<PoemItem> library;
    private String librarySignature = "";
    private PoemLibrary.Playback playback;
    private State state = State.IDLE;
    private boolean frameCallbackActive = false;
    private PoemItem current;
    private String currentDisplayText = "";
    private String lastPauseReason = "";
    private long startedCount = 0;

    public PoemEngine(Host host) {
        this.host = host;
    }

    public State state() {
        return state;
    }

    public boolean frameCallbackActive() {
        return frameCallbackActive;
    }

    public PoemItem currentPoem() {
        return current;
    }

    public String currentDisplayText() {
        return currentDisplayText;
    }

    public String lastPauseReason() {
        return lastPauseReason;
    }

    public long startedCount() {
        return startedCount;
    }

    public void configure(PoemConfig cfg, List<PoemItem> enabledPoems) {
        PoemConfig next = cfg == null ? new PoemConfig() : cfg;
        next.normalized();
        boolean orderChanged = !next.orderMode.equals(config.orderMode);
        boolean libChanged = !signature(enabledPoems).equals(librarySignature);
        config = next;
        library = enabledPoems;
        if (orderChanged || libChanged) {
            librarySignature = signature(enabledPoems);
            playback = new PoemLibrary.Playback(enabledPoems, next.orderMode, next.configRevision);
            if (state == State.CRAWL || state == State.CLOCK) {

                enterIdle();
            }
        } else if (playback == null) {
            playback = new PoemLibrary.Playback(enabledPoems, next.orderMode, next.configRevision);
        }
    }

    private static String signature(List<PoemItem> list) {
        StringBuilder sb = new StringBuilder();
        if (list != null) {
            for (PoemItem p : list) {
                sb.append(p.id).append(',').append(p.enabled ? '1' : '0').append(';');
            }
        }
        return sb.toString();
    }

    public void tick(boolean shouldPause, String reason) {
        if (shouldPause) {
            pause(reason);
            return;
        }
        if (!config.enabled) {
            pause("disabled");
            return;
        }
        if (library == null || library.isEmpty()) {
            pause("empty-library");
            return;
        }
        if (state == State.PAUSED) {

            enterIdle();
        }
        if (state == State.IDLE) {
            startNext();
        }
    }

    public void pause(String reason) {
        lastPauseReason = reason == null ? "" : reason;
        boolean changed = state != State.PAUSED;
        if (state == State.CRAWL) {
            host.stopCrawl();
        }
        if (state == State.CLOCK) {
            host.cancelClock();
        }

        if (changed) {
            host.restoreModules();
        }
        setFrameActive(false);
        state = State.PAUSED;
        if (changed) {
            host.onStateChanged(state);
        }
    }

    public void stop(String reason) {
        lastPauseReason = reason == null ? "" : reason;
        if (state == State.CRAWL) {
            host.stopCrawl();
        }
        if (state == State.CLOCK) {
            host.cancelClock();
        }
        host.restoreModules();
        setFrameActive(false);
        state = State.IDLE;
        playback = null;
        host.onStateChanged(state);
    }

    private void enterIdle() {
        if (state == State.CRAWL) {
            host.stopCrawl();
        }
        if (state == State.CLOCK) {
            host.cancelClock();
        }
        host.restoreModules();
        setFrameActive(false);
        state = State.IDLE;
        host.onStateChanged(state);
    }

    public void onCrawlFinished() {
        if (state != State.CRAWL) {
            return;
        }
        setFrameActive(false);
        host.stopCrawl();
        host.restoreModules();
        state = State.CLOCK;
        host.onStateChanged(state);
        int seconds = PoemConfig.clamp(config.clockSeconds, PoemConfig.CLOCK_MIN, PoemConfig.CLOCK_MAX);
        host.scheduleClock(seconds);
    }

    public void onClockElapsed() {
        if (state != State.CLOCK) {
            return;
        }
        if (!config.enabled || library == null || library.isEmpty()) {
            enterIdle();
            return;
        }
        state = State.IDLE;
        host.onStateChanged(state);
        startNext();
    }

    private void startNext() {
        if (playback == null) {
            playback = new PoemLibrary.Playback(library, config.orderMode, config.configRevision);
        }
        PoemItem poem = playback.next();
        if (poem == null) {
            enterIdle();
            return;
        }
        String text = poem.displayText(config.showAuthor, config.showSource);
        if (text.isEmpty()) {
            enterIdle();
            return;
        }
        current = poem;
        currentDisplayText = text;
        startedCount++;
        state = State.CRAWL;
        setFrameActive(true);
        host.startCrawl(poem, text, config.speedPx);
        host.onStateChanged(state);
    }

    private void setFrameActive(boolean active) {
        if (frameCallbackActive != active) {
            frameCallbackActive = active;
            host.setFrameCallbackActive(active);
        }
    }
}
