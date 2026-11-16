package io.github.rude56.poemstatus;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * 「一首诗什么时候结束」—— 必须按<b>屏幕上最后一个字消失</b>判定，不能按轨道走完。
 *
 * <p>用户报的现象：「诗穿过一个比较长的虫洞后要等好久才来下一首」。
 *
 * <p>根因：运行时用 {@code left + width < 0} 判结束，这等于假设「轨道坐标 = 屏幕坐标」。
 * 但虫洞把整句<b>往左瞬移</b>了一个洞宽 —— 屏幕上早就没字了，轨道还要再走一个洞宽。
 * 空等时长 = 洞宽 ÷ 速度，且这段时间屏幕完全空白：
 * <pre>
 *   洞宽 610px / 172px/s → 3.5 秒
 *   洞宽 770px / 172px/s → 4.5 秒
 *   洞宽 1000px/ 172px/s → 5.8 秒
 * </pre>
 *
 * <p>这一组是<b>行为验证</b>：用真实的 {@link HoleRects#wormholeSegments} 逐帧算屏幕上的
 * 实际绘制范围，找出「最后一个像素消失」的那一刻，再与 {@link HoleRects#finishLeft}
 * 给出的判据比较。两者必须一致。
 */
public class PoemFinishTest {

    private static final int BAR_W = 1440;
    private static final float SPEED = 172f;
    private static final int CAP = 32;

    private static final float[] S = new float[CAP];
    private static final float[] T = new float[CAP];
    private static final float[] W = new float[CAP];

    /** 本帧屏幕上还有没有可见像素（裁到状态栏范围内）。 */
    private static boolean hasVisiblePixel(List<HoleRects.Hole> holes, float left, float textWidth) {
        int n = HoleRects.wormholeSegments(holes, left, left + textWidth, 0, S, T, W, CAP);
        if (n <= 0) {
            // 没有虫洞：整条轨道直接画，裁到状态栏。
            return left + textWidth > 0f && left < BAR_W;
        }
        for (int i = 0; i < n; i++) {
            if (W[i] <= 0f) {
                continue;
            }
            float l = Math.max(S[i], 0f);
            float r = Math.min(S[i] + W[i], (float) BAR_W);
            if (r > l) {
                return true;
            }
        }
        return false;
    }

    /**
     * {@code finishLeft} 必须正好等于「最后一个像素消失」的那个轨道左端。
     *
     * <p>做法：以 0.5px 步长扫描轨道左端，找到最后一个「还有像素」的位置，
     * 与 {@code finishLeft} 比较，允许一个步长的误差。
     */
    private static void assertFinishMatchesReality(String holesRaw, float textWidth) {
        List<HoleRects.Hole> holes = HoleRects.parse(holesRaw);
        float predicted = HoleRects.finishLeft(holes, textWidth);

        // 从右往左扫，找最后一个还有可见像素的 left。
        float lastVisible = Float.NaN;
        for (float left = BAR_W; left > -textWidth - 400f; left -= 0.5f) {
            if (hasVisiblePixel(holes, left, textWidth)) {
                lastVisible = left;
            } else if (!Float.isNaN(lastVisible)) {
                break;                      // 已经进入「之后都没有」的区间
            }
        }
        assertTrue("这条配置下应当能扫到可见帧", !Float.isNaN(lastVisible));
        assertEquals("结束判据与实际最后一个可见帧不一致（洞=" + holesRaw
                        + "，文字宽=" + textWidth + "）",
                lastVisible, predicted, 1.0f);
    }

    @Test
    public void finishIsExactForNoHole() {
        assertFinishMatchesReality("", 800f);
        // 没有洞时就是经典判据：left < -width
        assertEquals(-800f, HoleRects.finishLeft(HoleRects.parse(""), 800f), 0.01f);
    }

    @Test
    public void finishIsExactForShortWormhole() {
        assertFinishMatchesReality("170:270:wormhole:0", 800f);
    }

    @Test
    public void finishIsExactForTheCurrentWideWormhole() {
        // 本机当前配置：洞宽 610px。
        assertFinishMatchesReality("670:1280:wormhole:0", 800f);
    }

    @Test
    public void finishIsExactForVeryWideWormhole() {
        assertFinishMatchesReality("200:1200:wormhole:0", 800f);
    }

    @Test
    public void finishIsExactForTwoWormholes() {
        assertFinishMatchesReality("100:300:wormhole:0,900:1200:wormhole:1", 800f);
    }

    @Test
    public void finishIsExactForWormholePlusTunnel() {
        // 隧道不影响位置，所以不计入 finishLeft。
        assertFinishMatchesReality("670:1280:wormhole:0,100:300:tunnel:1", 800f);
    }

    /** 隧道洞不改变结束时机 —— 它只是遮住字，位置与速度都不变。 */
    @Test
    public void tunnelDoesNotChangeFinishTime() {
        float noHole = HoleRects.finishLeft(HoleRects.parse(""), 800f);
        float withTunnel = HoleRects.finishLeft(HoleRects.parse("100:900:tunnel:0"), 800f);
        assertEquals("隧道不得影响结束时机", noHole, withTunnel, 0.01f);
    }

    /**
     * 省下的空等时间必须正好等于「洞宽 ÷ 速度」。
     *
     * <p>这条把「用户感知」直接钉成数字：改之前每次换首前空白 3.5 秒。
     */
    @Test
    public void savedDeadTimeEqualsHoleWidthOverSpeed() {
        List<HoleRects.Hole> holes = HoleRects.parse("670:1280:wormhole:0");
        float textWidth = 800f;
        float oldFinish = -textWidth;                                  // 旧判据
        float newFinish = HoleRects.finishLeft(holes, textWidth);
        float savedSeconds = (newFinish - oldFinish) / SPEED;
        assertEquals("省下的时间必须正好是洞宽÷速度", 610f / SPEED, savedSeconds, 0.001f);
        assertTrue("这条配置下确实省下了 3.5 秒以上", savedSeconds > 3.5f);
    }

    /** 多个虫洞时省下的时间 = 总洞宽 ÷ 速度（位移是累加的）。 */
    @Test
    public void savedDeadTimeSumsAllWormholes() {
        List<HoleRects.Hole> holes = HoleRects.parse("100:300:wormhole:0,900:1200:wormhole:1");
        assertEquals("总洞宽", 500f, HoleRects.totalWormholeWidth(holes), 0.01f);
        assertEquals(-800f + 500f, HoleRects.finishLeft(holes, 800f), 0.01f);
    }

    /** 越界的文字宽度不能让判据把诗提前掐掉。 */
    @Test
    public void finishIsSafeForDegenerateInputs() {
        List<HoleRects.Hole> holes = HoleRects.parse("670:1280:wormhole:0");
        // 极短文本：判据仍然要落在「最后一个字离开」的位置。
        assertFinishMatchesReality("670:1280:wormhole:0", 60f);
        // 没有洞且文本宽度为 0：finishLeft = 0，不会出现负的无限等待。
        assertEquals(0f, HoleRects.finishLeft(null, 0f), 0.01f);
    }
}
