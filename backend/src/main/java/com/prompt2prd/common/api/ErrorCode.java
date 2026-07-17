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
    BAD_REQUEST("Request parameters are invalid. Check required fields and value formats."),

    /** Authentication with the upstream model service failed (401 from model). */
    UNAUTHORIZED("Model service authentication failed. Verify the selected API key."),

    /** The requested model name is not recognized or not available. */
    MODEL_NOT_FOUND("The selected model is unavailable. Check the model name and service configuration."),

    /** Format of the model response is incompatible with the expected schema. */
    FORMAT_INCOMPATIBLE("The model response format is incompatible. Retry or choose another model."),

    /** IP-level or global rate limit has been exceeded. */
    RATE_LIMIT_EXCEEDED("The request limit has been reached. Retry later or use your own API key."),

    /** The upstream model service address is unreachable. */
    SERVICE_UNAVAILABLE("The model service is unreachable. Check the service address and network connection."),

    /** The upstream call timed out before producing a usable result. */
    REQUEST_TIMEOUT("The model request timed out. Retry or choose a faster model."),

    /** An unexpected internal error occurred. */
    INTERNAL_ERROR("An unexpected internal error occurred. Retry or contact support.");

    private final String clientMessage;

    ErrorCode(String clientMessage) {
        this.clientMessage = clientMessage;
    }

    /**
     * Returns the category-level message that is safe to expose to clients.
     * Exception messages can contain upstream payloads, prompts, or credentials
     * and must never be copied into an API response.
     */
    public String getClientMessage() {
        return clientMessage;
    }
}
