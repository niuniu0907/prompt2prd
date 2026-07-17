package com.prompt2prd.common.api;

import org.springframework.http.HttpStatus;

/**
 * Base exception for all predictable API errors.
 * <p>
 * Subclasses carry their own {@link ErrorCode} and {@link HttpStatus} so
 * the global handler can produce a uniform {@link ApiErrorResponse} without
 * per-type dispatch logic scattered across the codebase.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;
    private final HttpStatus httpStatus;

    public ApiException(ErrorCode errorCode, HttpStatus httpStatus, String message) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    public ApiException(ErrorCode errorCode, HttpStatus httpStatus, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    // --- Concrete exception classes below (one per ErrorCode category) ---

    /** Request parameters failed validation. */
    public static class BadRequest extends ApiException {
        public BadRequest(String message) {
            super(ErrorCode.BAD_REQUEST, HttpStatus.BAD_REQUEST, message);
        }
    }

    /** Authentication failure with upstream model service (maps 401 from model, not user login). */
    public static class Unauthorized extends ApiException {
        public Unauthorized(String message) {
            super(ErrorCode.UNAUTHORIZED, HttpStatus.BAD_GATEWAY, message);
        }
    }

    /** Model not found or not available. */
    public static class ModelNotFound extends ApiException {
        public ModelNotFound(String message) {
            super(ErrorCode.MODEL_NOT_FOUND, HttpStatus.NOT_FOUND, message);
        }
    }

    /** Format of model response is incompatible with expected schema. */
    public static class FormatIncompatible extends ApiException {
        public FormatIncompatible(String message) {
            super(ErrorCode.FORMAT_INCOMPATIBLE, HttpStatus.valueOf(422), message);
        }

        public FormatIncompatible(String message, Throwable cause) {
            super(ErrorCode.FORMAT_INCOMPATIBLE, HttpStatus.valueOf(422), message, cause);
        }
    }

    /** IP-level or global rate limit exceeded. */
    public static class RateLimitExceeded extends ApiException {
        public RateLimitExceeded(String message) {
            super(ErrorCode.RATE_LIMIT_EXCEEDED, HttpStatus.TOO_MANY_REQUESTS, message);
        }
    }

    /** Upstream model service address is unreachable. */
    public static class ServiceUnavailable extends ApiException {
        public ServiceUnavailable(String message) {
            super(ErrorCode.SERVICE_UNAVAILABLE, HttpStatus.BAD_GATEWAY, message);
        }

        public ServiceUnavailable(String message, Throwable cause) {
            super(ErrorCode.SERVICE_UNAVAILABLE, HttpStatus.BAD_GATEWAY, message, cause);
        }
    }

    /** Upstream call timed out. */
    public static class RequestTimeout extends ApiException {
        public RequestTimeout(String message) {
            super(ErrorCode.REQUEST_TIMEOUT, HttpStatus.GATEWAY_TIMEOUT, message);
        }
    }

    /** Unexpected internal error (catch-all for unanticipated failures). */
    public static class Internal extends ApiException {
        public Internal(String message) {
            super(ErrorCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, message);
        }

        public Internal(String message, Throwable cause) {
            super(ErrorCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, message, cause);
        }
    }
}
