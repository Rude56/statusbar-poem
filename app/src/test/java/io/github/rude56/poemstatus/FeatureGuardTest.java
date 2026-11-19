package io.github.rude56.poemstatus;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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

        assertTrue("试探必须放行", g.guardFast("x", counted));
        assertEquals("试探必须真的执行了 body", 1, ran[0]);
        assertFalse("试探成功后必须解除熔断", g.isDisabled("x"));
        assertEquals("失败计数必须清零", 0, g.failCount("x"));
    }

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

        boolean sawProbe = false;
        for (int i = 0; i < FeatureGuard.MAX_FAIL; i++) {
            if (!g.guardFast("x", boom)) {
                sawProbe = true;
            }
        }
        assertTrue(sawProbe);
        assertTrue("试探失败后必须仍然熔断", g.isDisabled("x"));
    }

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
