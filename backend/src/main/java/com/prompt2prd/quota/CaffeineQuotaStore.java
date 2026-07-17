package com.prompt2prd.quota;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Objects;

/** Bounded, automatically expiring in-memory counters for the single-instance MVP. */
@Component
public final class CaffeineQuotaStore implements QuotaStore {

    private final Cache<IpDayKey, UsageSnapshot> ipDailyUsage;
    private final Cache<LocalDate, Integer> globalDailyUsage;
    private final Cache<RateWindowKey, Integer> rateWindows;

    @Autowired
    public CaffeineQuotaStore(QuotaProperties properties) {
        this(properties, Ticker.systemTicker());
    }

    CaffeineQuotaStore(QuotaProperties properties, Ticker ticker) {
        long maximumSize = properties.maxTrackedEntries();
        ipDailyUsage = Caffeine.newBuilder()
                .maximumSize(maximumSize)
                .expireAfterAccess(Duration.ofDays(2))
                .ticker(ticker)
                .build();
        globalDailyUsage = Caffeine.newBuilder()
                .maximumSize(3)
                .expireAfterAccess(Duration.ofDays(2))
                .ticker(ticker)
                .build();
        rateWindows = Caffeine.newBuilder()
                .maximumSize(maximumSize)
                .expireAfterAccess(Duration.ofMinutes(2))
                .ticker(ticker)
                .build();
    }

    @Override
    public synchronized boolean tryConsumeIpOperation(
            String ipDigest,
            LocalDate date,
            QuotaOperation operation,
            int limit) {
        requireLimit(limit);
        IpDayKey key = new IpDayKey(ipDigest, date);
        UsageSnapshot current = ipDailyUsage.getIfPresent(key);
        if (current == null) {
            current = new UsageSnapshot(0, 0);
        }
        int used = operation == QuotaOperation.ANALYSIS
                ? current.analysisUsed()
                : current.fullPrdUsed();
        if (used >= limit) {
            return false;
        }
        UsageSnapshot updated = operation == QuotaOperation.ANALYSIS
                ? new UsageSnapshot(used + 1, current.fullPrdUsed())
                : new UsageSnapshot(current.analysisUsed(), used + 1);
        ipDailyUsage.put(key, updated);
        return true;
    }

    @Override
    public synchronized boolean tryConsumeGlobalCalls(LocalDate date, int calls, int limit) {
        if (calls <= 0) {
            throw new IllegalArgumentException("calls must be positive");
        }
        requireLimit(limit);
        int used = globalDailyUsage.get(date, ignored -> 0);
        if ((long) used + calls > limit) {
            return false;
        }
        globalDailyUsage.put(date, used + calls);
        return true;
    }

    @Override
    public synchronized boolean tryConsumeRateWindow(String ipDigest, long window, int limit) {
        requireLimit(limit);
        RateWindowKey key = new RateWindowKey(ipDigest, window);
        int used = rateWindows.get(key, ignored -> 0);
        if (used >= limit) {
            return false;
        }
        rateWindows.put(key, used + 1);
        return true;
    }

    @Override
    public synchronized UsageSnapshot snapshot(String ipDigest, LocalDate date) {
        UsageSnapshot snapshot = ipDailyUsage.getIfPresent(new IpDayKey(ipDigest, date));
        return snapshot == null ? new UsageSnapshot(0, 0) : snapshot;
    }

    @Override
    public synchronized int globalCallsUsed(LocalDate date) {
        Integer used = globalDailyUsage.getIfPresent(date);
        return used == null ? 0 : used;
    }

    void cleanUp() {
        ipDailyUsage.cleanUp();
        globalDailyUsage.cleanUp();
        rateWindows.cleanUp();
    }

    long trackedIpDays() {
        return ipDailyUsage.estimatedSize();
    }

    long trackedGlobalDays() {
        return globalDailyUsage.estimatedSize();
    }

    long trackedRateWindows() {
        return rateWindows.estimatedSize();
    }

    private void requireLimit(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
    }

    private record IpDayKey(String ipDigest, LocalDate date) {
        private IpDayKey {
            Objects.requireNonNull(ipDigest, "ipDigest");
            Objects.requireNonNull(date, "date");
        }
    }

    private record RateWindowKey(String ipDigest, long window) {
        private RateWindowKey {
            Objects.requireNonNull(ipDigest, "ipDigest");
        }
    }
}
