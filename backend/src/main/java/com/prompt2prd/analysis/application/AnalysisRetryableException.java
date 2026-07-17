package com.prompt2prd.analysis.application;

/** Signals invalid structured model output that may be retried with a fresh model call. */
public final class AnalysisRetryableException extends RuntimeException {

    public AnalysisRetryableException(String message) {
        super(message);
    }

    public AnalysisRetryableException(String message, Throwable cause) {
        super(message, cause);
    }
}
