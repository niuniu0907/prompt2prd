package com.prompt2prd.common.security;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LogSanitizerTests {

    private final LogSanitizer sanitizer = new LogSanitizer();

    @Test
    void modelCallSummaryContainsOnlySafeFields() {
        String summary = sanitizer.modelCallSummary(
                "53be8d9c-8949-4476-8835-8f12080257ac",
                "prd-generation",
                Duration.ofMillis(1234),
                "TIMEOUT");

        assertTrue(summary.contains("requestId=53be8d9c"));
        assertTrue(summary.contains("task=prd-generation"));
        assertTrue(summary.contains("elapsedMs=1234"));
        assertTrue(summary.contains("errorCategory=TIMEOUT"));
    }

    @Test
    void modelCallSummaryWithNullErrorCategoryExcludesIt() {
        String summary = sanitizer.modelCallSummary(
                "53be8d9c-8949-4476-8835-8f12080257ac",
                "analysis", Duration.ofMillis(500), null);

        assertFalse(summary.contains("errorCategory"));
    }

    @Test
    void modelCallSummaryDoesNotContainFullPromptOrKey() {
        String summary = sanitizer.modelCallSummary(
                "req-123", "test", Duration.ofMillis(100), "INTERNAL");

        assertFalse(summary.contains("Bearer"));
        assertFalse(summary.contains("sk-"));
        for (String secretTerm : List.of("api_key", "secret", "token", "password", "Authorization")) {
            assertFalse(summary.toLowerCase().contains(secretTerm.toLowerCase()),
                    "summary should not contain '" + secretTerm + "'");
        }
    }

    @Test
    void sanitizeUserTextTruncatesLongContent() {
        String longText = "x".repeat(200);
        String sanitized = sanitizer.sanitizeUserText(longText);
        assertTrue(sanitized.endsWith("..."));
        assertTrue(sanitized.length() <= LogSanitizer.MAX_USER_TEXT_IN_LOG + 3);
    }

    @Test
    void sanitizeUserTextRedactsBearerToken() {
        String text = "使用 Bearer sk-abc123def456 进行认证";
        String sanitized = sanitizer.sanitizeUserText(text);
        assertTrue(sanitized.contains("[REDACTED_BEARER]"));
        assertFalse(sanitized.contains("sk-abc123def456"));
    }

    @Test
    void sanitizeUserTextRedactsKeyLikePatterns() {
        String text = "api_key: sk-secret-value-here";
        String sanitized = sanitizer.sanitizeUserText(text);
        assertTrue(sanitized.contains("[REDACTED_KEY]"));
        assertFalse(sanitized.contains("sk-secret-value-here"));
    }

    @Test
    void sanitizeUserTextRedactsAuthorizationHeader() {
        String text = "Authorization: Bearer token123";
        String sanitized = sanitizer.sanitizeUserText(text);
        assertTrue(sanitized.contains("[REDACTED_AUTH_HEADER]"));
    }

    @Test
    void sanitizeUserTextReturnsPlaceholderForNullOrEmpty() {
        assertEquals("(empty)", sanitizer.sanitizeUserText(null));
        assertEquals("(empty)", sanitizer.sanitizeUserText("   "));
    }

    @Test
    void sanitizeUserTextPreservesSafeShortText() {
        String text = "创建任务管理系统的需求分析";
        String sanitized = sanitizer.sanitizeUserText(text);
        assertEquals(text, sanitized);
    }
}
