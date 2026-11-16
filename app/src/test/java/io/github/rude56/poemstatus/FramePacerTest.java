package io.github.rude56.poemstatus;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * 帧节奏。
 *
 * <p><b>这个测试文件曾经「覆盖」的是一段死代码</b>：
 * {@code FramePacer} 里原本还有 {@code reset/advance/delayMs/shouldDraw} 一套
 * 实例状态机，测试把它们逐条断言得很仔细，但生产代码里这些方法
 * <b>零调用</b> —— 真正跑在设备上的是 {@code PoetRuntime.OverlayView} 里
 * 按速度自适应的实现。也就是说：测试全绿，真实调度逻辑却没有任何测试盯着。
 *
 * <p>那套实例方法已删除，这里只测<b>真正被运行时调用的那个纯函数</b>
 * （{@code intervalNsForSpeed}），外加把「运行时确实调用它」钉在
 * {@code PowerRegressionTest} 里。
 */
public class FramePacerTest {

    @Test
    public void targetFrameIsAbout33ms() {
        assertEquals(30, FramePacer.TARGET_FPS);
        assertEquals(33_333_333L, FramePacer.TARGET_FRAME_NS);
        // 1 秒内最多 30 个目标点（120Hz 屏上唤醒次数从 120 降到 30）
        assertTrue(FramePacer.TARGET_FRAME_NS * 30L <= 1_000_000_000L);
    }

    /**
     * 帧间隔随速度自适应：低速时少醒，高速时回到 30fps 封顶。
     *
     * <p>依据：「位移不足 1px 不绘制」这条规则可以提前到调度阶段 ——
     * 走 1px 需要 {@code 1000/speed} 毫秒，与其每 33ms 醒来空转一次，
     * 不如直接按这个时间要下一帧。
     */
    @Test
    public void intervalGrowsAtLowSpeedAndCapsAtThirtyFps() {
        // 600px/s：1px 只要 1.7ms，比 33ms 短 → 用 30fps 封顶。
        assertEquals(FramePacer.TARGET_FRAME_NS, FramePacer.intervalNsForSpeed(600));
        assertEquals(FramePacer.TARGET_FRAME_NS, FramePacer.intervalNsForSpeed(120));
        // 60px/s：1px 要 16.7ms，仍比 33ms 短 → 还是 30fps。
        assertEquals(FramePacer.TARGET_FRAME_NS, FramePacer.intervalNsForSpeed(60));
        // 30px/s：1px 要 33.3ms → 正好等于 30fps 上限。
        assertEquals(FramePacer.TARGET_FRAME_NS, FramePacer.intervalNsForSpeed(30));
        // 20px/s：1px 要 50ms → 明显放宽（主线程只醒 20 次/秒）。
        assertTrue("低速必须放宽", FramePacer.intervalNsForSpeed(20) >= 50_000_000L);
        assertTrue("低速不得被压回 30fps",
                FramePacer.intervalNsForSpeed(20) > FramePacer.TARGET_FRAME_NS);
    }

    @Test
    public void intervalIsBoundedAndSafeForBadSpeed() {
        // 极端低速不得让间隔无限增长（否则掉帧/暂停都没法及时反映）。
        assertEquals(FramePacer.MAX_ADAPTIVE_INTERVAL_NS, FramePacer.intervalNsForSpeed(1));
        assertTrue(FramePacer.intervalNsForSpeed(1) <= FramePacer.MAX_ADAPTIVE_INTERVAL_NS);
        // 非法速度按封顶帧率处理，绝不返回 0 或负数（会导致死循环式 post）。
        assertEquals(FramePacer.TARGET_FRAME_NS, FramePacer.intervalNsForSpeed(0));
        assertEquals(FramePacer.TARGET_FRAME_NS, FramePacer.intervalNsForSpeed(-5));
    }

    /**
     * 间隔必须随速度单调不减：速度越慢，醒得越少。
     *
     * <p>这条断言能抓住「改公式时把大小关系写反」这类错误 ——
     * 那样低速反而会更高频地醒来，纯耗电且肉眼无感。
     */
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
