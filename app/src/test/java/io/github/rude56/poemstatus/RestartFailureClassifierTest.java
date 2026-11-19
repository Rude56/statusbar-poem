package io.github.rude56.poemstatus;

import org.junit.Test;

import java.io.FileNotFoundException;
import java.io.IOException;

import static org.junit.Assert.assertEquals;

public class RestartFailureClassifierTest {
    @Test
    public void acceptsVerifiedRootUidWithManagerBanner() {
        assertEquals(RestartResult.SUCCESS,
                RestartFailureClassifier.classifyRootProbe(0, "Root manager banner\r\n0\r\n"));
    }

    @Test
    public void requiresSuccessfulProbeAsWellAsZeroUid() {
        assertEquals(RestartResult.COMMAND_FAILED,
                RestartFailureClassifier.classifyRootProbe(1, "0\n"));
    }

    @Test
    public void missingUidIsNotAssumedToBeRoot() {
        assertEquals(RestartResult.COMMAND_FAILED,
                RestartFailureClassifier.classifyRootProbe(0, ""));
        assertEquals(RestartResult.COMMAND_FAILED,
                RestartFailureClassifier.classifyRootProbe(0, "1000-banner"));
    }

    @Test
    public void nonRootUidMeansAuthorizationWasNotGranted() {
        assertEquals(RestartResult.ROOT_DENIED,
                RestartFailureClassifier.classifyRootProbe(0, "2000\n"));
    }

    @Test
    public void classifiesExplicitAuthorizationDenialCaseInsensitively() {
        assertEquals(RestartResult.ROOT_DENIED,
                RestartFailureClassifier.classifyRootProbe(1, "su: Permission denied"));
        assertEquals(RestartResult.ROOT_DENIED,
                RestartFailureClassifier.classifyRootProbe(1, "SU: REQUEST REJECTED (uid=10234)"));
        assertEquals(RestartResult.ROOT_DENIED,
                RestartFailureClassifier.classifyRootProbe(1, "su: not allowed to su"));
    }

    @Test
    public void classifiesExplicitMissingRootService() {
        assertEquals(RestartResult.ROOT_UNAVAILABLE,
                RestartFailureClassifier.classifyRootProbe(127, "su: not found"));
        assertEquals(RestartResult.ROOT_UNAVAILABLE,
                RestartFailureClassifier.classifyRootProbe(1, "su: cannot connect to daemon"));
    }

    @Test
    public void arbitraryNonzeroOrEmptyProbeIsNotProofOfMissingRoot() {
        assertEquals(RestartResult.COMMAND_FAILED,
                RestartFailureClassifier.classifyRootProbe(1, null));
        assertEquals(RestartResult.COMMAND_FAILED,
                RestartFailureClassifier.classifyRootProbe(127, ""));
        assertEquals(RestartResult.COMMAND_FAILED,
                RestartFailureClassifier.classifyRootProbe(127, "id: not found"));
        assertEquals(RestartResult.COMMAND_FAILED,
                RestartFailureClassifier.classifyRootProbe(1, "id: unknown option -u"));
    }

    @Test
    public void missingSuExecutableIsUnavailable() {
        assertEquals(RestartResult.ROOT_UNAVAILABLE,
                RestartFailureClassifier.classifySuStartFailure(
                        new IOException("Cannot run program su: error=2, No such file or directory")));
        assertEquals(RestartResult.ROOT_UNAVAILABLE,
                RestartFailureClassifier.classifySuStartFailure(new FileNotFoundException("su")));
    }

    @Test
    public void checksNestedMissingExecutableCause() {
        assertEquals(RestartResult.ROOT_UNAVAILABLE,
                RestartFailureClassifier.classifySuStartFailure(
                        new IOException("start failed", new IOException("ENOENT"))));
    }

    @Test
    public void processLaunchPolicyAndOtherIoFailuresAreNotAssumedMissingRoot() {
        assertEquals(RestartResult.COMMAND_FAILED,
                RestartFailureClassifier.classifySuStartFailure(
                        new IOException("error=13, Permission denied")));
        assertEquals(RestartResult.COMMAND_FAILED,
                RestartFailureClassifier.classifySuStartFailure(new IOException("too many open files")));
        assertEquals(RestartResult.COMMAND_FAILED,
                RestartFailureClassifier.classifySuStartFailure(
                        new IOException("error=24, Too many open files")));
        assertEquals(RestartResult.COMMAND_FAILED,
                RestartFailureClassifier.classifySuStartFailure(new IOException("error=28")));
        assertEquals(RestartResult.COMMAND_FAILED,
                RestartFailureClassifier.classifySuStartFailure(new SecurityException("blocked")));
    }

    @Test
    public void commandExitCodesNeverMasqueradeAsMissingRoot() {
        assertEquals(RestartResult.SUCCESS, RestartFailureClassifier.classifyCommand(0, ""));
        for (int exitCode : new int[]{1, 9, 126, 127, 255}) {
            assertEquals(RestartResult.COMMAND_FAILED,
                    RestartFailureClassifier.classifyCommand(exitCode, null));
        }
    }

    @Test
    public void commandToolAndSelinuxFailuresAreNotAuthorizationDenials() {
        assertEquals(RestartResult.COMMAND_FAILED,
                RestartFailureClassifier.classifyCommand(127, "killall: not found"));
        assertEquals(RestartResult.COMMAND_FAILED,
                RestartFailureClassifier.classifyCommand(1, "pkill: Permission denied"));
        assertEquals(RestartResult.COMMAND_FAILED,
                RestartFailureClassifier.classifyCommand(1, "killall: no process killed"));
    }

    @Test
    public void recognizesExplicitSuReauthorizationDenialDuringRestart() {
        assertEquals(RestartResult.ROOT_DENIED,
                RestartFailureClassifier.classifyCommand(1, "su: Permission denied"));
        assertEquals(RestartResult.ROOT_DENIED,
                RestartFailureClassifier.classifyCommand(1, "SU: REQUEST REJECTED"));
        assertEquals(RestartResult.ROOT_UNAVAILABLE,
                RestartFailureClassifier.classifyCommand(1, "su: cannot connect to daemon"));
    }
}
