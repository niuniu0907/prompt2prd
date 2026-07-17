package com.prompt2prd.model.api;

import com.prompt2prd.common.config.ModelProperties;
import com.prompt2prd.model.application.ModelConnectionRequest;
import com.prompt2prd.model.application.ModelConnectionResult;
import com.prompt2prd.model.application.ModelGateway;
import com.prompt2prd.model.application.ModelGatewayException;
import com.prompt2prd.model.domain.ModelConfig;
import com.prompt2prd.quota.ClientIpDigest;
import com.prompt2prd.quota.QuotaService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ModelConfigControllerTests {

    private static final String USER_KEY = "sk-controller-secret";

    @Value("${local.server.port}")
    private int port;

    private WebTestClient webTestClient;

    @MockitoBean
    private ModelGateway modelGateway;

    @MockitoBean
    private ModelProperties modelProperties;

    @MockitoBean
    private QuotaService quotaService;

    @MockitoBean
    private ClientIpDigest clientIpDigest;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        when(clientIpDigest.from(any())).thenReturn("test-ip-digest");
        webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Test
    void returnsOnlyTheSystemKeyCapability() {
        when(modelProperties.systemKeyAvailable()).thenReturn(true);

        webTestClient.get()
                .uri("/api/model-config")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.systemKeyAvailable").isEqualTo(true)
                .jsonPath("$.systemApiKey").doesNotExist();
    }

    @Test
    void testsTheExplicitUserConfigurationWithoutFallingBack() {
        when(modelProperties.resolve(ModelConfig.KeySource.USER, USER_KEY))
                .thenReturn(ModelConfig.user(USER_KEY));
        when(modelGateway.testConnection(any()))
                .thenReturn(Mono.just(new ModelConnectionResult(
                        "deepseek-chat", Duration.ofMillis(37))));

        webTestClient.post()
                .uri("/api/model-config/test")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "keySource": "USER",
                          "provider": "DEEPSEEK",
                          "model": "deepseek-chat",
                          "apiKey": "sk-controller-secret",
                          "parameters": {"temperature": 0.2}
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.keySource").isEqualTo("USER")
                .jsonPath("$.model").isEqualTo("deepseek-chat")
                .jsonPath("$.latencyMs").isEqualTo(37)
                .jsonPath("$.apiKey").doesNotExist();

        var requestCaptor = org.mockito.ArgumentCaptor.forClass(ModelConnectionRequest.class);
        verify(modelGateway).testConnection(requestCaptor.capture());
        ModelConnectionRequest captured = requestCaptor.getValue();
        assertThat(captured.context().endpoint().baseUrl().toString())
                .isEqualTo("https://api.deepseek.com/v1");
        assertThat(captured.context().endpoint().apiKey()).isEqualTo(USER_KEY);
        assertThat(captured.context().endpoint().parameters()).containsEntry("temperature", 0.2);
        verify(modelProperties).resolve(ModelConfig.KeySource.USER, USER_KEY);
        verify(quotaService).checkFrequency("test-ip-digest");
        verify(quotaService).acquireUpstreamCalls(ModelConfig.KeySource.USER, 1);
    }

    @ParameterizedTest
    @MethodSource("gatewayFailures")
    void mapsGatewayFailuresToStableSafeApiErrors(
            ModelGatewayException.Kind kind,
            int expectedStatus,
            String expectedCode) {
        when(modelProperties.resolve(ModelConfig.KeySource.USER, USER_KEY))
                .thenReturn(ModelConfig.user(USER_KEY));
        when(modelGateway.testConnection(any())).thenReturn(Mono.error(
                new ModelGatewayException(kind, "upstream secret " + USER_KEY)));

        webTestClient.post()
                .uri("/api/model-config/test")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "keySource": "USER",
                          "provider": "OPENAI",
                          "model": "gpt-test",
                          "apiKey": "sk-controller-secret"
                        }
                        """)
                .exchange()
                .expectStatus().isEqualTo(expectedStatus)
                .expectBody()
                .jsonPath("$.code").isEqualTo(expectedCode)
                .jsonPath("$.message").value(message ->
                        assertThat(String.valueOf(message)).doesNotContain(USER_KEY));
    }

    private static Stream<Arguments> gatewayFailures() {
        return Stream.of(
                Arguments.of(ModelGatewayException.Kind.UNREACHABLE, 502, "SERVICE_UNAVAILABLE"),
                Arguments.of(ModelGatewayException.Kind.AUTHENTICATION, 502, "UNAUTHORIZED"),
                Arguments.of(ModelGatewayException.Kind.MODEL_NOT_FOUND, 404, "MODEL_NOT_FOUND"),
                Arguments.of(ModelGatewayException.Kind.RATE_LIMITED, 429, "RATE_LIMIT_EXCEEDED"),
                Arguments.of(ModelGatewayException.Kind.FORMAT_INCOMPATIBLE, 422, "FORMAT_INCOMPATIBLE")
        );
    }
}
