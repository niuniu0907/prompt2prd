package com.prompt2prd.analysis.application;

import com.prompt2prd.analysis.domain.RequirementState;
import com.prompt2prd.model.application.ModelCallContext;
import com.prompt2prd.model.application.ModelGatewayException;
import com.prompt2prd.stream.StreamEvent;
import com.prompt2prd.stream.StreamEventSequence;
import com.prompt2prd.stream.StreamEventType;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class AnalysisOrchestrator {

    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(5);

    private final AnalysisEngine analysisEngine;
    private final AnalysisContextBuilder contextBuilder;

    @Autowired
    public AnalysisOrchestrator(AnalysisEngine analysisEngine) {
        this(analysisEngine, new AnalysisContextBuilder());
    }

    AnalysisOrchestrator(AnalysisEngine analysisEngine, AnalysisContextBuilder contextBuilder) {
        this.analysisEngine = Objects.requireNonNull(analysisEngine, "analysisEngine");
        this.contextBuilder = Objects.requireNonNull(contextBuilder, "contextBuilder");
    }

    public Flux<StreamEvent> analyze(AnalysisExecution execution) {
        Objects.requireNonNull(execution, "execution");
        return Flux.defer(() -> {
            StreamEventSequence sequence = new StreamEventSequence(execution.requestId());
            AnalysisContextBuilder.AnalysisContext context = contextBuilder.build(
                    execution.currentState(), execution.recentAnswers(),
                    execution.missingInformation(), RequirementAnalyzer.OUTPUT_SCHEMA);
            RequirementAnalyzer.AnalysisCommand command = new RequirementAnalyzer.AnalysisCommand(
                    execution.modelContext(), execution.currentState(), context, execution.currentInput());
            Mono<RequirementAnalyzer.AnalysisResult> result = analysisEngine.analyze(command).cache();

            Flux<StreamEvent> heartbeat = Flux.interval(HEARTBEAT_INTERVAL)
                    .takeUntilOther(result)
                    .map(ignored -> sequence.next(StreamEventType.ANALYSIS_PROGRESS,
                            Map.of("progress", 50, "message", "分析仍在进行")));
            Flux<StreamEvent> completed = result.flatMapMany(value -> resultEvents(
                    sequence, execution.currentState(), value));

            return Flux.concat(
                            Flux.just(
                                    sequence.next(StreamEventType.ANALYSIS_STARTED,
                                            Map.of("phase", "analysis")),
                                    sequence.next(StreamEventType.ANALYSIS_PROGRESS,
                                            Map.of("progress", 10, "message", "正在提取需求"))),
                            Flux.merge(heartbeat, completed))
                    .onErrorResume(failure -> Flux.just(sequence.next(
                            StreamEventType.GENERATION_FAILED,
                            Map.of("errorCode", errorCode(failure),
                                    "retryable", retryable(failure)))))
                    .doOnCancel(execution.modelContext().cancellation()::cancel);
        });
    }

    private Flux<StreamEvent> resultEvents(
            StreamEventSequence sequence,
            RequirementState previous,
            RequirementAnalyzer.AnalysisResult result) {
        return Flux.defer(() -> {
            Flux<StreamEvent> requirements = Flux.fromIterable(result.appliedRequirements())
                    .map(item -> sequence.next(StreamEventType.REQUIREMENT_PATCH,
                            Map.of("path", "requirements/" + item.id(),
                                    "operation", "upsert", "value", item)));
            Flux<StreamEvent> questions = Flux.fromIterable(result.selectedQuestions())
                    .map(question -> sequence.next(StreamEventType.QUESTION_CREATED,
                            Map.of("question", question)));
            Flux<StreamEvent> conflicts = Flux.fromIterable(result.createdConflicts())
                    .map(conflict -> sequence.next(StreamEventType.CONFLICT_DETECTED,
                            Map.of("conflict", conflict)));
            Mono<StreamEvent> completeness = Mono.fromSupplier(() -> sequence.next(
                    StreamEventType.COMPLETENESS_CHANGED,
                    Map.of("previous", previous.completeness().total(),
                            "current", result.state().completeness().total(),
                            "missingInformation", result.missingInformation())));
            Mono<StreamEvent> terminal = Mono.fromSupplier(() -> sequence.next(
                    StreamEventType.GENERATION_COMPLETED,
                    Map.of("nextStage", "questions", "finalState", result.state())));
            return Flux.concat(requirements, questions, conflicts, completeness, terminal);
        });
    }

    private String errorCode(Throwable failure) {
        if (failure instanceof AnalysisRetryableException) {
            return "ANALYSIS_OUTPUT_INVALID";
        }
        if (failure instanceof ModelGatewayException gateway) {
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
        return "ANALYSIS_FAILED";
    }

    private boolean retryable(Throwable failure) {
        if (failure instanceof AnalysisRetryableException) {
            return true;
        }
        if (failure instanceof ModelGatewayException gateway) {
            return switch (gateway.kind()) {
                case UNREACHABLE, RATE_LIMITED, TIMEOUT, FORMAT_INCOMPATIBLE, INTERNAL -> true;
                case AUTHENTICATION, MODEL_NOT_FOUND, CANCELLED -> false;
            };
        }
        return false;
    }

    public record AnalysisExecution(
            String requestId,
            ModelCallContext modelContext,
            RequirementState currentState,
            String currentInput,
            List<AnalysisContextBuilder.QuestionAnswerTurn> recentAnswers,
            List<String> missingInformation) {
        public AnalysisExecution {
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(modelContext, "modelContext");
            Objects.requireNonNull(currentState, "currentState");
            if (currentInput == null || currentInput.isBlank()) {
                throw new IllegalArgumentException("currentInput must not be blank");
            }
            currentInput = currentInput.trim();
            recentAnswers = recentAnswers == null ? List.of() : List.copyOf(recentAnswers);
            missingInformation = missingInformation == null ? List.of() : List.copyOf(missingInformation);
            if (!requestId.equals(modelContext.requestId())) {
                throw new IllegalArgumentException("Execution and model request IDs must match");
            }
        }
    }
}
