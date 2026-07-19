package com.prompt2prd.analysis.api;

import com.prompt2prd.analysis.application.AnalysisContextBuilder;
import com.prompt2prd.analysis.application.AnalysisOrchestrator;
import com.prompt2prd.analysis.application.CompactAnalysisContext;
import com.prompt2prd.analysis.application.CompactPromptBuilder;
import com.prompt2prd.analysis.application.RoundPromptBuilder;
import com.prompt2prd.analysis.application.RoundQuestionGenerator;
import com.prompt2prd.common.api.ApiException;
import com.prompt2prd.common.api.RequestIdWebFilter;
import com.prompt2prd.common.config.ModelProperties;
import com.prompt2prd.model.application.ModelCallContext;
import com.prompt2prd.model.application.ModelCancellationSignal;
import com.prompt2prd.model.application.ModelEndpoint;
import com.prompt2prd.model.domain.ModelConfig;
import com.prompt2prd.quota.ClientIpDigest;
import com.prompt2prd.quota.QuotaOperation;
import com.prompt2prd.quota.QuotaService;
import com.prompt2prd.stream.StreamEvent;
import com.prompt2prd.stream.StreamEventSequence;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/analysis")
public class AnalysisController {

    private static final Logger log = LoggerFactory.getLogger(AnalysisController.class);

    private final AnalysisOrchestrator orchestrator;
    private final RoundQuestionGenerator roundQuestionGenerator;
    private final ModelProperties modelProperties;
    private final QuotaService quotaService;
    private final ClientIpDigest clientIpDigest;

    public AnalysisController(
            AnalysisOrchestrator orchestrator,
            RoundQuestionGenerator roundQuestionGenerator,
            ModelProperties modelProperties,
            QuotaService quotaService,
            ClientIpDigest clientIpDigest) {
        this.orchestrator = orchestrator;
        this.roundQuestionGenerator = roundQuestionGenerator;
        this.modelProperties = modelProperties;
        this.quotaService = quotaService;
        this.clientIpDigest = clientIpDigest;
    }

    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<StreamEvent>> analyze(
            @Valid @RequestBody AnalysisRequest request,
            ServerWebExchange exchange) {
        return stream(request.state(), request.input(), List.of(), request.missingInformation(),
                request.modelSettings(), exchange);
    }

    @PostMapping(path = "/answers", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<StreamEvent>> analyzeAnswers(
            @Valid @RequestBody AnalysisAnswersRequest request,
            ServerWebExchange exchange) {
        List<AnalysisContextBuilder.QuestionAnswerTurn> answers = request.answers().stream()
                .map(answer -> new AnalysisContextBuilder.QuestionAnswerTurn(
                        answer.questionId(), answer.batchId(), answer.question(),
                        answer.answer(), answer.answeredAt()))
                .toList();
        return stream(request.state(), answerAnalysisInput(request), answers,
                request.missingInformation(), request.modelSettings(), exchange);
    }

    @PostMapping(path = "/generate-round")
    public Mono<GenerateRoundResponse> generateRound(
            @Valid @RequestBody GenerateRoundRequest request,
            ServerWebExchange exchange) {
        return Mono.deferContextual(contextView -> {
            String requestId = contextView.getOrDefault(
                    RequestIdWebFilter.REQUEST_ID_KEY, UUID.randomUUID().toString());
            quotaService.beginOperation(
                    clientIpDigest.from(exchange), request.modelSettings().keySource(),
                    com.prompt2prd.quota.QuotaOperation.ANALYSIS);
            quotaService.acquireUpstreamCalls(request.modelSettings().keySource(), 1);
            ModelCancellationSignal cancellation = new ModelCancellationSignal();

            ModelEndpoint endpoint;
            try {
                ModelConfig credential = modelProperties.resolve(
                        request.modelSettings().keySource(), request.modelSettings().apiKey());
                endpoint = new ModelEndpoint(
                        request.modelSettings().provider().resolveBaseUrl(request.modelSettings().baseUrl()),
                        request.modelSettings().model(), credential.apiKey(),
                        request.modelSettings().parameters());
            } catch (RuntimeException exception) {
                return Mono.just(GenerateRoundResponse.error("INVALID_CONFIGURATION",
                        "Selected model configuration is invalid"));
            }

            ModelCallContext modelContext = new ModelCallContext(requestId, endpoint, cancellation);

            // Build compact context using only question texts (no UUIDs/timestamps)
            List<String> currentQuestionTexts = request.currentVisibleQuestions().stream()
                    .map(GenerateRoundRequest.QuestionSummary::text)
                    .toList();
            CompactAnalysisContext compactContext = CompactPromptBuilder.forRoundGeneration(
                    request.state(),
                    request.targetRoundNo(),
                    currentQuestionTexts,
                    request.coveredAreas());

            List<String> targetKeys = computeTargetKeys(
                    request.targetRoundNo(), request.coveredAreas());

            return roundQuestionGenerator.generate(
                            modelContext,
                            request.state().project().id(),
                            request.targetRoundNo(),
                            compactContext,
                            targetKeys,
                            request.coveredAreas())
                    .map(questions -> GenerateRoundResponse.success(
                            request.targetRoundNo(), questions,
                            targetKeys, requestId))
                    .doOnCancel(cancellation::cancel)
                    .onErrorResume(error -> {
                        log.warn("Round generation failed requestId={} errorType={}",
                                requestId, error.getClass().getSimpleName(), error);
                        return Mono.just(GenerateRoundResponse.error(
                                "GENERATION_FAILED",
                                "Round generation failed. Retry or check the model service status. (requestId: " + requestId + ")"));
                    });
        });
    }

    private List<String> computeTargetKeys(int roundNo, List<String> coveredAreas) {
        if (roundNo == 1) {
            return RoundPromptBuilder.roundOneCoverageKeys();
        }
        if (roundNo == 2) {
            return RoundPromptBuilder.roundTwoCoverageKeys();
        }
        return RoundPromptBuilder.nextRoundCoverageKeys(coveredAreas, 5);
    }

    private String answerAnalysisInput(AnalysisAnswersRequest request) {
        StringBuilder input = new StringBuilder("Continue requirement clarification.");
        // Only include supplemental input, not the full original document.
        // The project summary and confirmed facts are already in the compact context.
        if (request.supplementalInput() != null && !request.supplementalInput().isBlank()) {
            input.append("\nThis round supplemental idea:\n").append(request.supplementalInput());
        }
        return input.toString();
    }

    private Flux<ServerSentEvent<StreamEvent>> stream(
            com.prompt2prd.analysis.domain.RequirementState state,
            String input,
            List<AnalysisContextBuilder.QuestionAnswerTurn> answers,
            List<String> gaps,
            AnalysisModelSettings settings,
            ServerWebExchange exchange) {
        return Flux.deferContextual(context -> {
            String requestId = context.getOrDefault(
                    RequestIdWebFilter.REQUEST_ID_KEY, UUID.randomUUID().toString());
            quotaService.beginOperation(
                    clientIpDigest.from(exchange), settings.keySource(), QuotaOperation.ANALYSIS);
            quotaService.acquireUpstreamCalls(settings.keySource(), 1);
            ModelCancellationSignal cancellation = new ModelCancellationSignal();

            ModelEndpoint endpoint;
            try {
                endpoint = toEndpoint(settings);
            } catch (RuntimeException exception) {
                StreamEventSequence fallback = new StreamEventSequence(requestId);
                return Flux.just(toSse(fallback.next(
                        com.prompt2prd.stream.StreamEventType.GENERATION_FAILED,
                        java.util.Map.of("errorCode", "INVALID_CONFIGURATION",
                                "retryable", false))));
            }

            ModelCallContext modelContext = new ModelCallContext(requestId, endpoint, cancellation);
            AnalysisOrchestrator.AnalysisExecution execution = new AnalysisOrchestrator.AnalysisExecution(
                    requestId, modelContext, state, input, answers, gaps);
            return orchestrator.analyze(execution).map(this::toSse);
        });
    }

    private ServerSentEvent<StreamEvent> toSse(StreamEvent event) {
        return ServerSentEvent.<StreamEvent>builder(event)
                .id(Long.toString(event.eventId()))
                .event(event.type().wireName())
                .build();
    }

    private ModelEndpoint toEndpoint(AnalysisModelSettings settings) {
        try {
            ModelConfig credential = modelProperties.resolve(settings.keySource(), settings.apiKey());
            return new ModelEndpoint(settings.provider().resolveBaseUrl(settings.baseUrl()),
                    settings.model(), credential.apiKey(), settings.parameters());
        } catch (RuntimeException exception) {
            throw new ApiException.BadRequest("Selected model configuration is invalid");
        }
    }
}
