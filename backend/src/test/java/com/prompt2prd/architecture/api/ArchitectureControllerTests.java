package com.prompt2prd.architecture.api;

import com.prompt2prd.architecture.application.ArchitectureRecommender;
import com.prompt2prd.common.api.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.Map;
import java.util.UUID;

class ArchitectureControllerTests {

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToController(new ArchitectureController(new ArchitectureRecommender()))
                .controllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void returnsComparableCandidatesAndPendingFields() {
        client.post().uri("/api/architecture/recommend")
                .bodyValue(Map.ofEntries(
                        Map.entry("projectId", UUID.randomUUID().toString()),
                        Map.entry("knownTechnologies", java.util.List.of("Vue 3", "Java", "Spring Boot")),
                        Map.entry("targetPlatform", "WEB"),
                        Map.entry("teamSize", "SOLO"),
                        Map.entry("userScale", "SMALL"),
                        Map.entry("criticalCapabilities", Map.of("login", false, "realtime", false, "payments", false, "fileUpload", true, "ai", true)),
                        Map.entry("dataSensitivity", "PERSONAL"),
                        Map.entry("deployment", "MONOLITHIC_DOCKER"),
                        Map.entry("budget", "MINIMAL"),
                        Map.entry("timeline", "RAPID"),
                        Map.entry("maintenanceCapacity", "LOW")))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.candidates.length()").isEqualTo(3)
                .jsonPath("$.candidates[0].stack.frontend").exists()
                .jsonPath("$.candidates[0].scores.LEARNING_COST").exists()
                .jsonPath("$.pendingFields.length()").isEqualTo(0);
    }

    @Test
    void rejectsMissingProjectId() {
        client.post().uri("/api/architecture/recommend")
                .bodyValue(Map.of("knownTechnologies", java.util.List.of("Vue")))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.code").isEqualTo("BAD_REQUEST");
    }

    @Test
    void rejectsUnknownEnum() {
        client.post().uri("/api/architecture/recommend")
                .bodyValue(Map.of("projectId", UUID.randomUUID().toString(), "targetPlatform", "WATCH"))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.code").isEqualTo("BAD_REQUEST");
    }
}
