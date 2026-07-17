package com.prompt2prd.stream;

import com.prompt2prd.common.config.GenerationProperties;
import com.prompt2prd.model.application.ModelGatewayException;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;

import java.time.Duration;
import java.util.Objects;
import java.util.Set;

/**
 * Retry policy for model-backed reactive streams.
 * <p>
 * Only network-level / transient failures are retried. Authentication,
 * parameter, format, and cancellation errors are surfaced immediately
 * without retry — there is no infinite retry path.
 * <p>
 * Uses Reactor's {@link RetryBackoffSpec} with exponential backoff so
 * the policy composes naturally with {@code Flux.retryWhen(...)}.
 */
public final class StreamRetryConfig {

    private static final Set<ModelGatewayException.Kind> RETRYABLE_KINDS = Set.of(
            ModelGatewayException.Kind.UNREACHABLE,
            ModelGatewayException.Kind.TIMEOUT,
            ModelGatewayException.Kind.RATE_LIMITED
    );

    private final GenerationProperties properties;

    public StreamRetryConfig(GenerationProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    /**
     * Returns whether the given failure is eligible for retry.
     * Authentication, model-not-found, format-incompatible, cancellation,
     * and internal errors are never retried.
     */
    public static boolean isRetryable(Throwable error) {
        if (error instanceof ModelGatewayException gateway) {
            return RETRYABLE_KINDS.contains(gateway.kind());
        }
        return false;
    }

    /**
     * Builds a Reactor {@link Retry} spec that respects the configured
     * maximum retries and backoff duration. Only retryable errors trigger
     * a retry; non-retryable errors propagate immediately.
     */
    public RetryBackoffSpec retrySpec() {
        return Retry.backoff(properties.maxRetries(), properties.retryBackoff())
                .maxBackoff(maxBackoff())
                .filter(StreamRetryConfig::isRetryable)
                .onRetryExhaustedThrow((spec, signal) -> {
                    Throwable failure = signal.failure();
                    if (failure instanceof ModelGatewayException gateway) {
                        return gateway;
                    }
                    return new ModelGatewayException(
                            ModelGatewayException.Kind.INTERNAL,
                            "Retries exhausted after " + properties.maxRetries() + " attempts",
                            failure);
                });
    }

    /** Absolute ceiling on backoff to prevent runaway delays. */
    private Duration maxBackoff() {
        long ceiling = properties.retryBackoff().toMillis() * (1L << Math.min(properties.maxRetries(), 5));
        return Duration.ofMillis(Math.min(ceiling, Duration.ofSeconds(30).toMillis()));
    }
}
