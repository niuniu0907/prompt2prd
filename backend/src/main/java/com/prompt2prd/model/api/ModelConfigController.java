package com.prompt2prd.model.api;

import com.prompt2prd.common.api.ApiException;
import com.prompt2prd.common.api.RequestIdWebFilter;
import com.prompt2prd.common.config.ModelProperties;
import com.prompt2prd.model.adapter.ModelProviderPreset;
import com.prompt2prd.model.application.ModelCallContext;
import com.prompt2prd.model.application.ModelCancellationSignal;
import com.prompt2prd.model.application.ModelConnectionRequest;
import com.prompt2prd.model.application.ModelConnectionResult;
import com.prompt2prd.model.application.ModelEndpoint;
import com.prompt2prd.model.application.ModelGateway;
import com.prompt2prd.model.application.ModelGatewayException;
import com.prompt2prd.model.domain.ModelConfig;
import com.prompt2prd.quota.ClientIpDigest;
import com.prompt2prd.quota.QuotaService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/model-config")
public class ModelConfigController {

    private final ModelGateway modelGateway;
    private final ModelProperties modelProperties;
    private final QuotaService quotaService;
    private final ClientIpDigest clientIpDigest;

    public ModelConfigController(
            ModelGateway modelGateway,
            ModelProperties modelProperties,
            QuotaService quotaService,
            ClientIpDigest clientIpDigest) {
        this.modelGateway = modelGateway;
        this.modelProperties = modelProperties;
        this.quotaService = quotaService;
        this.clientIpDigest = clientIpDigest;
    }

    /** Returns capability metadata only; the configured system credential never leaves the server. */
    @GetMapping
    public ModelCapabilitiesResponse capabilities() {
        return new ModelCapabilitiesResponse(modelProperties.systemKeyAvailable());
    }

    @PostMapping("/test")
    public Mono<ModelConnectionResponse> testConnection(
            @Valid @RequestBody TestModelConnectionRequest request,
            ServerWebExchange exchange) {
        return Mono.deferContextual(contextView -> {
            quotaService.checkFrequency(clientIpDigest.from(exchange));
            String requestId = contextView.getOrDefault(
                    RequestIdWebFilter.REQUEST_ID_KEY,
                    UUID.randomUUID().toString());
            ModelCancellationSignal cancellation = new ModelCancellationSignal();
            ModelConnectionRequest gatewayRequest = new ModelConnectionRequest(new ModelCallContext(
                    requestId,
                    toEndpoint(request),
                    cancellation));
            quotaService.acquireUpstreamCalls(request.keySource(), 1);
            return modelGateway.testConnection(gatewayRequest)
                    .map(result -> ModelConnectionResponse.success(request.keySource(), result))
                    .doOnCancel(cancellation::cancel);
        }).onErrorMap(ModelGatewayException.class, this::toApiException);
    }

    private ModelEndpoint toEndpoint(TestModelConnectionRequest request) {
        try {
            ModelConfig credential = modelProperties.resolve(request.keySource(), request.apiKey());
            URI baseUrl = switch (request.provider()) {
                case OPENAI -> ModelProviderPreset.OPENAI.baseUrl();
                case DEEPSEEK -> ModelProviderPreset.DEEPSEEK.baseUrl();
                case QWEN -> ModelProviderPreset.QWEN.baseUrl();
                case CUSTOM -> customBaseUrl(request.baseUrl());
            };
            return new ModelEndpoint(
                    baseUrl,
                    request.model(),
                    credential.apiKey(),
                    request.parameters());
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new ApiException.BadRequest("Selected model configuration is invalid");
        }
    }

    private URI customBaseUrl(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Custom base URL must not be blank");
        }
        return URI.create(value.trim());
    }

    private ApiException toApiException(ModelGatewayException exception) {
        return switch (exception.kind()) {
            case UNREACHABLE -> new ApiException.ServiceUnavailable("Model service is unreachable", exception);
            case AUTHENTICATION -> new ApiException.Unauthorized("Model authentication failed");
            case MODEL_NOT_FOUND -> new ApiException.ModelNotFound("Model is unavailable");
            case RATE_LIMITED -> new ApiException.RateLimitExceeded("Model service rate limit reached");
            case FORMAT_INCOMPATIBLE -> new ApiException.FormatIncompatible(
                    "Model response is incompatible", exception);
            case TIMEOUT -> new ApiException.RequestTimeout("Model connection test timed out");
            case CANCELLED, INTERNAL -> new ApiException.Internal("Model connection test failed", exception);
        };
    }

    public record ModelCapabilitiesResponse(boolean systemKeyAvailable) {
    }

    public record TestModelConnectionRequest(
            @NotNull ModelConfig.KeySource keySource,
            @NotNull Provider provider,
            String baseUrl,
            @NotBlank String model,
            String apiKey,
            Map<String, Object> parameters) {

        public TestModelConnectionRequest {
            parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
        }
    }

    public record ModelConnectionResponse(
            boolean success,
            ModelConfig.KeySource keySource,
            String model,
            long latencyMs) {

        static ModelConnectionResponse success(
                ModelConfig.KeySource keySource,
                ModelConnectionResult result) {
            Duration latency = result.latency();
            return new ModelConnectionResponse(true, keySource, result.model(), latency.toMillis());
        }
    }

    public enum Provider {
        OPENAI,
        DEEPSEEK,
        QWEN,
        CUSTOM
    }
}
