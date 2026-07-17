package com.prompt2prd.common.api;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(ApiErrorProtocolTests.ErrorTriggerController.class)
class ApiErrorProtocolTests {

    @LocalServerPort
    private int port;

    private WebTestClient webClient;

    @BeforeEach
    void setUp() {
        webClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofSeconds(10))
                .build();
    }

    // --- individual error-category tests ---

    @Test
    void badRequestShouldReturn400WithStableCode() {
        ApiErrorResponse body = triggerAndExpect("/test-error/bad-request", 400, ErrorCode.BAD_REQUEST);
        assertThat(body.message()).isNotEmpty();
        assertThat(body.requestId()).isNotEmpty();
    }

    @Test
    void unauthorizedShouldReturn502WithStableCode() {
        ApiErrorResponse body = triggerAndExpect("/test-error/unauthorized", 502, ErrorCode.UNAUTHORIZED);
        assertThat(body.message()).isNotEmpty();
    }

    @Test
    void modelNotFoundShouldReturn404WithStableCode() {
        ApiErrorResponse body = triggerAndExpect("/test-error/model-not-found", 404, ErrorCode.MODEL_NOT_FOUND);
        assertThat(body.message()).isNotEmpty();
    }

    @Test
    void formatIncompatibleShouldReturn422WithStableCode() {
        ApiErrorResponse body = triggerAndExpect("/test-error/format-incompatible", 422, ErrorCode.FORMAT_INCOMPATIBLE);
        assertThat(body.message()).isNotEmpty();
    }

    @Test
    void rateLimitExceededShouldReturn429WithStableCode() {
        ApiErrorResponse body = triggerAndExpect("/test-error/rate-limit-exceeded", 429, ErrorCode.RATE_LIMIT_EXCEEDED);
        assertThat(body.message()).isNotEmpty();
    }

    @Test
    void serviceUnavailableShouldReturn502WithStableCode() {
        ApiErrorResponse body = triggerAndExpect("/test-error/service-unavailable", 502, ErrorCode.SERVICE_UNAVAILABLE);
        assertThat(body.message()).isNotEmpty();
    }

    @Test
    void requestTimeoutShouldReturn504WithStableCode() {
        ApiErrorResponse body = triggerAndExpect("/test-error/request-timeout", 504, ErrorCode.REQUEST_TIMEOUT);
        assertThat(body.message()).isNotEmpty();
    }

    @Test
    void internalErrorShouldReturn500WithStableCode() {
        ApiErrorResponse body = triggerAndExpect("/test-error/internal-error", 500, ErrorCode.INTERNAL_ERROR);
        assertThat(body.message()).isNotEmpty();
    }

    // --- cross-cutting tests ---

    @Test
    void everyResponseShouldIncludeUniqueRequestId() {
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < 3; i++) {
            ApiErrorResponse body = triggerAndExpect("/test-error/bad-request", 400, ErrorCode.BAD_REQUEST);
            assertThat(body.requestId()).isNotBlank();
            ids.add(body.requestId());
        }
        assertThat(ids).hasSize(3);
    }

    @Test
    void responseShouldNeverContainSensitiveKeysOrAuthHeaders() {
        List<String> paths = List.of(
                "/test-error/unauthorized",
                "/test-error/internal-error",
                "/test-error/service-unavailable"
        );
        for (String path : paths) {
            byte[] raw = webClient.get().uri(path)
                    .exchange()
                    .expectBody().returnResult().getResponseBody();
            String body = raw != null ? new String(raw) : "";
            assertThat(body)
                    .as("Response for %s must not contain secret tokens", path)
                    .doesNotContain("sk-", "Bearer ", "Authorization", "api-key", "secret");
        }
    }

    @Test
    void timestampShouldBeRecentUtcIso8601() {
        ApiErrorResponse body = triggerAndExpect("/test-error/bad-request", 400, ErrorCode.BAD_REQUEST);
        assertThat(body.timestamp()).isNotNull();
        // Must be within 10 seconds of now
        long diffSeconds = Math.abs(
                java.time.Duration.between(body.timestamp(), java.time.Instant.now()).getSeconds());
        assertThat(diffSeconds).isLessThan(10);
    }

    @Test
    void fallbackUnhandledExceptionShouldYield500InternalError() {
        ApiErrorResponse body = triggerAndExpect("/test-error/fallback", 500, ErrorCode.INTERNAL_ERROR);
        // Fallback must not leak the original exception message
        assertThat(body.message()).doesNotContain("NullPointerException", "RuntimeException");
    }

    @Nested
    class AllErrorCodesAreDistinct {
        @Test
        void noDuplicateCodes() {
            ErrorCode[] codes = ErrorCode.values();
            Set<ErrorCode> seen = new HashSet<>();
            for (ErrorCode code : codes) {
                assertThat(seen.add(code))
                        .as("ErrorCode %s appears more than once", code)
                        .isTrue();
            }
        }
    }

    // --- helpers ---

    private ApiErrorResponse triggerAndExpect(String path, int expectedStatus, ErrorCode expectedCode) {
        return webClient.get().uri(path)
                .exchange()
                .expectStatus().value(s -> assertThat(s).isEqualTo(expectedStatus))
                .expectHeader().exists("X-Request-Id")
                .expectBody(ApiErrorResponse.class)
                .consumeWith(result -> {
                    ApiErrorResponse body = result.getResponseBody();
                    assertThat(body).isNotNull();
                    assertThat(body.code()).isEqualTo(expectedCode);
                    assertThat(body.requestId()).isNotBlank();
                    assertThat(body.timestamp()).isNotNull();
                })
                .returnResult().getResponseBody();
    }

    // --- test-only controller that throws each exception type on demand ---

    @RestController
    @RequestMapping("/test-error")
    static class ErrorTriggerController {

        @GetMapping("/bad-request")
        Mono<String> badRequest() {
            throw new ApiException.BadRequest("Project name must contain at least 5 characters.");
        }

        @GetMapping("/unauthorized")
        Mono<String> unauthorized() {
            throw new ApiException.Unauthorized("Upstream model returned 401 — verify your API key.");
        }

        @GetMapping("/model-not-found")
        Mono<String> modelNotFound() {
            throw new ApiException.ModelNotFound("Model 'gpt-999' is not available. Check the model name.");
        }

        @GetMapping("/format-incompatible")
        Mono<String> formatIncompatible() {
            throw new ApiException.FormatIncompatible("Model response did not match the expected JSON schema.");
        }

        @GetMapping("/rate-limit-exceeded")
        Mono<String> rateLimitExceeded() {
            throw new ApiException.RateLimitExceeded("Daily analysis limit reached. Try again tomorrow or use your own API key.");
        }

        @GetMapping("/service-unavailable")
        Mono<String> serviceUnavailable() {
            throw new ApiException.ServiceUnavailable("Cannot reach model endpoint https://api.example.com — connection refused.");
        }

        @GetMapping("/request-timeout")
        Mono<String> requestTimeout() {
            throw new ApiException.RequestTimeout("Upstream model call timed out after 30 seconds.");
        }

        @GetMapping("/internal-error")
        Mono<String> internalError() {
            throw new ApiException.Internal("State merge produced an inconsistent result.");
        }

        @GetMapping("/fallback")
        Mono<String> fallback() {
            throw new RuntimeException("This raw exception message should NOT appear in the response body.");
        }
    }
}
