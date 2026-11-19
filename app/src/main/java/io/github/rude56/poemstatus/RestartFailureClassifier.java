package io.github.rude56.poemstatus;

import java.io.FileNotFoundException;
import java.util.Locale;

public final class RestartFailureClassifier {
    private RestartFailureClassifier() {
    }

    public static RestartResult classifyRootProbe(int exitCode, String output) {
        String text = normalize(output);
        if (exitCode == 0 && containsUid(text, "0")) {
            return RestartResult.SUCCESS;
        }
        if (containsAny(text, "permission denied", "access denied", "request rejected",
                "request denied", "not allowed to su", "not allowed to execute su",
                "authorization denied", "authentication failure", "授权被拒绝", "权限被拒绝")) {
            return RestartResult.ROOT_DENIED;
        }
        if (containsAny(text, "su: not found", "su: inaccessible or not found",
                "su: no such file or directory", "root is not available",
                "root access is not available", "no su binary", "no su daemon",
                "cannot connect to daemon", "failed to connect to daemon")) {
            return RestartResult.ROOT_UNAVAILABLE;
        }
        if (exitCode == 0 && containsNumericUid(text)) {
            return RestartResult.ROOT_DENIED;
        }
        return RestartResult.COMMAND_FAILED;
    }

    public static RestartResult classifyCommand(int exitCode, String output) {
        if (exitCode == 0) {
            return RestartResult.SUCCESS;
        }
        String text = normalize(output);
        if (containsAny(text, "su: permission denied", "su: access denied",
                "su: request rejected", "su: request denied", "su: not allowed to su",
                "su: authorization denied", "su: authentication failure")) {
            return RestartResult.ROOT_DENIED;
        }
        if (containsAny(text, "su: not found", "su: inaccessible or not found",
                "su: no such file or directory", "su: cannot connect to daemon",
                "su: failed to connect to daemon")) {
            return RestartResult.ROOT_UNAVAILABLE;
        }
        return RestartResult.COMMAND_FAILED;
    }

    public static RestartResult classifySuStartFailure(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            String text = normalize(cause.getMessage());
            if (cause instanceof FileNotFoundException
                    || text.matches("(?s).*\\berror\\s*=\\s*2\\b.*")
                    || containsAny(text, "enoent", "no such file or directory")) {
                return RestartResult.ROOT_UNAVAILABLE;
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return RestartResult.COMMAND_FAILED;
    }

    private static String normalize(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT);
    }

    private static boolean containsAny(String text, String... candidates) {
        for (String candidate : candidates) {
            if (text.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsUid(String text, String expected) {
        for (String line : text.split("\\R")) {
            if (expected.equals(line.trim())) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsNumericUid(String text) {
        for (String line : text.split("\\R")) {
            if (line.trim().matches("[0-9]+")) {
                return true;
            }
        }
        return false;
    }
}
