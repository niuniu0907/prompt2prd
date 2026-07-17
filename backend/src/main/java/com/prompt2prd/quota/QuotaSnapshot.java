package com.prompt2prd.quota;

/** Safe quota status returned to callers; it contains no client identifier. */
public record QuotaSnapshot(
        boolean systemKeyAvailable,
        int analysisRemaining,
        int fullPrdRemaining,
        int globalCallsRemaining) {

    public QuotaSnapshot {
        if (analysisRemaining < 0 || fullPrdRemaining < 0 || globalCallsRemaining < 0) {
            throw new IllegalArgumentException("Remaining quota values must not be negative");
        }
    }
}
