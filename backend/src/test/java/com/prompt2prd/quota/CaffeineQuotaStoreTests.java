package com.prompt2prd.quota;

import com.github.benmanes.caffeine.cache.Ticker;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class CaffeineQuotaStoreTests {

    private static final LocalDate TODAY = LocalDate.of(2026, 7, 17);

    @Test
    void admitsCountersExactlyToTheirLimitsAndKeepsDimensionsIndependent() {
        MutableTicker ticker = new MutableTicker();
        CaffeineQuotaStore store = new CaffeineQuotaStore(properties(10), ticker);

        assertThat(store.tryConsumeIpOperation("digest-a", TODAY, QuotaOperation.ANALYSIS, 2)).isTrue();
        assertThat(store.tryConsumeIpOperation("digest-a", TODAY, QuotaOperation.ANALYSIS, 2)).isTrue();
        assertThat(store.tryConsumeIpOperation("digest-a", TODAY, QuotaOperation.ANALYSIS, 2)).isFalse();
        assertThat(store.tryConsumeIpOperation("digest-a", TODAY, QuotaOperation.FULL_PRD, 1)).isTrue();
        assertThat(store.tryConsumeIpOperation("digest-b", TODAY, QuotaOperation.ANALYSIS, 2)).isTrue();
        assertThat(store.tryConsumeIpOperation("digest-a", TODAY.plusDays(1), QuotaOperation.ANALYSIS, 2)).isTrue();

        QuotaStore.UsageSnapshot snapshot = store.snapshot("digest-a", TODAY);
        assertThat(snapshot.analysisUsed()).isEqualTo(2);
        assertThat(snapshot.fullPrdUsed()).isEqualTo(1);
    }

    @Test
    void admitsGlobalBatchesAtomicallyWithoutPassingTheLimit() {
        CaffeineQuotaStore store = new CaffeineQuotaStore(properties(10), Ticker.systemTicker());

        assertThat(store.tryConsumeGlobalCalls(TODAY, 3, 4)).isTrue();
        assertThat(store.tryConsumeGlobalCalls(TODAY, 2, 4)).isFalse();
        assertThat(store.globalCallsUsed(TODAY)).isEqualTo(3);
        assertThat(store.tryConsumeGlobalCalls(TODAY, 1, 4)).isTrue();
        assertThat(store.tryConsumeGlobalCalls(TODAY, 1, 4)).isFalse();
    }

    @Test
    void frequencyWindowsAreIndependentAndRejectTheFirstExcessRequest() {
        CaffeineQuotaStore store = new CaffeineQuotaStore(properties(10), Ticker.systemTicker());

        assertThat(store.tryConsumeRateWindow("digest-a", 100, 2)).isTrue();
        assertThat(store.tryConsumeRateWindow("digest-a", 100, 2)).isTrue();
        assertThat(store.tryConsumeRateWindow("digest-a", 100, 2)).isFalse();
        assertThat(store.tryConsumeRateWindow("digest-a", 101, 2)).isTrue();
    }

    @Test
    void staleEntriesExpireAutomaticallyAndCachesStayBounded() {
        MutableTicker ticker = new MutableTicker();
        CaffeineQuotaStore store = new CaffeineQuotaStore(properties(2), ticker);

        store.tryConsumeIpOperation("digest-a", TODAY, QuotaOperation.ANALYSIS, 3);
        store.tryConsumeGlobalCalls(TODAY, 1, 10);
        store.tryConsumeRateWindow("digest-a", 100, 3);
        store.tryConsumeIpOperation("digest-b", TODAY, QuotaOperation.ANALYSIS, 3);
        store.tryConsumeIpOperation("digest-c", TODAY, QuotaOperation.ANALYSIS, 3);
        store.cleanUp();

        assertThat(store.trackedIpDays()).isLessThanOrEqualTo(2);

        ticker.advance(Duration.ofDays(3));
        store.cleanUp();

        assertThat(store.trackedIpDays()).isZero();
        assertThat(store.trackedGlobalDays()).isZero();
        assertThat(store.trackedRateWindows()).isZero();
    }

    private QuotaProperties properties(int maxEntries) {
        return new QuotaProperties(3, 1, 10, 5, maxEntries, "test-salt");
    }

    private static final class MutableTicker implements Ticker {
        private long nanos;

        @Override
        public long read() {
            return nanos;
        }

        void advance(Duration duration) {
            nanos += duration.toNanos();
        }
    }
}
