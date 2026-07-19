package com.prompt2prd.model.application;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

class ModelGatewayTests {

    @Test
    void fakeGatewayReturnsCompleteStructuredSuccess() {
        DemoResult expected = new DemoResult("宠物寄养平台", 3);
        FakeModelGateway gateway = FakeModelGateway.success(
                expected,
                List.of("第一段", "第二段"),
                new ModelConnectionResult("fixture-model", Duration.ofMillis(12)));

        StepVerifier.create(gateway.generateStructured(structuredRequest(newSignal())))
                .assertNext(result -> {
                    assertThat(result.value()).isEqualTo(expected);
                    assertThat(result.model()).isEqualTo("fixture-model");
                })
                .verifyComplete();
    }

    @Test
    void fakeGatewayStreamsOrderedTextChunks() {
        FakeModelGateway gateway = FakeModelGateway.success(
                new DemoResult("ok", 1),
                List.of("第一段", "第二段", "第三段"),
                new ModelConnectionResult("fixture-model", Duration.ofMillis(12)));
        TextModelRequest request = textRequest(newSignal());

        StepVerifier.create(gateway.streamText(request))
                .assertNext(chunk -> assertChunk(chunk, request.context().requestId(), 1, "第一段"))
                .assertNext(chunk -> assertChunk(chunk, request.context().requestId(), 2, "第二段"))
                .assertNext(chunk -> assertChunk(chunk, request.context().requestId(), 3, "第三段"))
                .verifyComplete();
    }

    @Test
    void fakeGatewayReturnsConnectionSuccess() {
        ModelConnectionResult expected =
                new ModelConnectionResult("fixture-model", Duration.ofMillis(12));
        FakeModelGateway gateway = FakeModelGateway.success(
                new DemoResult("ok", 1), List.of(), expected);

        StepVerifier.create(gateway.testConnection(connectionRequest(newSignal())))
                .expectNext(expected)
                .verifyComplete();
    }

    @Test
    void fakeGatewayReturnsModelListSuccess() {
        FakeModelGateway gateway = FakeModelGateway.success(
                new DemoResult("ok", 1),
                List.of(),
                new ModelConnectionResult("fixture-model", Duration.ofMillis(12)));

        StepVerifier.create(gateway.listModels(modelListRequest(newSignal())))
                .assertNext(result -> {
                    assertThat(result.models()).extracting(AvailableModel::id)
                            .containsExactly("fixture-model");
                    assertThat(result.latency()).isEqualTo(Duration.ofMillis(12));
                })
                .verifyComplete();
    }

    @Test
    void fakeGatewayReturnsFormatError() {
        FakeModelGateway gateway = FakeModelGateway.formatError();

        StepVerifier.create(gateway.generateStructured(structuredRequest(newSignal())))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ModelGatewayException.class);
                    assertThat(((ModelGatewayException) error).kind())
                            .isEqualTo(ModelGatewayException.Kind.FORMAT_INCOMPATIBLE);
                })
                .verify();
    }

    @Test
    void fakeGatewayCanDelayResults() {
        FakeModelGateway gateway = FakeModelGateway.delayed(
                Duration.ofSeconds(5),
                new DemoResult("delayed", 1),
                List.of("later"),
                new ModelConnectionResult("fixture-model", Duration.ofMillis(12)));

        StepVerifier.withVirtualTime(
                        () -> gateway.generateStructured(structuredRequest(newSignal())))
                .expectSubscription()
                .expectNoEvent(Duration.ofSeconds(4))
                .thenAwait(Duration.ofSeconds(1))
                .assertNext(result -> assertThat(result.value().name()).isEqualTo("delayed"))
                .verifyComplete();
    }

    @Test
    void cancellationSignalStopsDelayedCall() {
        ModelCancellationSignal signal = newSignal();
        FakeModelGateway gateway = FakeModelGateway.delayed(
                Duration.ofSeconds(30),
                new DemoResult("too late", 1),
                List.of("too late"),
                new ModelConnectionResult("fixture-model", Duration.ofMillis(12)));

        StepVerifier.create(gateway.generateStructured(structuredRequest(signal)))
                .then(signal::cancel)
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ModelGatewayException.class);
                    assertThat(((ModelGatewayException) error).kind())
                            .isEqualTo(ModelGatewayException.Kind.CANCELLED);
                })
                .verify(Duration.ofSeconds(2));
    }

    @Test
    void fakeGatewayCanReturnImmediateCancellation() {
        FakeModelGateway gateway = FakeModelGateway.cancelled();

        StepVerifier.create(gateway.testConnection(connectionRequest(newSignal())))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ModelGatewayException.class);
                    assertThat(((ModelGatewayException) error).kind())
                            .isEqualTo(ModelGatewayException.Kind.CANCELLED);
                })
                .verify();
    }

    @Test
    void endpointStringRepresentationNeverContainsApiKey() {
        ModelEndpoint endpoint = endpoint();

        assertThat(endpoint.toString())
                .doesNotContain(endpoint.apiKey())
                .contains("[REDACTED]");
    }

    private StructuredModelRequest<DemoResult> structuredRequest(
            ModelCancellationSignal signal) {
        return new StructuredModelRequest<>(
                context(signal),
                messages(),
                DemoResult.class,
                "{\"type\":\"object\",\"required\":[\"name\",\"count\"]}");
    }

    private TextModelRequest textRequest(ModelCancellationSignal signal) {
        return new TextModelRequest(context(signal), messages());
    }

    private ModelConnectionRequest connectionRequest(ModelCancellationSignal signal) {
        return new ModelConnectionRequest(context(signal));
    }

    private ModelListRequest modelListRequest(ModelCancellationSignal signal) {
        ModelEndpoint endpoint = endpoint();
        return new ModelListRequest(
                UUID.randomUUID().toString(),
                endpoint.baseUrl(),
                endpoint.apiKey(),
                signal);
    }

    private ModelCallContext context(ModelCancellationSignal signal) {
        return new ModelCallContext(UUID.randomUUID().toString(), endpoint(), signal);
    }

    private ModelEndpoint endpoint() {
        return new ModelEndpoint(
                URI.create("https://models.example.test/v1"),
                "fixture-model",
                "fixture-key",
                Map.of("temperature", 0.2));
    }

    private List<ModelMessage> messages() {
        return List.of(
                new ModelMessage(ModelMessage.Role.SYSTEM, "只返回约定格式"),
                new ModelMessage(ModelMessage.Role.USER, "整理当前需求"));
    }

    private ModelCancellationSignal newSignal() {
        return new ModelCancellationSignal();
    }

    private void assertChunk(
            TextModelChunk chunk,
            String requestId,
            long sequence,
            String content) {
        assertThat(chunk.requestId()).isEqualTo(requestId);
        assertThat(chunk.sequence()).isEqualTo(sequence);
        assertThat(chunk.content()).isEqualTo(content);
    }

    record DemoResult(String name, int count) {
    }
}
