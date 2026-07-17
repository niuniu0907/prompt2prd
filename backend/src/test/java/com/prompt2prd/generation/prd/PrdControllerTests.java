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

    @Test
    void validateReturnsOkForConsistentDocument() {
        Map<String, String> sections = new java.util.HashMap<>();
        for (var section : PrdDefinition.sections()) {
            sections.put(section.key().wireName(),
                    "REQ-001 核心功能\nUS-001 用户故事\nBR-001 业务规则\nAPI-001 接口\nPAGE-001 页面\nAC-001 验收条件\nPHASE-001 阶段一");
        }
        sections.put("acceptance", "AC-001 Given 用户已登录 When 点击按钮 Then 显示成功。");
        sections.put("architecture", "Vue 3 + Spring Boot 单体架构。");
        sections.put("implementation-phases", "PHASE-001 基础框架搭建。");

        client.post().uri("/api/generation/prd/validate")
                .bodyValue(Map.of("sections", sections, "confirmedArchitectureId", "arch-1"))
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.valid").isEqualTo(true)
                .jsonPath("$.errors").isArray()
                .jsonPath("$.warnings").isArray();
    }

    @Test
    void validateRejectsIncompleteSections() {
        Map<String, String> minimal = new java.util.HashMap<>();
        minimal.put("coding-agent-guide", "content");
        client.post().uri("/api/generation/prd/validate")
                .bodyValue(Map.of("sections", minimal))
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.valid").isEqualTo(false);
    }

    @Test
    void analyzeChangesReturnsReport() {
        var requirement = new com.prompt2prd.analysis.domain.RequirementItem(
                java.util.UUID.randomUUID(),
                java.util.UUID.fromString("123e4567-e89b-42d3-a456-426614174000"),
                com.prompt2prd.analysis.domain.RequirementType.BUSINESS_RULE,
                "退款规则", "退款时限为 24 小时",
                com.prompt2prd.analysis.domain.RequirementStatus.CONFIRMED,
                com.prompt2prd.analysis.domain.RequirementSourceType.USER_ANSWER,
                null, false, Map.of(),
                java.time.Instant.parse("2026-07-17T00:00:00Z"),
                java.time.Instant.parse("2026-07-17T00:00:00Z"));
        client.post().uri("/api/generation/prd/analyze-changes")
                .bodyValue(Map.of(
                        "sectionKey", "rules-exceptions",
                        "oldContent", "退款时限为 24 小时。",
                        "newContent", "退款时限为 48 小时。",
                        "currentRequirements", List.of(requirement)))
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.syncedChanges").isArray()
                .jsonPath("$.pendingChanges").isArray()
                .jsonPath("$.conflictWarnings").isArray();
    }

    private PrdRequest request() {
        return new PrdRequest(PrdTestFixtures.finalState(), List.of(),
                new PrdModelSettings(ModelConfig.KeySource.USER, PrdModelSettings.Provider.CUSTOM,
                        "http://localhost:11434", "fixture", "user-key", Map.of()));
    }
}
