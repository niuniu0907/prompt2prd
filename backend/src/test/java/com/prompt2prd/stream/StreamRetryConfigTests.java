package com.prompt2prd.stream;

import com.prompt2prd.common.config.GenerationProperties;
import com.prompt2prd.model.application.ModelGatewayException;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class StreamRetryConfigTests {

    private static GenerationProperties defaultProperties() {
        return new GenerationProperties(
                Duration.ofSeconds(30), Duration.ofMinutes(5), 3, Duration.ofMillis(10), Duration.ofSeconds(5));
    }

    @Test
    void retryableKindsAreMarkedAsRetryable() {
        assertTrue(StreamRetryConfig.isRetryable(
                new ModelGatewayException(ModelGatewayException.Kind.UNREACHABLE, "down")));
        assertTrue(StreamRetryConfig.isRetryable(
                new ModelGatewayException(ModelGatewayException.Kind.TIMEOUT, "timeout")));
        assertTrue(StreamRetryConfig.isRetryable(
                new ModelGatewayException(ModelGatewayException.Kind.RATE_LIMITED, "429")));
    }

    @Test
    void nonRetryableKindsAreNotRetried() {
        assertFalse(StreamRetryConfig.isRetryable(
                new ModelGatewayException(ModelGatewayException.Kind.AUTHENTICATION, "401")));
        assertFalse(StreamRetryConfig.isRetryable(
                new ModelGatewayException(ModelGatewayException.Kind.MODEL_NOT_FOUND, "404")));
        assertFalse(StreamRetryConfig.isRetryable(
                new ModelGatewayException(ModelGatewayException.Kind.FORMAT_INCOMPATIBLE, "bad json")));
        assertFalse(StreamRetryConfig.isRetryable(
                new ModelGatewayException(ModelGatewayException.Kind.CANCELLED, "cancel")));
        assertFalse(StreamRetryConfig.isRetryable(
                new ModelGatewayException(ModelGatewayException.Kind.INTERNAL, "500")));
    }

    @Test
    void nonGatewayExceptionIsNotRetryable() {
        assertFalse(StreamRetryConfig.isRetryable(new RuntimeException("any")));
    }

    @Test
    void retriesRecoverableErrorsUpToMax() {
        StreamRetryConfig config = new StreamRetryConfig(defaultProperties());
        AtomicInteger attempts = new AtomicInteger();

        Flux<String> stream = Flux.<String>defer(() -> {
            attempts.incrementAndGet();
            return Flux.error(new ModelGatewayException(
                    ModelGatewayException.Kind.UNREACHABLE, "transient"));
        }).retryWhen(config.retrySpec());

        StepVerifier.create(stream)
                .expectErrorSatisfies(error -> {
                    assertTrue(error instanceof ModelGatewayException);
                    assertEquals(ModelGatewayException.Kind.UNREACHABLE,
                            ((ModelGatewayException) error).kind());
                })
                .verify();

        // 1 initial + 3 retries = 4 attempts
        assertEquals(4, attempts.get());
    }

    @Test
    void doesNotRetryNonRecoverableErrors() {
        StreamRetryConfig config = new StreamRetryConfig(defaultProperties());
        AtomicInteger attempts = new AtomicInteger();

        Flux<String> stream = Flux.<String>defer(() -> {
            attempts.incrementAndGet();
            return Flux.error(new ModelGatewayException(
                    ModelGatewayException.Kind.AUTHENTICATION, "bad key"));
        }).retryWhen(config.retrySpec());

        StepVerifier.create(stream)
                .expectErrorSatisfies(error -> {
                    assertTrue(error instanceof ModelGatewayException);
                    assertEquals(ModelGatewayException.Kind.AUTHENTICATION,
                            ((ModelGatewayException) error).kind());
                })
                .verify();

        assertEquals(1, attempts.get());
    }

    @Test
    void succeedsOnRetryAfterTransientFailure() {
        StreamRetryConfig config = new StreamRetryConfig(defaultProperties());
        AtomicInteger attempts = new AtomicInteger();

        Flux<String> stream = Flux.<String>defer(() -> {
            int attempt = attempts.incrementAndGet();
            if (attempt < 3) {
                return Flux.error(new ModelGatewayException(
                        ModelGatewayException.Kind.TIMEOUT, "slow"));
            }
            return Flux.just("ok");
        }).retryWhen(config.retrySpec());

        StepVerifier.create(stream)
                .expectNext("ok")
                .verifyComplete();

        assertEquals(3, attempts.get());
    }

    @Test
    void exhaustsRetriesAndThrowsForPersistentFailure() {
        GenerationProperties props = new GenerationProperties(
                Duration.ofSeconds(30), Duration.ofMinutes(5), 2, Duration.ofMillis(5), Duration.ofSeconds(5));
        StreamRetryConfig config = new StreamRetryConfig(props);
        AtomicInteger attempts = new AtomicInteger();

        Flux<String> stream = Flux.<String>defer(() -> {
            attempts.incrementAndGet();
            return Flux.error(new ModelGatewayException(
                    ModelGatewayException.Kind.UNREACHABLE, "still down"));
        }).retryWhen(config.retrySpec());

        StepVerifier.create(stream)
                .expectError()
                .verify();

        // 1 initial + 2 retries = 3 attempts
        assertEquals(3, attempts.get());
    }

    @Test
    void zeroMaxRetriesPreventsAnyRetry() {
        GenerationProperties props = new GenerationProperties(
                Duration.ofSeconds(30), Duration.ofMinutes(5), 0, Duration.ofMillis(10), Duration.ofSeconds(5));
        StreamRetryConfig config = new StreamRetryConfig(props);
        AtomicInteger attempts = new AtomicInteger();

        Flux<String> stream = Flux.<String>defer(() -> {
            attempts.incrementAndGet();
            return Flux.error(new ModelGatewayException(
                    ModelGatewayException.Kind.UNREACHABLE, "down"));
        }).retryWhen(config.retrySpec());

        StepVerifier.create(stream)
                .expectError()
                .verify();

        assertEquals(1, attempts.get());
    }
}
