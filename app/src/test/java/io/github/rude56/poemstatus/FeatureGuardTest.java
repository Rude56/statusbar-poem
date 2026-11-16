package io.github.rude56.poemstatus;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** 熔断器：连续失败 MAX_FAIL 次后熔断，之后低频试探自愈。 */
public class FeatureGuardTest {

    @Test
    public void successPathReturnsTrue() {
        FeatureGuard g = new FeatureGuard();
        assertTrue(g.guard("x", () -> {
        }));
        assertFalse(g.isDisabled("x"));
    }

    @Test
    public void failuresBelowThresholdDoNotFuse() {
        FeatureGuard g = new FeatureGuard();
        FeatureGuard.Body boom = () -> {
            throw new IllegalStateException("boom");
        };
        for (int i = 0; i < FeatureGuard.MAX_FAIL - 1; i++) {
            assertFalse(g.guardFast("x", boom));
        }
        assertFalse("未达阈值不得熔断", g.isDisabled("x"));
        assertFalse(g.guardFast("x", boom));
        assertTrue("达到阈值必须熔断", g.isDisabled("x"));
    }

    /** 熔断后必须能自愈。 */
    @Test
    public void fusedFeatureRecoversOnProbe() {
        FeatureGuard g = new FeatureGuard();
        FeatureGuard.Body boom = () -> {
            throw new IllegalStateException("boom");
        };
        for (int i = 0; i < FeatureGuard.MAX_FAIL; i++) {
            g.guardFast("x", boom);
        }
        assertTrue(g.isDisabled("x"));

        // 熔断期间绝大多数调用必须被直接拒绝（不执行 body）。
        final int[] ran = {0};
        FeatureGuard.Body counted = () -> ran[0]++;
        int refused = 0;
        for (int i = 0; i < FeatureGuard.MAX_FAIL - 1; i++) {
            if (!g.guardFast("x", counted)) {
                refused++;
            }
        }
        assertEquals("熔断期间必须拒绝执行", 0, ran[0]);
        assertEquals(FeatureGuard.MAX_FAIL - 1, refused);

        // 下一次是试探：成功 → 恢复。
        assertTrue("试探必须放行", g.guardFast("x", counted));
        assertEquals("试探必须真的执行了 body", 1, ran[0]);
        assertFalse("试探成功后必须解除熔断", g.isDisabled("x"));
        assertEquals("失败计数必须清零", 0, g.failCount("x"));
    }

    /** 试探失败要重新熔断，但不要把失败计数无限累加。 */
    @Test
    public void failedProbeRefusesAgain() {
        FeatureGuard g = new FeatureGuard();
        FeatureGuard.Body boom = () -> {
            throw new IllegalStateException("boom");
        };
        for (int i = 0; i < FeatureGuard.MAX_FAIL; i++) {
            g.guardFast("x", boom);
        }
        assertTrue(g.isDisabled("x"));
        // 走到试探点并失败。
        boolean sawProbe = false;
        for (int i = 0; i < FeatureGuard.MAX_FAIL; i++) {
            if (!g.guardFast("x", boom)) {
                sawProbe = true;
            }
        }
        assertTrue(sawProbe);
        assertTrue("试探失败后必须仍然熔断", g.isDisabled("x"));
    }

    /** reset 必须能手动解除（供「用户改了配置」之类的外部事件调用）。 */
    @Test
    public void resetClearsFuse() {
        FeatureGuard g = new FeatureGuard();
        FeatureGuard.Body boom = () -> {
            throw new IllegalStateException("boom");
        };
        for (int i = 0; i < FeatureGuard.MAX_FAIL; i++) {
            g.guardFast("y", boom);
        }
        assertTrue(g.isDisabled("y"));
        assertEquals(FeatureGuard.MAX_FAIL, g.failCount("y"));
        g.reset("y");
        assertFalse(g.isDisabled("y"));
        assertEquals(0, g.failCount("y"));
    }

    /** 不同 id 互不影响：一个特性熔断不能拖垮别的特性。 */
    @Test
    public void differentIdsAreIndependent() {
        FeatureGuard g = new FeatureGuard();
        FeatureGuard.Body boom = () -> {
            throw new IllegalStateException("boom");
        };
        for (int i = 0; i < FeatureGuard.MAX_FAIL; i++) {
            g.guardFast("a", boom);
        }
        assertTrue(g.isDisabled("a"));
        assertFalse(g.isDisabled("b"));
        assertTrue("另一个特性必须照常工作", g.guardFast("b", () -> {
        }));
    }

    /** 未熔断时快路径不得写 Map（省电约束）。 */
    @Test
    public void fastPathLeavesNoTrace() {
        FeatureGuard g = new FeatureGuard();
        for (int i = 0; i < 100; i++) {
            assertTrue(g.guardFast("occlude", () -> {
            }));
        }
        assertEquals(0, g.failCount("occlude"));
        assertTrue(g.disabledIds().isEmpty());
    }
}
