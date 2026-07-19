package com.prompt2prd.generation.prd;

import com.prompt2prd.model.application.ModelCallContext;
import com.prompt2prd.model.application.ModelCancellationSignal;
import com.prompt2prd.model.application.ModelConnectionRequest;
import com.prompt2prd.model.application.ModelConnectionResult;
import com.prompt2prd.model.application.ModelEndpoint;
import com.prompt2prd.model.application.ModelGateway;
import com.prompt2prd.model.application.ModelGatewayException;
import com.prompt2prd.model.application.StructuredModelRequest;
import com.prompt2prd.model.application.StructuredModelResult;
import com.prompt2prd.model.application.TextModelChunk;
import com.prompt2prd.model.application.TextModelRequest;
import com.prompt2prd.stream.StreamEvent;
import com.prompt2prd.stream.StreamEventType;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PrdStreamOrchestratorTests {

    @Test
    void preservesChunkOrderAndSectionOwnership() {
        ScenarioGateway gateway = new ScenarioGateway(null, false);
        PrdGenerator generator = new PrdGenerator(gateway);
        PrdGenerationPlan plan = generator.plan(PrdTestFixtures.finalState(), List.of(), "acceptance-criteria");
        ModelCallContext context = context();

        List<StreamEvent> events = new PrdStreamOrchestrator(generator)
                .generate(new PrdStreamOrchestrator.Execution(context.requestId(), context, plan))
                .collectList().block();

        assertThat(events).extracting(StreamEvent::type).containsExactly(
                StreamEventType.SECTION_STARTED,
                StreamEventType.SECTION_DELTA,
                StreamEventType.SECTION_DELTA,
                StreamEventType.SECTION_COMPLETED,
                StreamEventType.GENERATION_COMPLETED);
        assertThat(events).extracting(StreamEvent::eventId).containsExactly(1L, 2L, 3L, 4L, 5L);
        assertThat(events.subList(0, 4)).allSatisfy(event ->
                assertThat(event.data().get("sectionId")).isEqualTo("acceptance-criteria"));
        assertThat(events.get(1).data().get("delta")).isEqualTo("第一段");
        assertThat(events.get(2).data().get("delta")).isEqualTo("第二段");
    }

    @Test
    void oneSectionFailureDoesNotDiscardCompletedSiblings() {
        ScenarioGateway gateway = new ScenarioGateway("business-rules", false);
        PrdGenerator generator = new PrdGenerator(gateway);
        PrdGenerationPlan plan = generator.plan(PrdTestFixtures.finalState(), List.of(), null);
        ModelCallContext context = context();

        List<StreamEvent> events = new PrdStreamOrchestrator(generator)
                .generate(new PrdStreamOrchestrator.Execution(context.requestId(), context, plan))
                .collectList().block();

        assertThat(events).anyMatch(event -> event.type() == StreamEventType.SECTION_FAILED
                && event.data().get("sectionId").equals("business-rules"));
        assertThat(events).anyMatch(event -> event.type() == StreamEventType.SECTION_COMPLETED
                && event.data().get("sectionId").equals("data-requirements"));
        assertThat(events).filteredOn(event -> event.type().terminal()).singleElement()
                .extracting(StreamEvent::type).isEqualTo(StreamEventType.GENERATION_COMPLETED);
    }

    @Test
    void taskLevelModelFailureStopsInsteadOfFailingEverySection() {
        ScenarioGateway gateway = new ScenarioGateway(
                null, false, ModelGatewayException.Kind.AUTHENTICATION);
        PrdGenerator generator = new PrdGenerator(gateway);
        PrdGenerationPlan plan = generator.plan(PrdTestFixtures.finalState(), List.of(), null);
        ModelCallContext context = context();

        List<StreamEvent> events = new PrdStreamOrchestrator(generator)
                .generate(new PrdStreamOrchestrator.Execution(context.requestId(), context, plan))
                .collectList().block();

        assertThat(events).extracting(StreamEvent::type).containsExactly(
                StreamEventType.SECTION_STARTED, StreamEventType.GENERATION_FAILED);
        assertThat(events.getLast().data()).containsEntry("errorCode", "MODEL_AUTHENTICATION_FAILED")
                .containsEntry("retryable", false);
    }

    @Test
    void cancellationProducesOneAbortedTerminalAndNoLaterContent() {
        ScenarioGateway gateway = new ScenarioGateway(null, true);
        PrdGenerator generator = new PrdGenerator(gateway);
        PrdGenerationPlan plan = generator.plan(PrdTestFixtures.finalState(), List.of(), "acceptance-criteria");
        ModelCallContext context = context();

        List<StreamEvent> events = new PrdStreamOrchestrator(generator)
                .generate(new PrdStreamOrchestrator.Execution(context.requestId(), context, plan))
                .collectList().block();

        assertThat(events).extracting(StreamEvent::type).containsExactly(
                StreamEventType.SECTION_STARTED, StreamEventType.GENERATION_ABORTED);
        assertThat(events).filteredOn(event -> event.type().terminal()).hasSize(1);
    }

    private static ModelCallContext context() {
        return new ModelCallContext(UUID.randomUUID().toString(),
                new ModelEndpoint(URI.create("http://localhost:11434"), "fixture", "key", Map.of()),
                new ModelCancellationSignal());
    }

    static final class ScenarioGateway implements ModelGateway {
        private final String failedSection;
        private final boolean cancelled;
        private final ModelGatewayException.Kind failureKind;

        ScenarioGateway(String failedSection, boolean cancelled) {
            this(failedSection, cancelled, ModelGatewayException.Kind.TIMEOUT);
        }

        ScenarioGateway(String failedSection, boolean cancelled, ModelGatewayException.Kind failureKind) {
            this.failedSection = failedSection;
            this.cancelled = cancelled;
            this.failureKind = failureKind;
        }

        @Override public <T> Mono<StructuredModelResult<T>> generateStructured(StructuredModelRequest<T> request) {
            return Mono.empty();
        }
        @Override public Flux<TextModelChunk> streamText(TextModelRequest request) {
            if (cancelled) return Flux.error(new ModelGatewayException(
                    ModelGatewayException.Kind.CANCELLED, "cancelled"));
            String prompt = request.messages().getLast().content();
            if (failedSection != null && prompt.contains("Section=" + failedSection + " |")) {
                return Flux.error(new ModelGatewayException(failureKind, "failed"));
            }
            if (failedSection == null && failureKind == ModelGatewayException.Kind.AUTHENTICATION) {
                return Flux.error(new ModelGatewayException(failureKind, "failed"));
            }
            return Flux.just(
                    new TextModelChunk(request.context().requestId(), 1, "第一段"),
                    new TextModelChunk(request.context().requestId(), 2, "第二段"));
        }
        @Override public Mono<ModelConnectionResult> testConnection(ModelConnectionRequest request) {
            return Mono.just(new ModelConnectionResult("fixture", Duration.ZERO));
        }
    }
}
