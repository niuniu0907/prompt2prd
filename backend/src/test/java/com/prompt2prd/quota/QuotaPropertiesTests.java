package com.prompt2prd.quota;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuotaPropertiesTests {

    @Test
    void usesTheDocumentedDefaults() {
        QuotaProperties properties = QuotaProperties.fromEnvironment(Map.of());

        assertThat(properties.analysisDailyLimit()).isEqualTo(3);
        assertThat(properties.fullPrdDailyLimit()).isEqualTo(1);
        assertThat(properties.globalDailyCallLimit()).isEqualTo(100);
        assertThat(properties.requestsPerMinute()).isEqualTo(30);
        assertThat(properties.maxTrackedEntries()).isEqualTo(10_000);
    }

    @Test
    void acceptsDeploymentOverridesAndRedactsTheDigestSalt() {
        QuotaProperties properties = QuotaProperties.fromEnvironment(Map.of(
                QuotaProperties.ANALYSIS_DAILY_LIMIT_ENV, "4",
                QuotaProperties.FULL_PRD_DAILY_LIMIT_ENV, "2",
                QuotaProperties.GLOBAL_DAILY_CALL_LIMIT_ENV, "250",
                QuotaProperties.REQUESTS_PER_MINUTE_ENV, "12",
                QuotaProperties.MAX_TRACKED_ENTRIES_ENV, "500",
                QuotaProperties.IP_DIGEST_SALT_ENV, "deployment-secret-salt"));

        assertThat(properties.analysisDailyLimit()).isEqualTo(4);
        assertThat(properties.fullPrdDailyLimit()).isEqualTo(2);
        assertThat(properties.globalDailyCallLimit()).isEqualTo(250);
        assertThat(properties.requestsPerMinute()).isEqualTo(12);
        assertThat(properties.maxTrackedEntries()).isEqualTo(500);
        assertThat(properties.toString())
                .contains("ipDigestSalt=[REDACTED]")
                .doesNotContain("deployment-secret-salt");
    }

    @Test
    void rejectsNonPositiveOrMalformedLimits() {
        assertThatThrownBy(() -> QuotaProperties.fromEnvironment(Map.of(
                QuotaProperties.GLOBAL_DAILY_CALL_LIMIT_ENV, "0")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> QuotaProperties.fromEnvironment(Map.of(
                QuotaProperties.REQUESTS_PER_MINUTE_ENV, "many")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
