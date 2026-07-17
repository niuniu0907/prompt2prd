package com.prompt2prd.common.api;

/**
 * Stable error codes for the Prompt2PRD API.
 * <p>
 * Each code maps to a specific category of error, allowing clients to
 * respond programmatically. The numeric range mirrors the HTTP status
 * class but is stable across code changes to the message text.
 */
public enum ErrorCode {

    /** Request parameters failed validation (name, length, type, etc.). */
    BAD_REQUEST,

    /** Authentication with the upstream model service failed (401 from model). */
    UNAUTHORIZED,

    /** The requested model name is not recognized or not available. */
    MODEL_NOT_FOUND,

    /** Format of the model response is incompatible with the expected schema. */
    FORMAT_INCOMPATIBLE,

    /** IP-level or global rate limit has been exceeded. */
    RATE_LIMIT_EXCEEDED,

    /** The upstream model service address is unreachable. */
    SERVICE_UNAVAILABLE,

    /** The upstream call timed out before producing a usable result. */
    REQUEST_TIMEOUT,

    /** An unexpected internal error occurred. */
    INTERNAL_ERROR
}
