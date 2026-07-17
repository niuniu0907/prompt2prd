package com.prompt2prd.common.api;

import java.time.Instant;

/**
 * Standard error response body for all Prompt2PRD API errors.
 * <p>
 * Never contains keys, Authorization headers, or complete prompt text.
 * Every error response includes a unique request ID for traceability.
 *
 * @param code      stable machine-readable error code
 * @param message   human-readable, actionable description (must not leak secrets)
 * @param requestId unique identifier for the current HTTP request
 * @param timestamp UTC instant when the error was generated
 */
public record ApiErrorResponse(
        ErrorCode code,
        String message,
        String requestId,
        Instant timestamp
) {

    public static ApiErrorResponse of(ErrorCode code, String message, String requestId) {
        return new ApiErrorResponse(code, message, requestId, Instant.now());
    }
}
