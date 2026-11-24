package de.germanrangers.hstglabs.core.tech;

import java.util.Objects;

/**
 * One validation result (docs/design.md §8.3), shown in the log and the dev UI.
 *
 * @param severity {@code ERROR} disables the subject, {@code WARNING} does not
 * @param subject  what it is about: a technology id, a file, a lab type id, "lab tier 2"
 * @param code     stable id, e.g. {@code unknown_stage}
 * @param message  English explanation naming the offending ids
 */
public record Problem(Severity severity, String subject, String code, String message) {

    public enum Severity {
        ERROR,
        WARNING
    }

    public Problem {
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
    }

    public static Problem error(String subject, String code, String message) {
        return new Problem(Severity.ERROR, subject, code, message);
    }

    public static Problem warning(String subject, String code, String message) {
        return new Problem(Severity.WARNING, subject, code, message);
    }

    @Override
    public String toString() {
        return severity + " " + subject + " [" + code + "]: " + message;
    }
}
