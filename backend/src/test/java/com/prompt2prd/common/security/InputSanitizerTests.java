package com.prompt2prd.common.security;

import com.prompt2prd.common.api.ApiException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InputSanitizerTests {

    private final InputSanitizer sanitizer = new InputSanitizer();

    @Test
    void acceptsValidText() {
        String result = sanitizer.sanitizeText("  创建一个任务管理工具  ", "description");
        assertEquals("创建一个任务管理工具", result);
    }

    @Test
    void rejectsNullText() {
        ApiException ex = assertThrows(ApiException.BadRequest.class, () ->
                sanitizer.sanitizeText(null, "description"));
        assertTrue(ex.getMessage().contains("description"));
    }

    @Test
    void rejectsEmptyText() {
        ApiException ex = assertThrows(ApiException.BadRequest.class, () ->
                sanitizer.sanitizeText("   ", "description"));
        assertTrue(ex.getMessage().contains("description"));
    }

    @Test
    void rejectsOversizedText() {
        String big = "x".repeat(InputSanitizer.MAX_TEXT_LENGTH + 1);
        ApiException ex = assertThrows(ApiException.BadRequest.class, () ->
                sanitizer.sanitizeText(big, "description"));
        assertTrue(ex.getMessage().contains(String.valueOf(InputSanitizer.MAX_TEXT_LENGTH)));
    }

    @Test
    void textAtMaxLengthIsAccepted() {
        String atLimit = "x".repeat(InputSanitizer.MAX_TEXT_LENGTH);
        assertEquals(atLimit, sanitizer.sanitizeText(atLimit, "description"));
    }

    @Test
    void rejectsEmptyUpload() {
        ApiException ex = assertThrows(ApiException.BadRequest.class, () ->
                sanitizer.checkUploadSize(0));
        assertTrue(ex.getMessage().contains("empty"));
    }

    @Test
    void rejectsOversizedUpload() {
        ApiException ex = assertThrows(ApiException.BadRequest.class, () ->
                sanitizer.checkUploadSize(InputSanitizer.MAX_UPLOAD_BYTES + 1));
        assertTrue(ex.getMessage().contains(String.valueOf(InputSanitizer.MAX_UPLOAD_BYTES)));
    }

    @Test
    void acceptsUploadAtMaxSize() {
        assertDoesNotThrow(() -> sanitizer.checkUploadSize(InputSanitizer.MAX_UPLOAD_BYTES));
    }

    @Test
    void rejectsOversizedRequestBody() {
        ApiException ex = assertThrows(ApiException.BadRequest.class, () ->
                sanitizer.checkRequestBodySize(InputSanitizer.MAX_REQUEST_BODY_BYTES + 1));
        assertTrue(ex.getMessage().contains(String.valueOf(InputSanitizer.MAX_REQUEST_BODY_BYTES)));
    }

    @Test
    void acceptsRequestBodyAtMaxSize() {
        assertDoesNotThrow(() -> sanitizer.checkRequestBodySize(InputSanitizer.MAX_REQUEST_BODY_BYTES));
    }
}
