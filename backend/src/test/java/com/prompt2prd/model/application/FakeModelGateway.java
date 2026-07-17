package com.prompt2prd.model.application;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

final class FakeModelGateway implements ModelGateway {

    enum Scenario {
        SUCCESS,
        FORMAT_ERROR,
        DELAYED,
        CANCELLED
    }

    private final Scenario scenario;
    private final Object structuredValue;
    private final List<String> textChunks;
    private final ModelConnectionResult connectionResult;
    private final Duration delay;

    private FakeModelGateway(
            Scenario scenario,
            Object structuredValue,
            List<String> textChunks,
            ModelConnectionResult connectionResult,
            Duration delay) {
        this.scenario = Objects.requireNonNull(scenario, "scenario");
        this.structuredValue = structuredValue;
        this.textChunks = List.copyOf(textChunks);
        this.connectionResult = Objects.requireNonNull(connectionResult, "connectionResult");
        this.delay = Objects.requireNonNull(delay, "delay");
    }

    static FakeModelGateway success(
            Object structuredValue,
            List<String> textChunks,
            ModelConnectionResult connectionResult) {
        return new FakeModelGateway(
                Scenario.SUCCESS,
                structuredValue,
                textChunks,
                connectionResult,
                Duration.ZERO);
    }

    static FakeModelGateway formatError() {
        return new FakeModelGateway(
                Scenario.FORMAT_ERROR,
                null,
                List.of(),
                new ModelConnectionResult("fixture-model", Duration.ZERO),
                Duration.ZERO);
    }

    static FakeModelGateway delayed(
            Duration delay,
            Object structuredValue,
            List<String> textChunks,
            ModelConnectionResult connectionResult) {
        return new FakeModelGateway(
                Scenario.DELAYED,
                structuredValue,
                textChunks,
                connectionResult,
                delay);
    }

    static FakeModelGateway cancelled() {
        return new FakeModelGateway(
                Scenario.CANCELLED,
                null,
                List.of(),
                new ModelConnectionResult("fixture-model", Duration.ZERO),
                Duration.ZERO);
    }

    @Override
    public <T> Mono<StructuredModelResult<T>> generateStructured(
            StructuredModelRequest<T> request) {
        return Mono.defer(() -> {
            if (scenario == Scenario.FORMAT_ERROR) {
                return Mono.error(new ModelGatewayException(
                        ModelGatewayException.Kind.FORMAT_INCOMPATIBLE,
                        "Fake structured output is invalid"));
            }
            if (scenario == Scenario.CANCELLED || request.context().cancellation().isCancelled()) {
                return Mono.error(cancelledException());
            }

            @SuppressWarnings("unchecked")
            T value = (T) structuredValue;
            Mono<StructuredModelResult<T>> result = Mono.just(
                    new StructuredModelResult<>(value, request.context().endpoint().model()));
            return raceCancellation(delayIfNeeded(result), request.context().cancellation());
        });
    }

    @Override
    public Flux<TextModelChunk> streamText(TextModelRequest request) {
        return Flux.defer(() -> {
            if (scenario == Scenario.CANCELLED || request.context().cancellation().isCancelled()) {
                return Flux.error(cancelledException());
            }
            if (scenario == Scenario.FORMAT_ERROR) {
                return Flux.error(new ModelGatewayException(
                        ModelGatewayException.Kind.FORMAT_INCOMPATIBLE,
                        "Fake text stream is invalid"));
            }

            Flux<TextModelChunk> chunks = Flux.range(0, textChunks.size())
                    .map(index -> new TextModelChunk(
                            request.context().requestId(),
                            index + 1L,
                            textChunks.get(index)));
            if (scenario == Scenario.DELAYED) {
                chunks = chunks.delaySubscription(delay);
            }
            Flux<TextModelChunk> cancelled = request.context().cancellation()
                    .whenCancelled()
                    .thenMany(Flux.error(cancelledException()));
            return Flux.firstWithSignal(chunks, cancelled);
        });
    }

    @Override
    public Mono<ModelConnectionResult> testConnection(ModelConnectionRequest request) {
        return Mono.defer(() -> {
            if (scenario == Scenario.CANCELLED || request.context().cancellation().isCancelled()) {
                return Mono.error(cancelledException());
            }
            if (scenario == Scenario.FORMAT_ERROR) {
                return Mono.error(new ModelGatewayException(
                        ModelGatewayException.Kind.FORMAT_INCOMPATIBLE,
                        "Fake connection response is incompatible"));
            }
            return raceCancellation(
                    delayIfNeeded(Mono.just(connectionResult)),
                    request.context().cancellation());
        });
    }

    private <T> Mono<T> delayIfNeeded(Mono<T> source) {
        return scenario == Scenario.DELAYED ? source.delaySubscription(delay) : source;
    }

    private <T> Mono<T> raceCancellation(Mono<T> source, ModelCancellationSignal cancellation) {
        Mono<T> cancelled = cancellation.whenCancelled().then(Mono.error(cancelledException()));
        return Mono.firstWithSignal(source, cancelled);
    }

    private ModelGatewayException cancelledException() {
        return new ModelGatewayException(
                ModelGatewayException.Kind.CANCELLED,
                "Fake model call was cancelled");
    }
}
