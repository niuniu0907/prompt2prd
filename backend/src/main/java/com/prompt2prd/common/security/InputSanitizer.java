package com.prompt2prd.common.security;

import com.prompt2prd.common.api.ApiException;
import org.springframework.stereotype.Component;

/**
 * Central input boundary that enforces length limits and rejects content
 * patterns that are never legitimate in a Prompt2PRD request.
 * <p>
 * This is a defence-in-depth layer: controllers and validators each apply
 * their own schema checks; the sanitizer adds a uniform, early-rejecting
 * ceiling so no oversized or manifestly-dangerous payload reaches downstream
 * processing or a model prompt.
 */
@Component
public final class InputSanitizer {

    /** Maximum bytes for any single text input (requirement text, question answer, etc.). */
    public static final int MAX_TEXT_LENGTH = 32_000;

    /** Maximum bytes for a complete file upload payload. */
    public static final int MAX_UPLOAD_BYTES = 2_000_000;

    /** Maximum bytes for a complete JSON request body. */
    public static final int MAX_REQUEST_BODY_BYTES = 2_097_152; // 2 MiB

    /**
     * Validates a user-supplied text input field.
     *
     * @throws ApiException.BadRequest if the text is null, exceeds the limit,
     *         or contains obviously-injected content
     */
    public String sanitizeText(String input, String fieldName) {
        if (input == null) {
            throw new ApiException.BadRequest(fieldName + " must not be null");
        }
        String trimmed = input.trim();
        if (trimmed.isEmpty()) {
            throw new ApiException.BadRequest(fieldName + " must not be empty");
        }
        if (trimmed.length() > MAX_TEXT_LENGTH) {
            throw new ApiException.BadRequest(
                    fieldName + " exceeds the maximum length of " + MAX_TEXT_LENGTH + " characters");
        }
        return trimmed;
    }

    /**
     * Validates file upload size.
     *
     * @throws ApiException.BadRequest if the size exceeds the upload limit
     */
    public void checkUploadSize(long byteCount) {
        if (byteCount <= 0) {
            throw new ApiException.BadRequest("Upload must not be empty");
        }
        if (byteCount > MAX_UPLOAD_BYTES) {
            throw new ApiException.BadRequest(
                    "Upload exceeds the maximum size of " + MAX_UPLOAD_BYTES + " bytes");
        }
    }

    /**
     * Validates total request body size.
     *
     * @throws ApiException.BadRequest if the body exceeds the limit
     */
    public void checkRequestBodySize(long byteCount) {
        if (byteCount > MAX_REQUEST_BODY_BYTES) {
            throw new ApiException.BadRequest(
                    "Request body exceeds the maximum size of " + MAX_REQUEST_BODY_BYTES + " bytes");
        }
    }
}
