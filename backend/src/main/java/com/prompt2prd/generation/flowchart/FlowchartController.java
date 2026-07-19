package com.prompt2prd.generation.flowchart;

import com.prompt2prd.common.api.ApiException;
import com.prompt2prd.common.api.RequestIdWebFilter;
import com.prompt2prd.common.config.ModelProperties;
import com.prompt2prd.model.application.ModelCallContext;
import com.prompt2prd.model.application.ModelCancellationSignal;
import com.prompt2prd.model.application.ModelEndpoint;
import com.prompt2prd.model.domain.ModelConfig;
import com.prompt2prd.quota.ClientIpDigest;
import com.prompt2prd.quota.QuotaService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/api/generation/flowchart")
public final class FlowchartController {

    private final FlowchartGenerator generator;
    private final ModelProperties modelProperties;
    private final QuotaService quotaService;
    private final ClientIpDigest clientIpDigest;

    public FlowchartController(FlowchartGenerator generator, ModelProperties modelProperties,
                               QuotaService quotaService, ClientIpDigest clientIpDigest) {
        this.generator = generator;
        this.modelProperties = modelProperties;
        this.quotaService = quotaService;
        this.clientIpDigest = clientIpDigest;
    }

    @PostMapping
    public Mono<FlowchartResult> generate(
            @Valid @RequestBody FlowchartRequest request,
            ServerWebExchange exchange) {
        return Mono.deferContextual(context -> {
            String requestId = context.getOrDefault(
                    RequestIdWebFilter.REQUEST_ID_KEY, UUID.randomUUID().toString());
            FlowchartModelSettings settings = request.modelSettings();
            quotaService.checkFrequency(clientIpDigest.from(exchange));
            boolean hasConfirmedInput = request.state().requirements().stream()
                    .anyMatch(item -> item.status() == com.prompt2prd.analysis.domain.RequirementStatus.CONFIRMED);
            if (hasConfirmedInput) quotaService.acquireUpstreamCalls(settings.keySource(), 1);
            ModelCallContext modelContext = new ModelCallContext(
                    requestId, toEndpoint(settings), new ModelCancellationSignal());
            return generator.generate(new FlowchartGenerator.Command(
                    modelContext, request.state(), request.targetKey()));
        });
    }

    private ModelEndpoint toEndpoint(FlowchartModelSettings settings) {
        try {
            ModelConfig credential = modelProperties.resolve(settings.keySource(), settings.apiKey());
            return new ModelEndpoint(settings.provider().resolveBaseUrl(settings.baseUrl()),
                    settings.model(), credential.apiKey(), settings.parameters());
        } catch (RuntimeException exception) {
            throw new ApiException.BadRequest("Selected model configuration is invalid");
        }
    }
}
