package com.prompt2prd.generation.flowchart;

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
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FlowchartGeneratorTests {

    @Test
    void generatesMainAndMultipleExceptionFlowsFromConfirmedFactsOnly() {
        UUID exceptionA = UUID.randomUUID();
        UUID exceptionB = UUID.randomUUID();
        StubGateway gateway = new StubGateway(output(
                diagram("main", "主流程", "flowchart TD\nA-->B", List.of()),
                List.of(
                        diagram("exception-a", "支付失败", "flowchart TD\nP-->F", List.of(exceptionA.toString())),
                        diagram("exception-b", "审核拒绝", "flowchart TD\nR-->E", List.of(exceptionB.toString())))));
        FlowchartGenerator generator = new FlowchartGenerator(gateway);

        FlowchartResult result = generator.generate(command(state(List.of(
                requirement(UUID.randomUUID(), RequirementType.USER_STORY, RequirementStatus.CONFIRMED, "用户提交订单"),
                requirement(exceptionA, RequirementType.EXCEPTION_SCENARIO, RequirementStatus.CONFIRMED, "支付失败后订单关闭"),
                requirement(exceptionB, RequirementType.EXCEPTION_SCENARIO, RequirementStatus.CONFIRMED, "审核拒绝后退回申请"),
                requirement(UUID.randomUUID(), RequirementType.BUSINESS_RULE, RequirementStatus.PENDING, "不得进入模型的草稿规则")
        )))).block();

        assertThat(result).isNotNull();
        assertThat(result.mainFlow().status()).isEqualTo(FlowchartResult.Status.GENERATED);
        assertThat(result.exceptionFlows()).hasSize(2);
        assertThat(result.missingInformation()).isEmpty();
        assertThat(gateway.request.messages().getLast().content())
                .contains("用户提交订单", "支付失败后订单关闭")
                .doesNotContain("不得进入模型的草稿规则");
    }

    @Test
    void returnsMissingInformationAndDropsInventedExceptionsWhenNoConfirmedExceptionExists() {
        StubGateway gateway = new StubGateway(output(
                diagram("main", "主流程", "flowchart TD\nA-->B", List.of()),
                List.of(diagram("invented", "虚构事故", "flowchart TD\nX-->Y", List.of(UUID.randomUUID().toString())))));

        FlowchartResult result = new FlowchartGenerator(gateway)
                .generate(command(state(List.of(requirement(UUID.randomUUID(), RequirementType.USER_STORY,
                        RequirementStatus.CONFIRMED, "用户提交申请"))))).block();

        assertThat(result.exceptionFlows()).isEmpty();
        assertThat(result.missingInformation()).singleElement().asString().contains("已确认的异常场景");
    }

    @Test
    void oneDiagramFailureDoesNotDiscardValidSiblings() {
        UUID exceptionA = UUID.randomUUID();
        UUID exceptionB = UUID.randomUUID();
        FlowchartModelOutput.DiagramCandidate failed = new FlowchartModelOutput.DiagramCandidate(
                "exception-b", "库存异常", "", List.of(exceptionB.toString()), "MODEL_DIAGRAM_FAILED");
        StubGateway gateway = new StubGateway(output(
                diagram("main", "主流程", "flowchart TD\nA-->B", List.of()),
                List.of(diagram("exception-a", "支付失败", "flowchart TD\nP-->F", List.of(exceptionA.toString())), failed)));

        FlowchartResult result = new FlowchartGenerator(gateway).generate(command(state(List.of(
                requirement(exceptionA, RequirementType.EXCEPTION_SCENARIO, RequirementStatus.CONFIRMED, "支付失败"),
                requirement(exceptionB, RequirementType.EXCEPTION_SCENARIO, RequirementStatus.CONFIRMED, "库存异常")
        )))).block();

        assertThat(result.mainFlow().status()).isEqualTo(FlowchartResult.Status.GENERATED);
        assertThat(result.exceptionFlows()).extracting(FlowchartResult.DiagramResult::status)
                .containsExactly(FlowchartResult.Status.GENERATED, FlowchartResult.Status.FAILED);
        assertThat(result.exceptionFlows().getLast().errorCode()).isEqualTo("MODEL_DIAGRAM_FAILED");
    }

    @Test
    void doesNotCallModelWithoutAnyConfirmedRequirement() {
        StubGateway gateway = new StubGateway(output(null, List.of()));
        FlowchartResult result = new FlowchartGenerator(gateway).generate(command(state(List.of(
                requirement(UUID.randomUUID(), RequirementType.USER_STORY, RequirementStatus.PENDING, "待确认流程")
        )))).block();

        assertThat(result.mainFlow()).isNull();
        assertThat(result.missingInformation()).isNotEmpty();
        assertThat(gateway.request).isNull();
    }

    private FlowchartModelOutput output(FlowchartModelOutput.DiagramCandidate main,
                                        List<FlowchartModelOutput.DiagramCandidate> exceptions) {
        return new FlowchartModelOutput(main, exceptions);
    }

    private FlowchartModelOutput.DiagramCandidate diagram(String key, String title, String mermaid,
                                                           List<String> sourceIds) {
        return new FlowchartModelOutput.DiagramCandidate(key, title, mermaid, sourceIds, null);
    }

    private FlowchartGenerator.Command command(RequirementState state) {
        return new FlowchartGenerator.Command(new ModelCallContext(
                UUID.randomUUID().toString(),
                new ModelEndpoint(URI.create("http://localhost:11434"), "fixture", "key", Map.of()),
                new ModelCancellationSignal()), state, null);
    }

    private RequirementState state(List<RequirementItem> requirements) {
        UUID projectId = requirements.isEmpty() ? UUID.randomUUID() : requirements.getFirst().projectId();
        List<RequirementItem> normalized = requirements.stream().map(item -> new RequirementItem(
                item.id(), projectId, item.type(), item.title(), item.content(), item.status(), item.sourceType(),
                item.sourceId(), item.locked(), item.metadata(), item.createdAt(), item.updatedAt())).toList();
        return new RequirementState(new ProjectSummary(projectId, "流程图项目", "zh-CN", ProjectStage.FLOWCHART, 80),
                normalized, List.of(), List.of(), List.of(), new CompletenessScore(80, List.of(), 0, false));
    }

    private RequirementItem requirement(UUID id, RequirementType type, RequirementStatus status, String content) {
        UUID projectId = UUID.fromString("123e4567-e89b-42d3-a456-426614174000");
        Instant now = Instant.parse("2026-07-17T00:00:00Z");
        return new RequirementItem(id, projectId, type, content, content, status,
                RequirementSourceType.USER_ANSWER, null, false, Map.of(), now, now);
    }

    private static final class StubGateway implements ModelGateway {
        private final FlowchartModelOutput output;
        private StructuredModelRequest<?> request;
        private StubGateway(FlowchartModelOutput output) { this.output = output; }
        @Override public <T> Mono<StructuredModelResult<T>> generateStructured(StructuredModelRequest<T> request) {
            this.request = request;
            return Mono.just(new StructuredModelResult<>(request.responseType().cast(output), "fixture"));
        }
        @Override public Flux<TextModelChunk> streamText(TextModelRequest request) { return Flux.empty(); }
        @Override public Mono<ModelConnectionResult> testConnection(ModelConnectionRequest request) {
            return Mono.just(new ModelConnectionResult("fixture", Duration.ZERO));
        }
    }
}
