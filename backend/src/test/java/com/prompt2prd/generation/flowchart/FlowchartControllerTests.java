package com.prompt2prd.generation.flowchart;

import com.prompt2prd.analysis.domain.CompletenessScore;
import com.prompt2prd.analysis.domain.ProjectStage;
import com.prompt2prd.analysis.domain.ProjectSummary;
import com.prompt2prd.analysis.domain.RequirementItem;
import com.prompt2prd.analysis.domain.RequirementSourceType;
import com.prompt2prd.analysis.domain.RequirementState;
import com.prompt2prd.analysis.domain.RequirementStatus;
import com.prompt2prd.analysis.domain.RequirementType;
import com.prompt2prd.common.api.GlobalExceptionHandler;
import com.prompt2prd.common.config.ModelProperties;
import com.prompt2prd.model.application.ModelConnectionRequest;
import com.prompt2prd.model.application.ModelConnectionResult;
import com.prompt2prd.model.application.ModelGateway;
import com.prompt2prd.model.application.StructuredModelRequest;
import com.prompt2prd.model.application.StructuredModelResult;
import com.prompt2prd.model.application.TextModelChunk;
import com.prompt2prd.model.application.TextModelRequest;
import com.prompt2prd.model.domain.ModelConfig;
import com.prompt2prd.quota.ClientIpDigest;
import com.prompt2prd.quota.QuotaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FlowchartControllerTests {

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        FlowchartModelOutput output = new FlowchartModelOutput(
                new FlowchartModelOutput.DiagramCandidate("main", "主流程", "flowchart TD\nA-->B", List.of(), null),
                List.of());
        QuotaService quotaService = mock(QuotaService.class);
        ClientIpDigest clientIpDigest = mock(ClientIpDigest.class);
        when(clientIpDigest.from(any())).thenReturn("a".repeat(64));
        FlowchartController controller = new FlowchartController(
                new FlowchartGenerator(new StubGateway(output)),
                ModelProperties.fromEnvironment(Map.of()), quotaService, clientIpDigest);
        client = WebTestClient.bindToController(controller)
                .controllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void exposesPostGenerationEndpoint() {
        client.post().uri("/api/generation/flowchart")
                .bodyValue(new FlowchartRequest(state(), null,
                        new FlowchartModelSettings(ModelConfig.KeySource.USER,
                                FlowchartModelSettings.Provider.CUSTOM,
                                "http://localhost:11434", "fixture", "user-key", Map.of())))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.mainFlow.status").isEqualTo("GENERATED")
                .jsonPath("$.mainFlow.mermaid").isEqualTo("flowchart TD\nA-->B");
    }

    @Test
    void rejectsMissingState() {
        client.post().uri("/api/generation/flowchart")
                .bodyValue(Map.of("modelSettings", Map.of(
                        "keySource", "USER", "provider", "CUSTOM", "baseUrl", "http://localhost:11434",
                        "model", "fixture", "apiKey", "user-key")))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.code").isEqualTo("BAD_REQUEST");
    }

    private RequirementState state() {
        UUID projectId = UUID.fromString("123e4567-e89b-42d3-a456-426614174000");
        Instant now = Instant.parse("2026-07-17T00:00:00Z");
        RequirementItem item = new RequirementItem(UUID.randomUUID(), projectId, RequirementType.USER_STORY,
                "提交申请", "用户提交申请后进入审核", RequirementStatus.CONFIRMED,
                RequirementSourceType.USER_ANSWER, null, false, Map.of(), now, now);
        return new RequirementState(new ProjectSummary(projectId, "流程图项目", "zh-CN", ProjectStage.FLOWCHART, 80),
                List.of(item), List.of(), List.of(), List.of(), new CompletenessScore(80, List.of(), 0, false));
    }

    private record StubGateway(FlowchartModelOutput output) implements ModelGateway {
        @Override public <T> Mono<StructuredModelResult<T>> generateStructured(StructuredModelRequest<T> request) {
            return Mono.just(new StructuredModelResult<>(request.responseType().cast(output), "fixture"));
        }
        @Override public Flux<TextModelChunk> streamText(TextModelRequest request) { return Flux.empty(); }
        @Override public Mono<ModelConnectionResult> testConnection(ModelConnectionRequest request) {
            return Mono.just(new ModelConnectionResult("fixture", Duration.ZERO));
        }
    }
}
