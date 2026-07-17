package com.prompt2prd.generation.prd;

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
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/generation/prd")
public final class PrdController {

    private final PrdGenerator generator;
    private final PrdStreamOrchestrator orchestrator;
    private final ModelProperties modelProperties;
    private final QuotaService quotaService;
    private final ClientIpDigest clientIpDigest;

    public PrdController(
            PrdGenerator generator,
            PrdStreamOrchestrator orchestrator,
            ModelProperties modelProperties,
            QuotaService quotaService,
            ClientIpDigest clientIpDigest) {
        this.generator = generator;
        this.orchestrator = orchestrator;
        this.modelProperties = modelProperties;
        this.quotaService = quotaService;
        this.clientIpDigest = clientIpDigest;
    }

    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<StreamEvent>> generateAll(
            @Valid @RequestBody PrdRequest request,
            ServerWebExchange exchange) {
        return stream(request, null, true, exchange);
    }

    @PostMapping(path = "/sections/{sectionId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<StreamEvent>> generateSection(
            @PathVariable String sectionId,
            @Valid @RequestBody PrdRequest request,
            ServerWebExchange exchange) {
        return stream(request, sectionId, false, exchange);
    }

    private Flux<ServerSentEvent<StreamEvent>> stream(
            PrdRequest request,
            String sectionId,
            boolean full,
            ServerWebExchange exchange) {
        return Flux.deferContextual(context -> {
            String requestId = context.getOrDefault(
                    RequestIdWebFilter.REQUEST_ID_KEY, UUID.randomUUID().toString());
            PrdGenerationPlan plan;
            try {
                plan = generator.plan(request.state(), request.missingInformation(), sectionId);
            } catch (IllegalArgumentException exception) {
                throw new ApiException.BadRequest(exception.getMessage());
            }
            String ipDigest = clientIpDigest.from(exchange);
            if (full) {
                quotaService.beginOperation(ipDigest, request.modelSettings().keySource(), QuotaOperation.FULL_PRD);
            } else {
                quotaService.checkFrequency(ipDigest);
            }
            quotaService.acquireUpstreamCalls(request.modelSettings().keySource(), plan.sections().size());
            ModelCancellationSignal cancellation = new ModelCancellationSignal();
            ModelCallContext modelContext = new ModelCallContext(
                    requestId, toEndpoint(request.modelSettings()), cancellation);
            PrdStreamOrchestrator.Execution execution = new PrdStreamOrchestrator.Execution(
                    requestId, modelContext, plan);
            return orchestrator.generate(execution)
                    .map(this::toSse)
                    .doOnCancel(cancellation::cancel);
        });
    }

    @PostMapping("/validate")
    public Mono<ResponseEntity<PrdValidator.ValidationReport>> validate(
            @Valid @RequestBody PrdValidateRequest request) {
        return Mono.fromCallable(() -> {
            PrdValidator.ValidationReport report = PrdValidator.validate(
                    request.sections(), request.confirmedArchitectureId());
            return ResponseEntity.ok(report);
        });
    }

    @PostMapping("/analyze-changes")
    public Mono<ResponseEntity<PrdChangeAnalyzer.PrdChangeReport>> analyzeChanges(
            @Valid @RequestBody PrdChangeAnalysisRequest request) {
        return Mono.fromCallable(() -> {
            PrdChangeAnalyzer.PrdChangeReport report = PrdChangeAnalyzer.analyze(
                    request.sectionKey(), request.oldContent(), request.newContent(),
                    request.currentRequirements());
            return ResponseEntity.ok(report);
        });
    }

    private ServerSentEvent<StreamEvent> toSse(StreamEvent event) {
        return ServerSentEvent.<StreamEvent>builder(event)
                .id(Long.toString(event.eventId()))
                .event(event.type().wireName())
                .build();
    }

    private ModelEndpoint toEndpoint(PrdModelSettings settings) {
        try {
            ModelConfig credential = modelProperties.resolve(settings.keySource(), settings.apiKey());
            return new ModelEndpoint(settings.provider().resolveBaseUrl(settings.baseUrl()),
                    settings.model(), credential.apiKey(), settings.parameters());
        } catch (RuntimeException exception) {
            throw new ApiException.BadRequest("Selected model configuration is invalid");
        }
    }

    public record PrdValidateRequest(
            @NotEmpty Map<String, String> sections,
            String confirmedArchitectureId) {
    }

    public record PrdChangeAnalysisRequest(
            @NotBlank String sectionKey,
            @NotBlank String oldContent,
            @NotBlank String newContent,
            @NotEmpty List<com.prompt2prd.analysis.domain.RequirementItem> currentRequirements) {
    }
}
