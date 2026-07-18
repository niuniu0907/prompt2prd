package com.prompt2prd.analysis.api;

import com.prompt2prd.analysis.application.AnalysisOrchestrator;
import com.prompt2prd.analysis.domain.CompletenessScore;
import com.prompt2prd.analysis.domain.ProjectStage;
import com.prompt2prd.analysis.domain.ProjectSummary;
import com.prompt2prd.analysis.domain.RequirementState;
import com.prompt2prd.common.config.JacksonConfiguration;
import com.prompt2prd.common.config.ModelProperties;
import com.prompt2prd.model.domain.ModelConfig;
import com.prompt2prd.quota.ClientIpDigest;
import com.prompt2prd.quota.QuotaService;
import com.prompt2prd.stream.StreamEventSequence;
import com.prompt2prd.stream.StreamEventType;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnalysisControllerTests {

    @Test
    void exposesInitialAndAnswerAnalysisAsPostEventStreams() {
        AnalysisOrchestrator orchestrator = mock(AnalysisOrchestrator.class);
        when(orchestrator.analyze(any())).thenAnswer(invocation -> {
            AnalysisOrchestrator.AnalysisExecution execution = invocation.getArgument(0);
            StreamEventSequence sequence = new StreamEventSequence(execution.requestId());
            return Flux.just(
                    sequence.next(StreamEventType.ANALYSIS_STARTED, Map.of("phase", "analysis")),
                    sequence.next(StreamEventType.GENERATION_COMPLETED,
                            Map.of("nextStage", "questions", "finalState", execution.currentState())));
        });
        ModelProperties properties = mock(ModelProperties.class);
        when(properties.resolve(ModelConfig.KeySource.USER, "user-key"))
                .thenReturn(ModelConfig.user("user-key"));
        QuotaService quotaService = mock(QuotaService.class);
        ClientIpDigest digest = mock(ClientIpDigest.class);
        when(digest.from(any())).thenReturn("a".repeat(64));
        AnalysisController controller = new AnalysisController(
                orchestrator, properties, quotaService, digest);
        WebTestClient client = WebTestClient.bindToController(controller).build();
        AnalysisModelSettings settings = new AnalysisModelSettings(
                ModelConfig.KeySource.USER, AnalysisModelSettings.Provider.CUSTOM,
                "http://localhost:11434", "fixture", "user-key", Map.of());

        client.post().uri("/api/analysis")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(new AnalysisRequest(state(), "创建一个需求分析工作台", List.of(), settings))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
                .expectBodyList(String.class);

        client.post().uri("/api/analysis/answers")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(new AnalysisAnswersRequest(state(), List.of(
                        new AnalysisAnswersRequest.AnswerTurn(
                                UUID.randomUUID(), UUID.randomUUID(), "谁使用？", "产品经理",
                                java.time.Instant.parse("2026-07-17T10:00:00Z"))),
                        "我要做需求分析工作台", "还需要支持二次补充想法",
                        List.of(), settings))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM);

        org.mockito.ArgumentCaptor<AnalysisOrchestrator.AnalysisExecution> executionCaptor =
                org.mockito.ArgumentCaptor.forClass(AnalysisOrchestrator.AnalysisExecution.class);
        verify(orchestrator, atLeastOnce()).analyze(executionCaptor.capture());
        org.assertj.core.api.Assertions.assertThat(executionCaptor.getAllValues().getLast().currentInput())
                .contains("Original project idea")
                .contains("我要做需求分析工作台")
                .contains("This round supplemental idea")
                .contains("还需要支持二次补充想法");
    }

    @Test
    void decodesRawJsonRecordRequestAsEventStream() {
        AnalysisOrchestrator orchestrator = mock(AnalysisOrchestrator.class);
        when(orchestrator.analyze(any())).thenAnswer(invocation -> {
            AnalysisOrchestrator.AnalysisExecution execution = invocation.getArgument(0);
            StreamEventSequence sequence = new StreamEventSequence(execution.requestId());
            return Flux.just(sequence.next(StreamEventType.ANALYSIS_STARTED, Map.of("phase", "analysis")));
        });
        ModelProperties properties = mock(ModelProperties.class);
        when(properties.resolve(ModelConfig.KeySource.USER, "user-key"))
                .thenReturn(ModelConfig.user("user-key"));
        QuotaService quotaService = mock(QuotaService.class);
        ClientIpDigest digest = mock(ClientIpDigest.class);
        when(digest.from(any())).thenReturn("a".repeat(64));
        AnalysisController controller = new AnalysisController(
                orchestrator, properties, quotaService, digest);
        JacksonConfiguration jacksonConfiguration = new JacksonConfiguration();
        WebTestClient client = WebTestClient.bindToController(controller)
                .httpMessageCodecs(jacksonConfiguration::configureHttpMessageCodecs)
                .build();

        client.post().uri("/api/analysis")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue("""
                        {
                          "state": {
                            "project": {
                              "id": "11111111-1111-4111-8111-111111111111",
                              "name": "分析工作台",
                              "language": "zh-CN",
                              "stage": "CLARIFYING",
                              "completeness": 0
                            },
                            "requirements": [],
                            "questions": [],
                            "answers": [],
                            "conflicts": [],
                            "completeness": {
                              "total": 0,
                              "dimensions": [],
                              "pendingCount": 0,
                              "hasCoreConflict": false
                            }
                          },
                          "input": "创建一个需求分析工作台",
                          "missingInformation": [],
                          "modelSettings": {
                            "keySource": "USER",
                            "provider": "CUSTOM",
                            "baseUrl": "http://localhost:11434",
                            "model": "fixture",
                            "apiKey": "user-key",
                            "parameters": {}
                          }
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
                .expectBody(String.class)
                .value(body -> org.assertj.core.api.Assertions.assertThat(body)
                        .contains("event:analysis_started")
                        .contains("\"type\":\"analysis_started\"")
                        .containsPattern("\"timestamp\":\"\\d{4}-\\d{2}-\\d{2}T"));
    }

    private RequirementState state() {
        return new RequirementState(
                new ProjectSummary(UUID.randomUUID(), "分析工作台", "zh-CN", ProjectStage.CLARIFYING, 0),
                List.of(), List.of(), List.of(), List.of(),
                new CompletenessScore(0, List.of(), 0, false));
    }
}
