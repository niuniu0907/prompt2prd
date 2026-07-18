package com.prompt2prd.model.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.prompt2prd.model.application.ModelCallContext;
import com.prompt2prd.model.application.ModelCancellationSignal;
import com.prompt2prd.model.application.ModelConnectionRequest;
import com.prompt2prd.model.application.ModelEndpoint;
import com.prompt2prd.model.application.ModelGatewayException;
import com.prompt2prd.model.application.ModelListRequest;
import com.prompt2prd.model.application.ModelMessage;
import com.prompt2prd.model.application.StructuredModelRequest;
import com.prompt2prd.model.application.StructuredModelResult;
import com.prompt2prd.model.application.TextModelChunk;
import com.prompt2prd.model.application.TextModelRequest;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class SpringAiModelGatewayTests {

    private static final String OUTPUT_SCHEMA = """
            {"type":"object","properties":{"name":{"type":"string"}},
             "required":["name"],"additionalProperties":false}
            """;

    @Test
    void mapsAddressModelParametersAndBearerAuthentication() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        try (TestServer server = TestServer.start(exchange -> {
            requestBody.set(readBody(exchange));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            respondJson(exchange, completion("{\"name\":\"ready\"}"));
        })) {
            SpringAiModelGateway gateway = new SpringAiModelGateway(new EndpointAddressPolicy(Set.of()));
            StructuredModelResult<DemoResult> result = gateway.generateStructured(structuredRequest(server.baseUrl()))
                    .block();

            assertThat(result).isNotNull();
            assertThat(result.value().name()).isEqualTo("ready");
            assertThat(result.model()).isEqualTo("test-model");
            assertThat(authorization).hasValue("Bearer test-key");
            assertThat(requestBody.get())
                    .contains("\"model\":\"test-model\"")
                    .contains("\"temperature\":0.25")
                    .contains("\"top_p\":0.8")
                    .contains("\"custom_flag\":true")
                    .contains("\"json_schema\"");
        }
    }

    @Test
    void deepSeekStructuredRequestsUseJsonObjectCompatibilityMode() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        try (TestServer server = TestServer.start(exchange -> {
            requestBody.set(readBody(exchange));
            respondJson(exchange, completion("{\"name\":\"ready\"}"));
        })) {
            SpringAiModelGateway gateway = new SpringAiModelGateway(new EndpointAddressPolicy(Set.of()));

            StructuredModelResult<DemoResult> result = gateway
                    .generateStructured(structuredRequest(server.baseUrl(), "deepseek-chat"))
                    .block();

            assertThat(result).isNotNull();
            assertThat(result.value().name()).isEqualTo("ready");
            assertThat(requestBody.get())
                    .contains("\"model\":\"deepseek-chat\"")
                    .contains("\"response_format\":{\"type\":\"json_object\"}")
                    .contains("Return only valid JSON matching the requested schema")
                    .doesNotContain("\"json_schema\"");
        }
    }

    @Test
    void structuredResultIsNotPublishedBeforeTheWholeResponseArrives() throws Exception {
        CountDownLatch firstHalfSent = new CountDownLatch(1);
        CountDownLatch releaseSecondHalf = new CountDownLatch(1);
        try (TestServer server = TestServer.start(exchange -> {
            byte[] payload = completion("{\"name\":\"complete\"}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, payload.length);
            int split = payload.length / 2;
            exchange.getResponseBody().write(payload, 0, split);
            exchange.getResponseBody().flush();
            firstHalfSent.countDown();
            await(releaseSecondHalf);
            exchange.getResponseBody().write(payload, split, payload.length - split);
            exchange.close();
        })) {
            SpringAiModelGateway gateway = new SpringAiModelGateway(new EndpointAddressPolicy(Set.of()));
            CompletableFuture<StructuredModelResult<DemoResult>> result = gateway
                    .generateStructured(structuredRequest(server.baseUrl())).toFuture();

            assertThat(firstHalfSent.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(result).isNotDone();
            releaseSecondHalf.countDown();
            assertThat(result.get(3, TimeUnit.SECONDS).value().name()).isEqualTo("complete");
        }
    }

    @Test
    void textGenerationRemainsARealOrderedStream() throws Exception {
        try (TestServer server = TestServer.start(exchange -> respondStream(exchange,
                streamChunk("hello ", null) + streamChunk("world", "stop") + "data: [DONE]\n\n"))) {
            SpringAiModelGateway gateway = new SpringAiModelGateway(new EndpointAddressPolicy(Set.of()));

            StepVerifier.create(gateway.streamText(textRequest(server.baseUrl())))
                    .assertNext(chunk -> assertChunk(chunk, 1, "hello "))
                    .assertNext(chunk -> assertChunk(chunk, 2, "world"))
                    .verifyComplete();
        }
    }

    @Test
    void fetchesModelsWithPinnedGetRequestAndFriendlyNames() throws Exception {
        AtomicReference<String> method = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        try (TestServer server = TestServer.start(exchange -> {
            method.set(exchange.getRequestMethod());
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            respondJson(exchange, """
                    {"object":"list","data":[
                      {"id":"deepseek-chat","object":"model"},
                      {"id":"gpt-4o-mini","object":"model"}
                    ]}
                    """);
        })) {
            SpringAiModelGateway gateway = new SpringAiModelGateway(new EndpointAddressPolicy(Set.of()));

            var result = gateway.listModels(new ModelListRequest(
                    UUID.randomUUID().toString(),
                    URI.create(server.baseUrl()),
                    "test-key",
                    new ModelCancellationSignal())).block();

            assertThat(method).hasValue("GET");
            assertThat(authorization).hasValue("Bearer test-key");
            assertThat(result).isNotNull();
            assertThat(result.models()).extracting("id")
                    .containsExactly("deepseek-chat", "gpt-4o-mini");
            assertThat(result.models()).extracting("displayName")
                    .containsExactly("DeepSeek Chat", "GPT 4o Mini");
        }
    }

    @Test
    void automaticRedirectsAreDisabled() throws Exception {
        AtomicInteger redirectedHits = new AtomicInteger();
        try (TestServer target = TestServer.start(exchange -> {
            redirectedHits.incrementAndGet();
            respondJson(exchange, completion("OK"));
        }); TestServer source = TestServer.start(exchange -> {
            exchange.getResponseHeaders().set("Location", target.baseUrl() + "/chat/completions");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        })) {
            SpringAiModelGateway gateway = new SpringAiModelGateway(new EndpointAddressPolicy(Set.of()));

            StepVerifier.create(gateway.testConnection(new ModelConnectionRequest(context(source.baseUrl()))))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(ModelGatewayException.class);
                        assertThat(((ModelGatewayException) error).kind())
                                .isEqualTo(ModelGatewayException.Kind.INTERNAL);
                    })
                    .verify();
            assertThat(redirectedHits).hasValue(0);
        }
    }

    private static StructuredModelRequest<DemoResult> structuredRequest(String baseUrl) {
        return structuredRequest(baseUrl, "test-model");
    }

    private static StructuredModelRequest<DemoResult> structuredRequest(String baseUrl, String model) {
        ModelEndpoint endpoint = new ModelEndpoint(
                URI.create(baseUrl),
                model,
                "test-key",
                Map.of("temperature", 0.25, "top_p", 0.8, "custom_flag", true));
        return new StructuredModelRequest<>(
                new ModelCallContext(UUID.randomUUID().toString(), endpoint, new ModelCancellationSignal()),
                List.of(new ModelMessage(ModelMessage.Role.SYSTEM, "Return JSON."),
                        new ModelMessage(ModelMessage.Role.USER, "Status?")),
                DemoResult.class,
                OUTPUT_SCHEMA);
    }

    private static TextModelRequest textRequest(String baseUrl) {
        return new TextModelRequest(context(baseUrl),
                List.of(new ModelMessage(ModelMessage.Role.USER, "Stream text.")));
    }

    private static ModelCallContext context(String baseUrl) {
        return new ModelCallContext(
                UUID.randomUUID().toString(),
                new ModelEndpoint(URI.create(baseUrl), "test-model", "test-key", Map.of()),
                new ModelCancellationSignal());
    }

    private static void assertChunk(TextModelChunk chunk, long sequence, String content) {
        assertThat(chunk.sequence()).isEqualTo(sequence);
        assertThat(chunk.content()).isEqualTo(content);
    }

    private static String completion(String content) {
        String escaped = content.replace("\\", "\\\\").replace("\"", "\\\"");
        return """
                {"id":"chatcmpl-test","object":"chat.completion","created":1,
                 "model":"test-model","choices":[{"index":0,"message":{"role":"assistant","content":"%s"},
                 "finish_reason":"stop"}],"usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}
                """.formatted(escaped);
    }

    private static String streamChunk(String content, String finishReason) {
        String escaped = content.replace("\\", "\\\\").replace("\"", "\\\"");
        String finish = finishReason == null ? "null" : "\"" + finishReason + "\"";
        return "data: {\"id\":\"chatcmpl-test\",\"object\":\"chat.completion.chunk\",\"created\":1,"
                + "\"model\":\"test-model\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\""
                + escaped + "\"},\"finish_reason\":" + finish + "}]}\n\n";
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private static void respondJson(HttpExchange exchange, String body) throws IOException {
        respond(exchange, "application/json", body);
    }

    private static void respondStream(HttpExchange exchange, String body) throws IOException {
        respond(exchange, "text/event-stream", body);
    }

    private static void respond(HttpExchange exchange, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(3, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for test latch");
            }
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }

    record DemoResult(String name) {
    }

    private static final class TestServer implements AutoCloseable {

        private final HttpServer server;

        private TestServer(HttpServer server) {
            this.server = server;
        }

        static TestServer start(ThrowingHandler handler) throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/v1/chat/completions", exchange -> {
                try {
                    handler.handle(exchange);
                }
                catch (Exception ex) {
                    exchange.close();
                    throw ex instanceof IOException io ? io : new IOException(ex);
                }
            });
            server.createContext("/v1/models", exchange -> {
                try {
                    handler.handle(exchange);
                }
                catch (Exception ex) {
                    exchange.close();
                    throw ex instanceof IOException io ? io : new IOException(ex);
                }
            });
            server.start();
            return new TestServer(server);
        }

        String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }

    @FunctionalInterface
    private interface ThrowingHandler {
        void handle(HttpExchange exchange) throws Exception;
    }
}
