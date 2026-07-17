package com.prompt2prd.analysis.application;

import com.prompt2prd.analysis.domain.CompletenessScore;
import com.prompt2prd.analysis.domain.ProjectStage;
import com.prompt2prd.analysis.domain.ProjectSummary;
import com.prompt2prd.analysis.domain.RequirementDimension;
import com.prompt2prd.analysis.domain.RequirementState;
import com.prompt2prd.model.application.ModelCallContext;
import com.prompt2prd.model.application.ModelCancellationSignal;
import com.prompt2prd.model.application.ModelConnectionRequest;
import com.prompt2prd.model.application.ModelConnectionResult;
import com.prompt2prd.model.application.ModelEndpoint;
import com.prompt2prd.model.application.ModelGateway;
import com.prompt2prd.model.application.StructuredModelRequest;
import com.prompt2prd.model.application.StructuredModelResult;
import com.prompt2prd.model.application.TextModelChunk;
import com.prompt2prd.model.application.TextModelRequest;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequirementAnalyzerTests {

    @Test
    void validatesModelOutputThenMergesRequirementsAndSelectsQuestions() {
        RequirementState initial = emptyState();
        AnalysisModelOutput output = validOutput(List.of(
                question("role-owner", "谁负责维护项目？"),
                question("role-owner", "由谁维护这个项目？")
        ));
        RequirementAnalyzer analyzer = new RequirementAnalyzer(new StubGateway(output));

        RequirementAnalyzer.AnalysisResult result = analyzer.analyze(command(initial)).block();

        assertThat(result).isNotNull();
        assertThat(result.state().requirements()).hasSize(1);
        assertThat(result.state().questions()).hasSize(1);
        assertThat(result.state().questions().getFirst().status().name()).isEqualTo("PENDING");
        assertThat(result.missingInformation()).containsExactly("目标用户尚未确认");
        assertThat(result.suggestedProjectName()).isEqualTo("需求工作台");
        assertThat(result.state().project().completeness()).isBetween(0, 100);
    }

    @Test
    void missingRequiredModelFieldIsRetryableAndInputStateIsUnchanged() {
        RequirementState initial = emptyState();
        AnalysisModelOutput invalid = new AnalysisModelOutput(
                "需求工作台",
                List.of(new AnalysisModelOutput.RequirementCandidate(
                        null, "FEATURE", "", "支持需求分析", "INFERRED")),
                List.of(),
                List.of("目标用户尚未确认")
        );
        RequirementAnalyzer analyzer = new RequirementAnalyzer(new StubGateway(invalid));

        assertThatThrownBy(() -> analyzer.analyze(command(initial)).block())
                .isInstanceOf(AnalysisRetryableException.class);
        assertThat(initial.requirements()).isEmpty();
        assertThat(initial.questions()).isEmpty();
        assertThat(initial.project().completeness()).isZero();
    }

    @Test
    void unknownEnumIsRetryableInsteadOfEnteringTheMerger() {
        RequirementState initial = emptyState();
        AnalysisModelOutput invalid = new AnalysisModelOutput(
                "需求工作台",
                List.of(new AnalysisModelOutput.RequirementCandidate(
                        null, "NOT_A_TYPE", "分析需求", "支持需求分析", "INFERRED")),
                List.of(),
                List.of()
        );

        assertThatThrownBy(() -> new RequirementAnalyzer(new StubGateway(invalid))
                .analyze(command(initial)).block())
                .isInstanceOf(AnalysisRetryableException.class);
        assertThat(initial.requirements()).isEmpty();
    }

    @Test
    void gatewayReceivesCurrentBoundedContextAndStructuredSchema() {
        RequirementState initial = emptyState();
        StubGateway gateway = new StubGateway(validOutput(List.of()));
        RequirementAnalyzer analyzer = new RequirementAnalyzer(gateway);

        analyzer.analyze(command(initial)).block();

        assertThat(gateway.request).isNotNull();
        assertThat(gateway.request.responseType()).isEqualTo(AnalysisModelOutput.class);
        assertThat(gateway.request.outputSchema()).contains("requirements", "questions");
        assertThat(gateway.request.messages()).hasSize(2);
        assertThat(gateway.request.messages().getLast().content()).contains("需求工作台", "zh-CN");
    }

    private RequirementAnalyzer.AnalysisCommand command(RequirementState state) {
        AnalysisContextBuilder.AnalysisContext context = new AnalysisContextBuilder().build(
                state, List.of(), List.of("目标用户尚未确认"), "analysis-output-schema");
        ModelCallContext modelContext = new ModelCallContext(
                UUID.randomUUID().toString(),
                new ModelEndpoint(URI.create("http://localhost:11434"), "fixture-model", "key", Map.of()),
                new ModelCancellationSignal());
        return new RequirementAnalyzer.AnalysisCommand(modelContext, state, context);
    }

    private AnalysisModelOutput validOutput(
            List<AnalysisModelOutput.QuestionCandidate> questions) {
        return new AnalysisModelOutput(
                "需求工作台",
                List.of(new AnalysisModelOutput.RequirementCandidate(
                        null, "FEATURE", "分析需求", "支持结构化需求分析", "INFERRED")),
                questions,
                List.of("目标用户尚未确认")
        );
    }

    private AnalysisModelOutput.QuestionCandidate question(String semanticKey, String text) {
        return new AnalysisModelOutput.QuestionCandidate(
                text,
                "明确维护责任",
                RequirementDimension.ROLES_PERMISSIONS.name(),
                "maintainer",
                semanticKey,
                "TEXT",
                List.of(),
                5,
                5,
                3,
                3
        );
    }

    private RequirementState emptyState() {
        UUID projectId = UUID.randomUUID();
        return new RequirementState(
                new ProjectSummary(projectId, "需求工作台", "zh-CN", ProjectStage.CLARIFYING, 0),
                List.of(), List.of(), List.of(), List.of(),
                new CompletenessScore(0, List.of(), 0, false));
    }

    private static final class StubGateway implements ModelGateway {
        private final AnalysisModelOutput output;
        private StructuredModelRequest<?> request;

        private StubGateway(AnalysisModelOutput output) {
            this.output = output;
        }

        @Override
        public <T> Mono<StructuredModelResult<T>> generateStructured(StructuredModelRequest<T> request) {
            this.request = request;
            return Mono.just(new StructuredModelResult<>(request.responseType().cast(output), "fixture-model"));
        }

        @Override
        public Flux<TextModelChunk> streamText(TextModelRequest request) {
            return Flux.empty();
        }

        @Override
        public Mono<ModelConnectionResult> testConnection(ModelConnectionRequest request) {
            return Mono.just(new ModelConnectionResult("fixture-model", java.time.Duration.ZERO));
        }
    }
}
