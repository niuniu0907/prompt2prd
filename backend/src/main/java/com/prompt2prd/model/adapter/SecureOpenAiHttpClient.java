package com.prompt2prd.model.adapter;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;

import com.openai.core.RequestOptions;
import com.openai.core.Timeout;
import com.openai.core.http.Headers;
import com.openai.core.http.HttpClient;
import com.openai.core.http.HttpMethod;
import com.openai.core.http.HttpRequest;
import com.openai.core.http.HttpRequestBody;
import com.openai.core.http.HttpResponse;
import com.openai.errors.OpenAIIoException;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Dns;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSink;

/** OpenAI SDK transport with redirects disabled and DNS pinned to prevalidated IPs. */
final class SecureOpenAiHttpClient implements HttpClient {

    private final OkHttpClient client;

    SecureOpenAiHttpClient(EndpointAddressPolicy.ValidatedEndpoint endpoint) {
        this(endpoint, Duration.ofSeconds(30), Duration.ofMinutes(10), Duration.ofMinutes(10));
    }

    SecureOpenAiHttpClient(
            EndpointAddressPolicy.ValidatedEndpoint endpoint,
            Duration connectTimeout,
            Duration readTimeout,
            Duration callTimeout) {
        Dns pinnedDns = hostname -> {
            if (!normalizeHost(hostname).equals(normalizeHost(endpoint.host()))) {
                throw new java.net.UnknownHostException("Unexpected model request host");
            }
            return endpoint.addresses();
        };
        this.client = new OkHttpClient.Builder()
                .dns(pinnedDns)
                .followRedirects(false)
                .followSslRedirects(false)
                .retryOnConnectionFailure(false)
                .connectTimeout(connectTimeout)
                .readTimeout(readTimeout)
                .callTimeout(callTimeout)
                .build();
    }

    @Override
    public HttpResponse execute(HttpRequest request, RequestOptions options) {
        Call call = newCall(request, options);
        try {
            return toHttpResponse(call.execute());
        }
        catch (IOException ex) {
            throw new OpenAIIoException("Request failed", ex);
        }
        finally {
            closeRequestBody(request);
        }
    }

    @Override
    public CompletableFuture<HttpResponse> executeAsync(HttpRequest request, RequestOptions options) {
        CompletableFuture<HttpResponse> future = new CompletableFuture<>();
        Call call = newCall(request, options);
        call.enqueue(new Callback() {
            @Override
            public void onResponse(Call ignored, Response response) {
                future.complete(toHttpResponse(response));
            }

            @Override
            public void onFailure(Call ignored, IOException error) {
                future.completeExceptionally(new OpenAIIoException("Request failed", error));
            }
        });
        future.whenComplete((ignored, error) -> {
            if (error instanceof CancellationException) {
                call.cancel();
            }
            closeRequestBody(request);
        });
        return future;
    }

    private Call newCall(HttpRequest request, RequestOptions options) {
        OkHttpClient.Builder builder = client.newBuilder();
        Timeout timeout = options.getTimeout();
        if (timeout != null) {
            builder.connectTimeout(timeout.connect())
                    .readTimeout(timeout.read())
                    .writeTimeout(timeout.write())
                    .callTimeout(timeout.request());
        }
        OkHttpClient perCallClient = builder.build();
        return perCallClient.newCall(toOkHttpRequest(request, perCallClient));
    }

    private static Request toOkHttpRequest(HttpRequest source, OkHttpClient client) {
        RequestBody body = toOkHttpBody(source.body());
        if (body == null && requiresBody(source.method())) {
            body = RequestBody.create("", null);
        }
        Request.Builder builder = new Request.Builder()
                .url(source.url())
                .method(source.method().name(), body);
        for (String name : source.headers().names()) {
            for (String value : source.headers().values(name)) {
                builder.addHeader(name, value);
            }
        }
        if (!source.headers().names().contains("X-Stainless-Read-Timeout") && client.readTimeoutMillis() != 0) {
            builder.addHeader("X-Stainless-Read-Timeout",
                    Long.toString(Duration.ofMillis(client.readTimeoutMillis()).getSeconds()));
        }
        return builder.build();
    }

    private static RequestBody toOkHttpBody(HttpRequestBody source) {
        if (source == null) {
            return null;
        }
        MediaType mediaType = source.contentType() == null ? null : MediaType.parse(source.contentType());
        return new RequestBody() {
            @Override
            public MediaType contentType() {
                return mediaType;
            }

            @Override
            public long contentLength() {
                return source.contentLength();
            }

            @Override
            public boolean isOneShot() {
                return !source.repeatable();
            }

            @Override
            public void writeTo(BufferedSink sink) throws IOException {
                source.writeTo(sink.outputStream());
            }
        };
    }

    private static HttpResponse toHttpResponse(Response response) {
        Headers.Builder headers = Headers.builder();
        for (int index = 0; index < response.headers().size(); index++) {
            headers.put(response.headers().name(index), response.headers().value(index));
        }
        Headers copiedHeaders = headers.build();
        return new HttpResponse() {
            @Override
            public int statusCode() {
                return response.code();
            }

            @Override
            public Headers headers() {
                return copiedHeaders;
            }

            @Override
            public InputStream body() {
                return Objects.requireNonNull(response.body()).byteStream();
            }

            @Override
            public void close() {
                if (response.body() != null) {
                    response.body().close();
                }
            }
        };
    }

    private static boolean requiresBody(HttpMethod method) {
        return method == HttpMethod.POST || method == HttpMethod.PUT || method == HttpMethod.PATCH;
    }

    private static void closeRequestBody(HttpRequest request) {
        if (request.body() != null) {
            request.body().close();
        }
    }

    private static String normalizeHost(String host) {
        String normalized = host.toLowerCase(java.util.Locale.ROOT).replaceFirst("\\.$", "");
        return normalized.startsWith("[") && normalized.endsWith("]")
                ? normalized.substring(1, normalized.length() - 1)
                : normalized;
    }

    @Override
    public void close() {
        client.dispatcher().executorService().shutdown();
        client.connectionPool().evictAll();
    }
}
