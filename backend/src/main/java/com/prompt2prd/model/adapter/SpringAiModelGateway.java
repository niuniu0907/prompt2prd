package com.prompt2prd.model.adapter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClientAsyncImpl;
import com.openai.client.OpenAIClientImpl;
import com.openai.core.ClientOptions;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.OpenAIInvalidDataException;
import com.openai.errors.OpenAIServiceException;
import com.prompt2prd.model.application.AvailableModel;
import com.prompt2prd.model.application.ModelCallContext;
import com.prompt2prd.model.application.ModelConnectionRequest;
import com.prompt2prd.model.application.ModelConnectionResult;
import com.prompt2prd.model.application.ModelEndpoint;
import com.prompt2prd.model.application.ModelGateway;
import com.prompt2prd.model.application.ModelGatewayException;
import com.prompt2prd.model.application.ModelListRequest;
import com.prompt2prd.model.application.ModelListResult;
import com.prompt2prd.model.application.ModelMessage;
import com.prompt2prd.model.application.StructuredModelRequest;
import com.prompt2prd.model.application.StructuredModelResult;
import com.prompt2prd.model.application.TextModelChunk;
import com.prompt2prd.model.application.TextModelRequest;
import okhttp3.Dns;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** Spring AI adapter for OpenAI-compatible chat completion services. */
public final class SpringAiModelGateway implements ModelGateway {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final EndpointAddressPolicy endpointPolicy;

    public SpringAiModelGateway() {
        this(EndpointAddressPolicy.fromEnvironment());
    }

    public SpringAiModelGateway(EndpointAddressPolicy endpointPolicy) {
        this.endpointPolicy = endpointPolicy;
    }

    @Override
    public <T> Mono<StructuredModelResult<T>> generateStructured(StructuredModelRequest<T> request) {
        return withCancellation(
                request.context(),
                Mono.using(
                        () -> createClient(request.context().endpoint(), request.outputSchema()),
                        client -> Mono.fromCallable(() -> {
                            BeanOutputConverter<T> converter = new BeanOutputConverter<>(request.responseType());
                            ResponseEntity<ChatResponse, T> response = client.chatClient()
                                    .prompt()
                                    .messages(toSpringMessages(request.messages()))
                                    .call()
                                    .responseEntity(converter);
                            return new StructuredModelResult<>(response.entity(), responseModel(response.response(),
                                    request.context().endpoint().model()));
                        }).subscribeOn(Schedulers.boundedElastic()),
                        ClientBundle::close))
                .onErrorMap(this::translateFailure);
    }

    @Override
    public Flux<TextModelChunk> streamText(TextModelRequest request) {
        AtomicLong sequence = new AtomicLong();
        Flux<TextModelChunk> stream = Flux.using(
                () -> createClient(request.context().endpoint(), null),
                client -> client.chatClient()
                        .prompt()
                        .messages(toSpringMessages(request.messages()))
                        .stream()
                        .content()
                        .filter(content -> content != null && !content.isEmpty())
                        .map(content -> new TextModelChunk(
                                request.context().requestId(), sequence.incrementAndGet(), content)),
                ClientBundle::close);

        return stream.takeUntilOther(request.context().cancellation().whenCancelled())
                .concatWith(Flux.defer(() -> request.context().cancellation().isCancelled()
                        ? Flux.error(cancelled())
                        : Flux.empty()))
                .onErrorMap(this::translateFailure);
    }

    @Override
    public Mono<ModelConnectionResult> testConnection(ModelConnectionRequest request) {
        Instant started = Instant.now();
        Mono<ModelConnectionResult> call = Mono.using(
                () -> createClient(request.context().endpoint(), null),
                client -> Mono.fromCallable(() -> {
                    client.chatClient().prompt().user("Reply with OK.").call().content();
                    return new ModelConnectionResult(
                            request.context().endpoint().model(), Duration.between(started, Instant.now()));
                }).subscribeOn(Schedulers.boundedElastic()),
                ClientBundle::close);
        return withCancellation(request.context(), call).onErrorMap(this::translateFailure);
    }

    @Override
    public Mono<ModelListResult> listModels(ModelListRequest request) {
        Instant started = Instant.now();
        Mono<ModelListResult> call = Mono.using(
                () -> createModelListClient(request),
                client -> Mono.fromCallable(() -> {
                    Response response = client.newCall(new Request.Builder()
                            .url(modelsUrl(request.baseUrl()))
                            .get()
                            .header("Authorization", "Bearer " + request.apiKey())
                            .header("Accept", "application/json")
                            .build()).execute();
                    try (response) {
                        if (!response.isSuccessful()) {
                            throw statusFailure(response.code());
                        }
                        ResponseBody body = response.body();
                        if (body == null) {
                            throw new ModelGatewayException(
                                    ModelGatewayException.Kind.FORMAT_INCOMPATIBLE,
                                    "Model list response body is empty");
                        }
                        return new ModelListResult(
                                parseModels(body.string()),
                                Duration.between(started, Instant.now()));
                    }
                }).subscribeOn(Schedulers.boundedElastic()),
                this::closeModelListClient);
        return withCancellation(request, call).onErrorMap(this::translateFailure);
    }

    private ClientBundle createClient(ModelEndpoint endpoint, String outputSchema) {
        EndpointAddressPolicy.ValidatedEndpoint validated = endpointPolicy.validate(endpoint.baseUrl());
        SecureOpenAiHttpClient transport = new SecureOpenAiHttpClient(validated);
        ClientOptions clientOptions = ClientOptions.builder()
                .httpClient(transport)
                .baseUrl(endpoint.baseUrl().toString())
                .apiKey(endpoint.apiKey())
                .maxRetries(0)
                .build();

        OpenAiChatOptions options = toOptions(endpoint, outputSchema);
        OpenAiChatModel model = OpenAiChatModel.builder()
                .openAiClient(new OpenAIClientImpl(clientOptions))
                .openAiClientAsync(new OpenAIClientAsyncImpl(clientOptions))
                .options(options)
                .build();
        return new ClientBundle(ChatClient.create(model), clientOptions);
    }

    private OkHttpClient createModelListClient(ModelListRequest request) {
        EndpointAddressPolicy.ValidatedEndpoint endpoint = endpointPolicy.validate(request.baseUrl());
        Dns pinnedDns = hostname -> {
            if (!normalizeHost(hostname).equals(normalizeHost(endpoint.host()))) {
                throw new java.net.UnknownHostException("Unexpected model request host");
            }
            return endpoint.addresses();
        };
        return new OkHttpClient.Builder()
                .dns(pinnedDns)
                .followRedirects(false)
                .followSslRedirects(false)
                .retryOnConnectionFailure(false)
                .build();
    }

    private void closeModelListClient(OkHttpClient client) {
        client.dispatcher().executorService().shutdown();
        client.connectionPool().evictAll();
    }

    private static String modelsUrl(java.net.URI baseUrl) {
        String base = baseUrl.toString().replaceFirst("/+$", "");
        return base + "/models";
    }

    private static List<AvailableModel> parseModels(String body) {
        try {
            JsonNode data = JSON.readTree(body).path("data");
            if (!data.isArray()) {
                throw new ModelGatewayException(
                        ModelGatewayException.Kind.FORMAT_INCOMPATIBLE,
                        "Model list data must be an array");
            }
            List<AvailableModel> models = new ArrayList<>();
            for (JsonNode item : data) {
                String id = item.path("id").asText("");
                if (!id.isBlank()) {
                    models.add(new AvailableModel(id, friendlyName(id)));
                }
            }
            return models;
        }
        catch (IOException | IllegalArgumentException ex) {
            throw new ModelGatewayException(
                    ModelGatewayException.Kind.FORMAT_INCOMPATIBLE,
                    "Model list response format is incompatible", ex);
        }
    }

    private static String friendlyName(String id) {
        return switch (id) {
            case "deepseek-chat" -> "DeepSeek Chat";
            case "deepseek-reasoner" -> "DeepSeek Reasoner";
            default -> {
                String[] parts = id.replace('_', '-').split("-");
                List<String> words = new ArrayList<>();
                for (String part : parts) {
                    if (!part.isBlank()) {
                        words.add(part.equalsIgnoreCase("gpt")
                                ? "GPT"
                                : part.substring(0, 1).toUpperCase(Locale.ROOT) + part.substring(1));
                    }
                }
                yield words.isEmpty() ? id : String.join(" ", words);
            }
        };
    }

    private static ModelGatewayException statusFailure(int statusCode) {
        ModelGatewayException.Kind kind = switch (statusCode) {
            case 401, 403 -> ModelGatewayException.Kind.AUTHENTICATION;
            case 404 -> ModelGatewayException.Kind.MODEL_NOT_FOUND;
            case 408, 504 -> ModelGatewayException.Kind.TIMEOUT;
            case 429 -> ModelGatewayException.Kind.RATE_LIMITED;
            default -> statusCode >= 500
                    ? ModelGatewayException.Kind.UNREACHABLE
                    : ModelGatewayException.Kind.INTERNAL;
        };
        return new ModelGatewayException(kind, "OpenAI-compatible model list request failed");
    }

    private static OpenAiChatOptions toOptions(ModelEndpoint endpoint, String outputSchema) {
        OpenAiChatOptions.Builder builder = OpenAiChatOptions.builder()
                .baseUrl(endpoint.baseUrl().toString())
                .apiKey(endpoint.apiKey())
                .model(endpoint.model())
                .maxRetries(0);
        Map<String, Object> extraBody = new LinkedHashMap<>();
        for (Map.Entry<String, Object> parameter : endpoint.parameters().entrySet()) {
            String name = parameter.getKey();
            Object value = parameter.getValue();
            switch (name) {
                case "temperature" -> builder.temperature(number(value, name).doubleValue());
                case "top_p" -> builder.topP(number(value, name).doubleValue());
                case "frequency_penalty" -> builder.frequencyPenalty(number(value, name).doubleValue());
                case "presence_penalty" -> builder.presencePenalty(number(value, name).doubleValue());
                case "max_tokens" -> builder.maxTokens(number(value, name).intValue());
                case "max_completion_tokens" -> builder.maxCompletionTokens(number(value, name).intValue());
                case "seed" -> builder.seed(number(value, name).intValue());
                case "stop" -> builder.stop(stringList(value, name));
                default -> extraBody.put(name, value);
            }
        }
        if (!extraBody.isEmpty()) {
            builder.extraBody(extraBody);
        }
        if (outputSchema != null) {
            builder.outputSchema(outputSchema);
        }
        return builder.build();
    }

    private static Number number(Object value, String name) {
        if (value instanceof Number number) {
            return number;
        }
        throw new IllegalArgumentException("Model parameter " + name + " must be numeric");
    }

    private static List<String> stringList(Object value, String name) {
        if (!(value instanceof List<?> values)) {
            throw new IllegalArgumentException("Model parameter " + name + " must be a list");
        }
        List<String> result = new ArrayList<>();
        for (Object item : values) {
            if (!(item instanceof String text)) {
                throw new IllegalArgumentException("Model parameter " + name + " must contain strings");
            }
            result.add(text);
        }
        return result;
    }

    private static List<Message> toSpringMessages(List<ModelMessage> messages) {
        return messages.stream().map(message -> switch (message.role()) {
            case SYSTEM -> new SystemMessage(message.content());
            case USER -> new UserMessage(message.content());
            case ASSISTANT -> new AssistantMessage(message.content());
        }).map(Message.class::cast).toList();
    }

    private static String responseModel(ChatResponse response, String fallback) {
        if (response != null && response.getMetadata() != null
                && response.getMetadata().getModel() != null
                && !response.getMetadata().getModel().isBlank()) {
            return response.getMetadata().getModel();
        }
        return fallback;
    }

    private static <T> Mono<T> withCancellation(ModelCallContext context, Mono<T> operation) {
        if (context.cancellation().isCancelled()) {
            return Mono.error(cancelled());
        }
        Mono<T> cancellation = context.cancellation().whenCancelled().then(Mono.error(cancelled()));
        return Mono.firstWithSignal(operation, cancellation);
    }

    private static <T> Mono<T> withCancellation(ModelListRequest request, Mono<T> operation) {
        if (request.cancellation().isCancelled()) {
            return Mono.error(cancelled());
        }
        Mono<T> cancellation = request.cancellation().whenCancelled().then(Mono.error(cancelled()));
        return Mono.firstWithSignal(operation, cancellation);
    }

    private static String normalizeHost(String host) {
        String normalized = host.toLowerCase(Locale.ROOT).replaceFirst("\\.$", "");
        return normalized.startsWith("[") && normalized.endsWith("]")
                ? normalized.substring(1, normalized.length() - 1)
                : normalized;
    }

    private Throwable translateFailure(Throwable failure) {
        Throwable cause = unwrap(failure);
        if (cause instanceof ModelGatewayException) {
            return cause;
        }
        if (cause instanceof OpenAIServiceException service) {
            ModelGatewayException.Kind kind = switch (service.statusCode()) {
                case 401, 403 -> ModelGatewayException.Kind.AUTHENTICATION;
                case 404 -> ModelGatewayException.Kind.MODEL_NOT_FOUND;
                case 408, 504 -> ModelGatewayException.Kind.TIMEOUT;
                case 429 -> ModelGatewayException.Kind.RATE_LIMITED;
                default -> service.statusCode() >= 500
                        ? ModelGatewayException.Kind.UNREACHABLE
                        : ModelGatewayException.Kind.INTERNAL;
            };
            return new ModelGatewayException(kind, "OpenAI-compatible model request failed", cause);
        }
        if (cause instanceof OpenAIInvalidDataException) {
            return new ModelGatewayException(ModelGatewayException.Kind.FORMAT_INCOMPATIBLE,
                    "Model response format is incompatible", cause);
        }
        if (cause instanceof OpenAIIoException) {
            return new ModelGatewayException(ModelGatewayException.Kind.UNREACHABLE,
                    "Model service is unreachable", cause);
        }
        if (cause instanceof TimeoutException) {
            return new ModelGatewayException(ModelGatewayException.Kind.TIMEOUT,
                    "Model request timed out", cause);
        }
        if (cause instanceof java.util.concurrent.CancellationException) {
            return cancelled();
        }
        return new ModelGatewayException(ModelGatewayException.Kind.INTERNAL,
                "Unexpected model adapter failure", cause);
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof CompletionException || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static ModelGatewayException cancelled() {
        return new ModelGatewayException(ModelGatewayException.Kind.CANCELLED, "Model request was cancelled");
    }

    private record ClientBundle(ChatClient chatClient, ClientOptions clientOptions) implements AutoCloseable {
        @Override
        public void close() {
            clientOptions.close();
        }
    }
}
