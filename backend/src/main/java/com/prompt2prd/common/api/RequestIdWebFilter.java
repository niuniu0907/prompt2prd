package com.prompt2prd.common.api;

import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * Assigns a unique {@code requestId} to every HTTP request.
 * <p>
 * The id is attached to the Reactor {@link Context} under the key
 * {@link #REQUEST_ID_KEY} so downstream handlers can retrieve it,
 * and also written to the response header {@code X-Request-Id}.
 */
@Component
@Order(-200)
public class RequestIdWebFilter implements WebFilter {

    public static final String REQUEST_ID_KEY = "prompt2prd.requestId";
    public static final String RESPONSE_HEADER = "X-Request-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String requestId = UUID.randomUUID().toString();
        exchange.getResponse().getHeaders().set(RESPONSE_HEADER, requestId);
        MDC.put("requestId", requestId);
        return chain.filter(exchange)
                .contextWrite(Context.of(REQUEST_ID_KEY, requestId))
                .doFinally(signalType -> MDC.remove("requestId"));
    }
}
