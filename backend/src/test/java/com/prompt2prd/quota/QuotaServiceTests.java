package com.prompt2prd.quota;

import com.github.benmanes.caffeine.cache.Ticker;
import com.prompt2prd.common.api.ApiException;
import com.prompt2prd.common.config.ModelProperties;
import com.prompt2prd.model.domain.ModelConfig;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuotaServiceTests {

    private static final String DIGEST = "a".repeat(64);

    @Test
    void disabledSystemModeReportsZeroAndRejectsSystemOperations() {
        MutableClock clock = new MutableClock("2026-07-17T12:00:00Z");
        QuotaService service = service(properties(3, 1, 10, 5), disabledModels(), clock);

        assertThat(service.current(DIGEST)).isEqualTo(new QuotaSnapshot(false, 0, 0, 0));
        assertThatThrownBy(() -> service.beginOperation(
                DIGEST, ModelConfig.KeySource.SYSTEM, QuotaOperation.ANALYSIS))
                .isInstanceOf(ApiException.BadRequest.class);
    }

    @Test
    void enforcesPerIpDailyBoundariesAndResetsOnTheNextUtcDate() {
        MutableClock clock = new MutableClock("2026-07-17T23:59:00Z");
        QuotaService service = service(properties(2, 1, 10, 20), enabledModels(), clock);

        service.beginOperation(DIGEST, ModelConfig.KeySource.SYSTEM, QuotaOperation.ANALYSIS);
        service.beginOperation(DIGEST, ModelConfig.KeySource.SYSTEM, QuotaOperation.ANALYSIS);
        service.beginOperation(DIGEST, ModelConfig.KeySource.SYSTEM, QuotaOperation.FULL_PRD);

        assertThat(service.current(DIGEST)).isEqualTo(new QuotaSnapshot(true, 0, 0, 10));
        assertThatThrownBy(() -> service.beginOperation(
                DIGEST, ModelConfig.KeySource.SYSTEM, QuotaOperation.ANALYSIS))
                .isInstanceOf(ApiException.RateLimitExceeded.class);

        clock.setInstant("2026-07-18T00:01:00Z");

        assertThat(service.current(DIGEST)).isEqualTo(new QuotaSnapshot(true, 2, 1, 10));
    }

    @Test
    void oneChunkedAnalysisConsumesOneIpOperationButEveryChunkConsumesGlobalBudget() {
        MutableClock clock = new MutableClock("2026-07-17T12:00:00Z");
        QuotaService service = service(properties(3, 1, 4, 20), enabledModels(), clock);

        service.beginOperation(DIGEST, ModelConfig.KeySource.SYSTEM, QuotaOperation.ANALYSIS);
        service.acquireUpstreamCalls(ModelConfig.KeySource.SYSTEM, 3);

        assertThat(service.current(DIGEST)).isEqualTo(new QuotaSnapshot(true, 2, 1, 1));
        assertThatThrownBy(() -> service.acquireUpstreamCalls(ModelConfig.KeySource.SYSTEM, 2))
                .isInstanceOf(ApiException.RateLimitExceeded.class);
        assertThat(service.current(DIGEST).globalCallsRemaining()).isEqualTo(1);
    }

    @Test
    void userKeyBypassesFreeAndGlobalBudgetsButNotTheBasicFrequencyLimit() {
        MutableClock clock = new MutableClock("2026-07-17T12:00:00Z");
        QuotaService service = service(properties(3, 1, 1, 2), enabledModels(), clock);

        service.beginOperation(DIGEST, ModelConfig.KeySource.USER, QuotaOperation.ANALYSIS);
        service.acquireUpstreamCalls(ModelConfig.KeySource.USER, 100);
        service.beginOperation(DIGEST, ModelConfig.KeySource.USER, QuotaOperation.FULL_PRD);

        assertThat(service.current(DIGEST)).isEqualTo(new QuotaSnapshot(true, 3, 1, 1));
        assertThatThrownBy(() -> service.beginOperation(
                DIGEST, ModelConfig.KeySource.USER, QuotaOperation.ANALYSIS))
                .isInstanceOf(ApiException.RateLimitExceeded.class);
    }

    @Test
    void rejectsInvalidUpstreamCallCountsWithoutChangingBudget() {
        MutableClock clock = new MutableClock("2026-07-17T12:00:00Z");
        QuotaService service = service(properties(3, 1, 5, 5), enabledModels(), clock);

        assertThatThrownBy(() -> service.acquireUpstreamCalls(ModelConfig.KeySource.SYSTEM, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(service.current(DIGEST).globalCallsRemaining()).isEqualTo(5);
    }

    private QuotaService service(
            QuotaProperties properties,
            ModelProperties modelProperties,
            Clock clock) {
        return new QuotaService(
                new CaffeineQuotaStore(properties, Ticker.systemTicker()),
                properties,
                modelProperties,
                clock);
    }

    private QuotaProperties properties(int analysis, int prd, int global, int rate) {
        return new QuotaProperties(analysis, prd, global, rate, 100, "test-salt");
    }

    private ModelProperties enabledModels() {
        return ModelProperties.fromEnvironment(Map.of(
                ModelProperties.SYSTEM_KEY_ENABLED_ENV, "true",
                ModelProperties.SYSTEM_API_KEY_ENV, "test-only-key"));
    }

    private ModelProperties disabledModels() {
        return ModelProperties.fromEnvironment(Map.of());
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(String instant) {
            this.instant = Instant.parse(instant);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            if (!ZoneOffset.UTC.equals(zone)) {
                throw new UnsupportedOperationException("Tests use UTC only");
            }
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        void setInstant(String value) {
            instant = Instant.parse(value);
        }
    }
}
