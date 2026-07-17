package com.prompt2prd.common.api;

import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
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

    /** Sensitive tokens to strip from error messages before returning them to the client. */
    private static final Set<String> SENSITIVE_HEADERS = Set.of(
            "authorization", "x-api-key", "api-key", "openai-api-key",
            "x-deepseek-key", "x-qwen-key", "proxy-authorization"
    );

    @ExceptionHandler(ApiException.class)
    public Mono<ResponseEntity<ApiErrorResponse>> handleApiException(
            ApiException ex, ServerWebExchange exchange) {
        String requestId = extractRequestId(exchange);
        logError(requestId, ex);
        ApiErrorResponse body = ApiErrorResponse.of(ex.getErrorCode(), sanitize(ex.getMessage()), requestId);
        return Mono.just(ResponseEntity.status(ex.getHttpStatus()).body(body));
    }

    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<ApiErrorResponse>> handleFallback(
            Exception ex, ServerWebExchange exchange) {
        String requestId = extractRequestId(exchange);
        log.error("requestId={} — unhandled internal error", requestId, ex);
        ApiErrorResponse body = ApiErrorResponse.of(
                ErrorCode.INTERNAL_ERROR,
                "An unexpected internal error occurred. Please try again or contact support.",
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
            log.error("requestId={} code={} status={} — {}",
                    requestId, ex.getErrorCode(), ex.getHttpStatus().value(), ex.getMessage(), ex);
        } else {
            log.warn("requestId={} code={} status={} — {}",
                    requestId, ex.getErrorCode(), ex.getHttpStatus().value(), ex.getMessage());
        }
    }

    static String sanitize(String message) {
        if (message == null) {
            return "An error occurred.";
        }
        String sanitized = message;
        for (String header : SENSITIVE_HEADERS) {
            sanitized = sanitized.replaceAll("(?i)" + header + "[\\s]*[:=][\\s]*\\S+", header + ": ***");
        }
        // Remove bearer tokens
        sanitized = sanitized.replaceAll("(?i)bearer\\s+[\\w\\-\\.]+", "bearer ***");
        return sanitized;
    }
}
