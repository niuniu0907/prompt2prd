package com.prompt2prd.generation.prd;

import com.prompt2prd.analysis.domain.RequirementItem;
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
        assertThat(plan.sections()).hasSize(17);
        assertThat(plan.selectedArchitecture()).isNotNull();
        assertThat(plan.missingItems()).isEmpty();
    }

    @Test
    void marksLowCompletenessMissingArchitectureAndCoreConflictAsDraft() {
        PrdGenerator generator = new PrdGenerator(new CapturingGateway());
        PrdGenerationPlan plan = generator.plan(
                PrdTestFixtures.state(65, false, true, List.of()), List.of("缺少支付规则"), null);

        assertThat(plan.mode()).isEqualTo(PrdGenerationPlan.Mode.DRAFT);
        assertThat(plan.missingItems()).anyMatch(item -> item.contains("65"))
                .contains("尚未确认主架构。", "存在未解决的核心冲突。", "缺少支付规则");
    }

    @Test
    void onlyConfirmedRequirementsEnterPromptAndInputStateIsUnchanged() {
        RequirementItem pending = PrdTestFixtures.item(RequirementType.ASSUMPTION,
                "未确认退款", "secret-pending-content", RequirementStatus.PENDING, Map.of());
        var state = PrdTestFixtures.state(90, true, false, List.of(pending));
        List<RequirementItem> before = List.copyOf(state.requirements());
        CapturingGateway gateway = new CapturingGateway();
        PrdGenerator generator = new PrdGenerator(gateway);
        PrdGenerationPlan plan = generator.plan(state, List.of(), "apis");
        generator.streamSection(context(), plan.sections().getFirst()).collectList().block();

        assertThat(plan.sections()).singleElement().satisfies(section ->
                assertThat(section.definition().key()).isEqualTo(PrdDefinition.SectionKey.APIS));
        assertThat(gateway.lastRequest.messages().toString()).doesNotContain("secret-pending-content")
                .contains("待确认：未确认退款", "MUST NOT");
        assertThat(state.requirements()).containsExactlyElementsOf(before);
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
