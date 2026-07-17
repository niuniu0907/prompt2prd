package com.prompt2prd.quota;

import java.time.LocalDate;

/** Replaceable storage contract for quota counters. */
public interface QuotaStore {

    boolean tryConsumeIpOperation(
            String ipDigest,
            LocalDate date,
            QuotaOperation operation,
            int limit);

    boolean tryConsumeGlobalCalls(LocalDate date, int calls, int limit);

    boolean tryConsumeRateWindow(String ipDigest, long window, int limit);

    UsageSnapshot snapshot(String ipDigest, LocalDate date);

    int globalCallsUsed(LocalDate date);

    record UsageSnapshot(int analysisUsed, int fullPrdUsed) {
    }
}
