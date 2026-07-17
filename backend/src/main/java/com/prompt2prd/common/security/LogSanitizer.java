package com.prompt2prd.common.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.regex.Pattern;

/**
 * Centralised log-safety policy.
 * <p>
 * All business-layer log statements that reference model interactions must
 * pass through the sanitizer so that API keys, Authorization headers, and
 * complete prompt / requirement text never appear in log output.
 * <p>
 * Acceptable log fields: requestId, task / operation type, elapsed time,
 * error category (not message), and counts. Everything else is stripped.
 */
@Component
public final class LogSanitizer {

    private static final Logger log = LoggerFactory.getLogger(LogSanitizer.class);
    private static final Pattern BEARER_PATTERN = Pattern.compile(
            "(?i)bearer\\s+[a-z0-9\\-._~+/]+=*", Pattern.MULTILINE);
    private static final Pattern KEY_LIKE = Pattern.compile(
            "(?i)(sk|api[_-]?key|secret|token|password)\\s*[:=]\\s*\\S+");
    private static final Pattern AUTHORIZATION_HEADER = Pattern.compile(
            "(?i)authorization\\s*:\\s*\\S+");

    /** Maximum characters of user text that may appear in a log message. */
    public static final int MAX_USER_TEXT_IN_LOG = 80;

    /**
     * Returns a log-safe summary of a model call outcome. The returned string
     * contains only the requestId, task label, elapsed duration, and error
     * category — never the prompt, response body, or credential material.
     */
    public String modelCallSummary(
            String requestId, String task, Duration elapsed, String errorCategory) {
        StringBuilder sb = new StringBuilder(128);
        sb.append("requestId=").append(requestId);
        sb.append(" task=").append(nullToNone(task));
        sb.append(" elapsedMs=").append(elapsed.toMillis());
        if (errorCategory != null && !errorCategory.isBlank()) {
            sb.append(" errorCategory=").append(errorCategory);
        }
        return sb.toString();
    }

    /**
     * Returns a truncated, sanitised version of user-supplied text for logging.
     * The output is never longer than {@link #MAX_USER_TEXT_IN_LOG} characters
     * and has all credential-like patterns removed.
     */
    public String sanitizeUserText(String text) {
        if (text == null || text.isBlank()) {
            return "(empty)";
        }
        String cleaned = BEARER_PATTERN.matcher(text).replaceAll("[REDACTED_BEARER]");
        cleaned = KEY_LIKE.matcher(cleaned).replaceAll("[REDACTED_KEY]");
        cleaned = AUTHORIZATION_HEADER.matcher(cleaned).replaceAll("[REDACTED_AUTH_HEADER]");
        if (cleaned.length() <= MAX_USER_TEXT_IN_LOG) {
            return cleaned;
        }
        return cleaned.substring(0, MAX_USER_TEXT_IN_LOG) + "...";
    }

    /**
     * Logs a model interaction using only safe metadata fields.
     */
    public void logModelCall(
            String requestId, String task, Duration elapsed, String errorCategory) {
        log.info("{}", modelCallSummary(requestId, task, elapsed, errorCategory));
    }

    private static String nullToNone(String value) {
        return value == null || value.isBlank() ? "none" : value;
    }
}
