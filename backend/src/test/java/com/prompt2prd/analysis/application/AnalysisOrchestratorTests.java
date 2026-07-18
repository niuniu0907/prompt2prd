package com.prompt2prd.analysis.application;

import com.prompt2prd.analysis.domain.ClarificationQuestion;
import com.prompt2prd.analysis.domain.CompletenessScore;
import com.prompt2prd.analysis.domain.ProjectStage;
import com.prompt2prd.analysis.domain.ProjectSummary;
import com.prompt2prd.analysis.domain.RequirementItem;
import com.prompt2prd.analysis.domain.RequirementSourceType;
import com.prompt2prd.analysis.domain.RequirementState;
import com.prompt2prd.analysis.domain.RequirementStatus;
import com.prompt2prd.analysis.domain.RequirementType;
import com.prompt2prd.model.application.ModelCallContext;
import com.prompt2prd.model.application.ModelCancellationSignal;
import com.prompt2prd.model.application.ModelEndpoint;
import com.prompt2prd.model.application.ModelGatewayException;
import com.prompt2prd.stream.StreamEvent;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AnalysisOrchestratorTests {

    @Test
    void emitsAnalysisEventsInDeterministicOrder() {
        RequirementState initial = state(List.of());
        RequirementItem item = requirement(initial.project().id());
        RequirementState completed = state(List.of(item));
        RequirementAnalyzer.AnalysisResult result = new RequirementAnalyzer.AnalysisResult(
                completed, List.of(item), List.of(), List.of(), List.of("缺少角色"), "分析工作台");
        AnalysisOrchestrator orchestrator = new AnalysisOrchestrator(command -> Mono.just(result));

        List<StreamEvent> events = orchestrator.analyze(execution(initial)).collectList().block();

        assertThat(events).isNotNull();
        assertThat(events).extracting(event -> event.type().wireName())
                .containsExactly(
                        "analysis_started", "analysis_progress", "requirement_patch",
                        "completeness_changed", "generation_completed");
        assertThat(events).extracting(StreamEvent::eventId)
                .containsExactly(1L, 2L, 3L, 4L, 5L);
    }

    @Test
    void emitsHeartbeatWithinFiveSecondsWhileModelIsPending() {
        RequirementState initial = state(List.of());
        AnalysisOrchestrator orchestrator = new AnalysisOrchestrator(
                command -> Mono.delay(Duration.ofSeconds(12))
                        .map(ignored -> new RequirementAnalyzer.AnalysisResult(
                                initial, List.of(), List.of(), List.of(), List.of(), "分析工作台")));

        StepVerifier.withVirtualTime(() -> orchestrator.analyze(execution(initial)))
                .expectNextCount(2)
                .thenAwait(Duration.ofSeconds(5))
                .assertNext(event -> assertThat(event.data().get("message")).isEqualTo("分析仍在进行"))
                .thenCancel()
                .verify();
    }

    @Test
    void convertsAnalyzerFailureToOneTerminalFailureEvent() {
        RequirementState initial = state(List.of());
        AnalysisOrchestrator orchestrator = new AnalysisOrchestrator(
                command -> Mono.error(new AnalysisRetryableException("bad output")));

        List<StreamEvent> events = orchestrator.analyze(execution(initial)).collectList().block();

        assertThat(events).extracting(event -> event.type().wireName())
                .containsExactly("analysis_started", "analysis_progress", "generation_failed");
        assertThat(events.getLast().data()).containsEntry("retryable", true);
    }

    @Test
    void exposesModelFailureCodesInTerminalFailureEvent() {
        RequirementState initial = state(List.of());
        AnalysisOrchestrator orchestrator = new AnalysisOrchestrator(
                command -> Mono.error(new ModelGatewayException(
                        ModelGatewayException.Kind.AUTHENTICATION, "bad key")));

        List<StreamEvent> events = orchestrator.analyze(execution(initial)).collectList().block();

        assertThat(events.getLast().type().wireName()).isEqualTo("generation_failed");
        assertThat(events.getLast().data())
                .containsEntry("errorCode", "MODEL_AUTHENTICATION_FAILED")
                .containsEntry("retryable", false);
    }

    private AnalysisOrchestrator.AnalysisExecution execution(RequirementState state) {
        ModelCancellationSignal cancellation = new ModelCancellationSignal();
        String requestId = UUID.randomUUID().toString();
        return new AnalysisOrchestrator.AnalysisExecution(
                requestId,
                new ModelCallContext(requestId,
                        new ModelEndpoint(URI.create("http://localhost:11434"), "fixture", "key", Map.of()),
                        cancellation),
                state,
                "创建一个需求分析工作台",
                List.of(),
                List.of("缺少角色"));
    }

    private RequirementState state(List<RequirementItem> requirements) {
        UUID projectId = requirements.isEmpty() ? UUID.randomUUID() : requirements.getFirst().projectId();
        return new RequirementState(
                new ProjectSummary(projectId, "分析工作台", "zh-CN", ProjectStage.CLARIFYING,
                        requirements.isEmpty() ? 0 : 40),
                requirements, List.<ClarificationQuestion>of(), List.of(), List.of(),
                new CompletenessScore(requirements.isEmpty() ? 0 : 40, List.of(), 0, false));
    }

    private RequirementItem requirement(UUID projectId) {
        Instant now = Instant.parse("2026-07-17T10:00:00Z");
        return new RequirementItem(
                UUID.randomUUID(), projectId, RequirementType.FEATURE, "需求分析", "提取结构化需求",
                RequirementStatus.INFERRED, RequirementSourceType.AI_INFERENCE, null,
                false, Map.of("dimension", "FEATURES"), now, now);
    }
}
