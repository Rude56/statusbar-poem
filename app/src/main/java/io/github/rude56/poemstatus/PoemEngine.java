package io.github.rude56.poemstatus;

import java.util.List;

/**
 * 播放状态机（纯逻辑，不引用任何 android.* 类型，可在 JVM 里直接测）：IDLE → CRAWL → CLOCK → CRAWL…，任一状态都可切到 PAUSED。
 * 进入 CLOCK / PAUSED / IDLE 必须停帧回调并恢复所有状态栏模块的原始 alpha；停留期间只允许一个定时器。
 */
public final class PoemEngine {

    public enum State {IDLE, CRAWL, CLOCK, PAUSED}

    public interface Host {
        /** 开始爬行：准备 Overlay、测量文本、扫描模块。 */
        void startCrawl(PoemItem poem, String displayText, int speedPx);

        /** 停止爬行：移除 Overlay、取消帧回调（幂等）。 */
        void stopCrawl();

        /** 恢复所有模块的原始 alpha（不是粗暴置 1f），幂等。 */
        void restoreModules();

        /** 安排时间停留：只允许一个定时器。seconds<=0 表示立刻回调。 */
        void scheduleClock(int seconds);

        /** 取消时间停留定时器。 */
        void cancelClock();

        /** 帧回调开关，供测试与诊断断言。 */
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

    /** 配置/诗库更新：内容变了才重建播放器，否则保持当前进度。 */
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

    /** 每次控制器 tick（配置变更 / 屏幕事件 / 前台切换）调用一次。 */
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
        // CRAWL / CLOCK 由帧回调或定时器推进，这里不做任何事（保持 0 定时器）。
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
        // 任何进入暂停的路径都必须恢复系统栏；已处于暂停态则跳过：
        // 暂停期间 tick() 仍会周期性调 pause()，重复恢复会触发整条状态栏全量重绘。
        if (changed) {
            host.restoreModules();
        }
        setFrameActive(false);
        state = State.PAUSED;
        if (changed) {
            host.onStateChanged(state);
        }
    }

    /** 彻底停止（关闭开关 / 视图不可用）：回到 IDLE 并释放播放器位置。 */
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

    /** 去 IDLE 但不重建播放器（当前这首作废，下一首继续）。 */
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

    /** 帧回调里发现诗句已经滚出屏幕时调用。 */
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

    /** 时间停留结束（或 clockSeconds=0 时立刻回调）。 */
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
