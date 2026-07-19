package com.prompt2prd.generation.prd;

import com.prompt2prd.model.application.ModelCallContext;
import com.prompt2prd.model.application.ModelGatewayException;
import com.prompt2prd.stream.StreamEvent;
import com.prompt2prd.stream.StreamEventSequence;
import com.prompt2prd.stream.StreamEventType;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Converts independent section streams into the shared task event protocol. */
@Component
public final class PrdStreamOrchestrator {

    private final PrdGenerator generator;

    public PrdStreamOrchestrator(PrdGenerator generator) {
        this.generator = Objects.requireNonNull(generator, "generator");
    }

    public Flux<StreamEvent> generate(Execution execution) {
        Objects.requireNonNull(execution, "execution");
        return Flux.defer(() -> {
            StreamEventSequence events = new StreamEventSequence(execution.requestId());
            List<String> completed = new ArrayList<>();
            List<String> failed = new ArrayList<>();
            // Serial generation via concatMap so that StreamEventSequence.next()
            // always emits strictly-monotonic IDs. The frontend SSE parser
            // throws OUT_OF_ORDER_EVENT on any gap, which flatMap concurrency
            // can cause when two sections interleave their SECTION_DELTA events.
            Flux<StreamEvent> sections = Flux.fromIterable(execution.plan().sections())
                    .concatMap(
                            section -> streamSection(execution.modelContext(), section, events, completed, failed));
            return sections.concatWith(Mono.fromSupplier(() -> events.next(
                            StreamEventType.GENERATION_COMPLETED,
                            Map.of("nextStage", "PRD_EDITING", "finalState",
                                    finalState(execution.plan(), completed, failed)))))
                    .onErrorResume(this::isCancellation, error -> Mono.just(events.next(
                            StreamEventType.GENERATION_ABORTED,
                            Map.of("reason", "MODEL_CANCELLED", "completedStages", List.copyOf(completed)))))
                    .onErrorResume(error -> Mono.just(events.next(
                            StreamEventType.GENERATION_FAILED,
                            Map.of("errorCode", errorCode(error), "retryable", retryable(error)))));
        });
    }

    private Flux<StreamEvent> streamSection(
            ModelCallContext context,
            PrdGenerationPlan.SectionPlan section,
            StreamEventSequence events,
            List<String> completed,
            List<String> failed) {
        String sectionId = section.definition().key().wireName();
        Flux<StreamEvent> body = generator.streamSection(context, section)
                .map(chunk -> events.next(StreamEventType.SECTION_DELTA,
                        Map.of("sectionId", sectionId, "delta", chunk.content())))
                .concatWith(Mono.fromSupplier(() -> {
                    completed.add(sectionId);
                    return events.next(StreamEventType.SECTION_COMPLETED,
                            Map.of("sectionId", sectionId, "status", "COMPLETED"));
                }))
                .onErrorResume(error -> {
                    if (isCancellation(error)) return Flux.error(error);
                    if (isTaskLevelFailure(error)) return Flux.error(error);
                    failed.add(sectionId);
                    return Flux.just(events.next(StreamEventType.SECTION_FAILED,
                            Map.of("sectionId", sectionId, "errorCode", errorCode(error), "retryable", true)));
                });
        return Flux.concat(Mono.fromSupplier(() -> events.next(StreamEventType.SECTION_STARTED,
                Map.of("sectionId", sectionId, "title", section.definition().title()))), body);
    }

    private Map<String, Object> finalState(
            PrdGenerationPlan plan, List<String> completed, List<String> failed) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("mode", plan.mode().name());
        state.put("missingItems", plan.missingItems());
        state.put("completedSections", List.copyOf(completed));
        state.put("failedSections", List.copyOf(failed));
        return Map.copyOf(state);
    }

    private boolean isCancellation(Throwable error) {
        return error instanceof ModelGatewayException gateway
                && gateway.kind() == ModelGatewayException.Kind.CANCELLED;
    }

    private boolean isTaskLevelFailure(Throwable error) {
        if (!(error instanceof ModelGatewayException gateway)) return false;
        return switch (gateway.kind()) {
            case AUTHENTICATION, MODEL_NOT_FOUND, RATE_LIMITED, UNREACHABLE -> true;
            case FORMAT_INCOMPATIBLE, TIMEOUT, CANCELLED, INTERNAL -> false;
        };
    }

    private String errorCode(Throwable error) {
        if (error instanceof ModelGatewayException gateway) {
            return switch (gateway.kind()) {
                case UNREACHABLE -> "MODEL_UNREACHABLE";
                case AUTHENTICATION -> "MODEL_AUTHENTICATION_FAILED";
                case MODEL_NOT_FOUND -> "MODEL_NOT_FOUND";
                case RATE_LIMITED -> "MODEL_RATE_LIMITED";
                case FORMAT_INCOMPATIBLE -> "MODEL_FORMAT_INCOMPATIBLE";
                case TIMEOUT -> "MODEL_TIMEOUT";
                case CANCELLED -> "MODEL_CANCELLED";
                case INTERNAL -> "MODEL_INTERNAL_ERROR";
            };
        }
        return "SECTION_GENERATION_FAILED";
    }

    private boolean retryable(Throwable error) {
        if (error instanceof ModelGatewayException gateway) {
            return switch (gateway.kind()) {
                case UNREACHABLE, RATE_LIMITED, FORMAT_INCOMPATIBLE, TIMEOUT, INTERNAL -> true;
                case AUTHENTICATION, MODEL_NOT_FOUND, CANCELLED -> false;
            };
        }
        return true;
    }

    public record Execution(
            String requestId,
            ModelCallContext modelContext,
            PrdGenerationPlan plan) {
        public Execution {
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(modelContext, "modelContext");
            Objects.requireNonNull(plan, "plan");
            if (!requestId.equals(modelContext.requestId())) {
                throw new IllegalArgumentException("request IDs must match");
            }
        }
    }
}
