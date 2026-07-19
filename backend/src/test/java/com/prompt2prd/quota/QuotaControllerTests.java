package com.prompt2prd.quota;

import com.github.benmanes.caffeine.cache.Ticker;
import com.prompt2prd.common.config.ModelProperties;
import com.prompt2prd.model.domain.ModelConfig;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

class QuotaControllerTests {

    @Test
    void returnsZeroRemainingValuesWhenSystemKeyModeIsDisabled() {
        Fixture fixture = fixture(false);

        fixture.client().get()
                .uri("/api/quota")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.systemKeyAvailable").isEqualTo(false)
                .jsonPath("$.analysisRemaining").isEqualTo(0)
                .jsonPath("$.fullPrdRemaining").isEqualTo(0)
                .jsonPath("$.globalCallsRemaining").isEqualTo(0)
                .jsonPath("$.ipDigest").doesNotExist();
    }

    @Test
    void returnsTheCallersAccurateRemainingSystemAllowance() {
        Fixture fixture = fixture(true);
        String digest = fixture.digest().digestAddress("unknown");
        fixture.service().beginOperation(
                digest, ModelConfig.KeySource.SYSTEM, QuotaOperation.ANALYSIS);
        fixture.service().acquireUpstreamCalls(ModelConfig.KeySource.SYSTEM, 2);

        fixture.client().get()
                .uri("/api/quota")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.systemKeyAvailable").isEqualTo(true)
                .jsonPath("$.analysisRemaining").isEqualTo(2)
                .jsonPath("$.fullPrdRemaining").isEqualTo(1)
                .jsonPath("$.globalCallsRemaining").isEqualTo(8)
                .jsonPath("$.ipDigest").doesNotExist();
    }

    private Fixture fixture(boolean enabled) {
        QuotaProperties properties = new QuotaProperties(3, 1, 10, 30, 100, "test-salt");
        ModelProperties models = enabled
                ? ModelProperties.fromEnvironment(Map.of(
                        ModelProperties.SYSTEM_KEY_ENABLED_ENV, "true",
                        ModelProperties.SYSTEM_API_KEY_ENV, "test-only-key"))
                : ModelProperties.fromEnvironment(Map.of());
        QuotaService service = new QuotaService(
                new CaffeineQuotaStore(properties, Ticker.systemTicker()),
                properties,
                models,
                Clock.fixed(Instant.parse("2026-07-17T12:00:00Z"), ZoneOffset.UTC));
        ClientIpDigest digest = new ClientIpDigest(properties);
        QuotaController controller = new QuotaController(service, digest);
        WebTestClient client = WebTestClient.bindToController(controller).build();
        return new Fixture(client, service, digest);
    }

    private record Fixture(
            WebTestClient client,
            QuotaService service,
            ClientIpDigest digest) {
    }
}
