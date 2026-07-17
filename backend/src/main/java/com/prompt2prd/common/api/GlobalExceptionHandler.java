package com.prompt2prd.common.api;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebInputException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Translates every {@link ApiException} (and fallback {@link Exception})
 * into a uniform {@link ApiErrorResponse} JSON body.
 * <p>
 * The response must never contain keys, Authorization headers, or
 * complete prompt text — only stable error codes and actionable messages.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public Mono<ResponseEntity<ApiErrorResponse>> handleApiException(
            ApiException ex, ServerWebExchange exchange) {
        String requestId = extractRequestId(exchange);
        logError(requestId, ex);
        ApiErrorResponse body = ApiErrorResponse.of(
                ex.getErrorCode(), ex.getErrorCode().getClientMessage(), requestId);
        return Mono.just(ResponseEntity.status(ex.getHttpStatus()).body(body));
    }

    @ExceptionHandler(ServerWebInputException.class)
    public Mono<ResponseEntity<ApiErrorResponse>> handleInvalidRequest(
            ServerWebInputException ex, ServerWebExchange exchange) {
        String requestId = extractRequestId(exchange);
        log.warn("requestId={} code={} status={} exceptionType={}",
                requestId,
                ErrorCode.BAD_REQUEST,
                ex.getStatusCode().value(),
                ex.getClass().getSimpleName());
        ApiErrorResponse body = ApiErrorResponse.of(
                ErrorCode.BAD_REQUEST,
                ErrorCode.BAD_REQUEST.getClientMessage(),
                requestId);
        return Mono.just(ResponseEntity.badRequest().body(body));
    }

    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<ApiErrorResponse>> handleFallback(
            Exception ex, ServerWebExchange exchange) {
        String requestId = extractRequestId(exchange);
        log.error("requestId={} code={} status=500 exceptionType={}",
                requestId, ErrorCode.INTERNAL_ERROR, ex.getClass().getSimpleName());
        ApiErrorResponse body = ApiErrorResponse.of(
                ErrorCode.INTERNAL_ERROR,
                ErrorCode.INTERNAL_ERROR.getClientMessage(),
                requestId);
        return Mono.just(ResponseEntity.status(500).body(body));
    }

    // --- helpers ---

    private String extractRequestId(ServerWebExchange exchange) {
        List<String> headers = exchange.getResponse().getHeaders().get(RequestIdWebFilter.RESPONSE_HEADER);
        if (headers != null && !headers.isEmpty()) {
            return headers.getFirst();
        }
        return "unknown";
    }

    private void logError(String requestId, ApiException ex) {
        if (ex instanceof ApiException.Internal) {
            log.error("requestId={} code={} status={} exceptionType={}",
                    requestId,
                    ex.getErrorCode(),
                    ex.getHttpStatus().value(),
                    ex.getClass().getSimpleName());
        } else {
            log.warn("requestId={} code={} status={} exceptionType={}",
                    requestId,
                    ex.getErrorCode(),
                    ex.getHttpStatus().value(),
                    ex.getClass().getSimpleName());
        }
    }
}
