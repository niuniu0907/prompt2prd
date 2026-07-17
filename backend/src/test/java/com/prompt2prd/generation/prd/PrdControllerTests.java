package com.prompt2prd.generation.prd;

import com.prompt2prd.common.api.GlobalExceptionHandler;
import com.prompt2prd.common.config.ModelProperties;
import com.prompt2prd.model.domain.ModelConfig;
import com.prompt2prd.quota.ClientIpDigest;
import com.prompt2prd.quota.QuotaOperation;
import com.prompt2prd.quota.QuotaService;
import com.prompt2prd.stream.StreamEvent;
import com.prompt2prd.stream.StreamEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PrdControllerTests {

    private WebTestClient client;
    private QuotaService quotaService;

    @BeforeEach
    void setUp() {
        var gateway = new PrdStreamOrchestratorTests.ScenarioGateway(null, false);
        var generator = new PrdGenerator(gateway);
        quotaService = mock(QuotaService.class);
        ClientIpDigest clientIpDigest = mock(ClientIpDigest.class);
        when(clientIpDigest.from(any())).thenReturn("a".repeat(64));
        var controller = new PrdController(generator, new PrdStreamOrchestrator(generator),
                ModelProperties.fromEnvironment(Map.of()), quotaService, clientIpDigest);
        client = WebTestClient.bindToController(controller)
                .controllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void streamsFullPrdWithUniqueTerminalEvent() {
        List<StreamEvent> events = client.post().uri("/api/generation/prd")
                .bodyValue(request())
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith("text/event-stream")
                .returnResult(StreamEvent.class).getResponseBody().collectList().block();

        assertThat(events).isNotNull();
        assertThat(events).filteredOn(event -> event.type() == StreamEventType.SECTION_STARTED).hasSize(17);
        assertThat(events).filteredOn(event -> event.type().terminal()).singleElement()
                .extracting(StreamEvent::type).isEqualTo(StreamEventType.GENERATION_COMPLETED);
        verify(quotaService).beginOperation("a".repeat(64), ModelConfig.KeySource.USER, QuotaOperation.FULL_PRD);
        verify(quotaService).acquireUpstreamCalls(ModelConfig.KeySource.USER, 17);
    }

    @Test
    void streamsOnlyRequestedSection() {
        List<StreamEvent> events = client.post().uri("/api/generation/prd/sections/apis")
                .bodyValue(request()).exchange().expectStatus().isOk()
                .returnResult(StreamEvent.class).getResponseBody().collectList().block();

        assertThat(events).filteredOn(event -> event.type() == StreamEventType.SECTION_STARTED)
                .singleElement().satisfies(event -> assertThat(event.data().get("sectionId")).isEqualTo("apis"));
        verify(quotaService).checkFrequency("a".repeat(64));
        verify(quotaService).acquireUpstreamCalls(ModelConfig.KeySource.USER, 1);
    }

    @Test
    void rejectsUnknownSection() {
        client.post().uri("/api/generation/prd/sections/unknown")
                .bodyValue(request()).exchange().expectStatus().isBadRequest()
                .expectBody().jsonPath("$.code").isEqualTo("BAD_REQUEST");
    }

    private PrdRequest request() {
        return new PrdRequest(PrdTestFixtures.finalState(), List.of(),
                new PrdModelSettings(ModelConfig.KeySource.USER, PrdModelSettings.Provider.CUSTOM,
                        "http://localhost:11434", "fixture", "user-key", Map.of()));
    }
}
