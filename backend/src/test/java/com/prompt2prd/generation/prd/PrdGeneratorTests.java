package com.prompt2prd.generation.prd;

import com.prompt2prd.analysis.domain.ClarificationAnswer;
import com.prompt2prd.analysis.domain.ClarificationOption;
import com.prompt2prd.analysis.domain.ClarificationQuestion;
import com.prompt2prd.analysis.domain.RequirementItem;
import com.prompt2prd.analysis.domain.QuestionInputType;
import com.prompt2prd.analysis.domain.QuestionStatus;
import com.prompt2prd.analysis.domain.RequirementDimension;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PrdGeneratorTests {

    @Test
    void createsFinalPlanWithAllSectionsForEligibleState() {
        PrdGenerator generator = new PrdGenerator(new CapturingGateway());
        PrdGenerationPlan plan = generator.plan(PrdTestFixtures.finalState(), List.of(), null);

        assertThat(plan.mode()).isEqualTo(PrdGenerationPlan.Mode.FINAL);
        assertThat(plan.sections()).hasSize(12);
        assertThat(plan.selectedArchitecture()).isNull();
        assertThat(plan.missingItems()).isEmpty();
    }

    @Test
    void marksLowCompletenessAndCoreConflictAsDraftWithoutBlockingOnArchitecture() {
        PrdGenerator generator = new PrdGenerator(new CapturingGateway());
        PrdGenerationPlan plan = generator.plan(
                PrdTestFixtures.state(65, false, true, List.of()), List.of("缺少支付规则"), null);

        assertThat(plan.mode()).isEqualTo(PrdGenerationPlan.Mode.DRAFT);
        assertThat(plan.missingItems())
                .contains("存在未解决的核心冲突，相关章节必须标记为待确认或冲突。", "缺少支付规则")
                .doesNotContain("尚未确认主架构。");
    }

    @Test
    void pendingRequirementsEnterPromptWithPendingStatusAndInputStateIsUnchanged() {
        RequirementItem pending = PrdTestFixtures.item(RequirementType.ASSUMPTION,
                "未确认退款", "secret-pending-content", RequirementStatus.PENDING, Map.of());
        var state = PrdTestFixtures.state(90, true, false, List.of(pending));
        List<RequirementItem> before = List.copyOf(state.requirements());
        CapturingGateway gateway = new CapturingGateway();
        PrdGenerator generator = new PrdGenerator(gateway);
        PrdGenerationPlan plan = generator.plan(state, List.of(), "acceptance-criteria");
        generator.streamSection(context(), plan.sections().getFirst()).collectList().block();

        assertThat(plan.sections()).singleElement().satisfies(section ->
                assertThat(section.definition().key()).isEqualTo(PrdDefinition.SectionKey.ACCEPTANCE_CRITERIA));
        assertThat(gateway.lastRequest.messages().toString())
                .contains("secret-pending-content", "待确认：未确认退款", "MUST NOT");
        assertThat(state.requirements()).containsExactlyElementsOf(before);
    }

    @Test
    void savedClarificationAnswersEnterPrdPromptAsDraftEvidence() {
        UUID questionId = UUID.fromString("20000000-0000-4000-8000-000000000001");
        UUID batchId = UUID.fromString("20000000-0000-4000-8000-000000000002");
        UUID optionId = UUID.fromString("20000000-0000-4000-8000-000000000003");
        ClarificationQuestion question = new ClarificationQuestion(
                questionId,
                PrdTestFixtures.PROJECT_ID,
                batchId,
                "平台是否需要在线支付？",
                "影响订单和退款规则",
                RequirementDimension.BUSINESS_RULES,
                "payment.enabled",
                "payment-enabled",
                QuestionInputType.SINGLE_SELECT,
                List.of(new ClarificationOption(optionId, "需要在线支付", "需要支付流程", true)),
                5,
                QuestionStatus.ANSWERED,
                PrdTestFixtures.NOW,
                PrdTestFixtures.NOW);
        ClarificationAnswer answer = new ClarificationAnswer(
                UUID.fromString("20000000-0000-4000-8000-000000000004"),
                PrdTestFixtures.PROJECT_ID,
                questionId,
                List.of(optionId),
                "先支持微信支付",
                "MVP 只做一种支付",
                false,
                PrdTestFixtures.NOW,
                PrdTestFixtures.NOW);
        RequirementState source = PrdTestFixtures.state(40, false, false, List.of());
        RequirementState state = new RequirementState(
                source.project(), source.requirements(), List.of(question), List.of(answer),
                source.conflicts(), source.completeness());
        CapturingGateway gateway = new CapturingGateway();
        PrdGenerator generator = new PrdGenerator(gateway);
        PrdGenerationPlan plan = generator.plan(state, List.of(), "business-rules");
        generator.streamSection(context(), plan.sections().getFirst()).collectList().block();

        assertThat(gateway.lastRequest.messages().toString())
                .contains("Saved clarification answers=")
                .contains("平台是否需要在线支付？ -> 需要在线支付；先支持微信支付；备注：MVP 只做一种支付");
    }

    @Test
    void prdPromptUsesOnlyTechnicalDecisionSummaryNotFullArchitectureCandidate() {
        RequirementItem architecture = PrdTestFixtures.item(RequirementType.TECHNICAL_CONSTRAINT,
                "Vue 3 + Spring Boot 单体",
                "前端 Vue 3；后端 Spring Boot；部署 单容器",
                RequirementStatus.CONFIRMED,
                Map.of(
                        "kind", "ARCHITECTURE_CANDIDATE",
                        "candidate", Map.of(
                                "totalScore", "30/35",
                                "unselectedReasons", List.of("备选架构学习成本更高"),
                                "scores", Map.of("LEARNING_COST", 5))));
        var state = PrdTestFixtures.state(90, false, false, List.of(architecture));
        PrdGenerator generator = new PrdGenerator(new CapturingGateway());
        PrdGenerationPlan plan = generator.plan(state, List.of(), "data-requirements");

        assertThat(plan.sections()).singleElement().satisfies(section -> {
            assertThat(section.prompt())
                    .doesNotContain("Optional technical plan status=")
                    .doesNotContain("前端 Vue 3；后端 Spring Boot；部署 单容器")
                    .doesNotContain("30/35", "unselectedReasons", "备选架构", "LEARNING_COST");
        });
    }

    private static ModelCallContext context() {
        return new ModelCallContext(UUID.randomUUID().toString(),
                new ModelEndpoint(URI.create("http://localhost:11434"), "fixture", "key", Map.of()),
                new ModelCancellationSignal());
    }

    static final class CapturingGateway implements ModelGateway {
        private TextModelRequest lastRequest;

        @Override public <T> Mono<StructuredModelResult<T>> generateStructured(StructuredModelRequest<T> request) {
            return Mono.empty();
        }
        @Override public Flux<TextModelChunk> streamText(TextModelRequest request) {
            lastRequest = request;
            return Flux.just(new TextModelChunk(request.context().requestId(), 1, "正文"));
        }
        @Override public Mono<ModelConnectionResult> testConnection(ModelConnectionRequest request) {
            return Mono.just(new ModelConnectionResult("fixture", Duration.ZERO));
        }
    }
}
