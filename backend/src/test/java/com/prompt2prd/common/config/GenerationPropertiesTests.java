package com.prompt2prd.common.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GenerationPropertiesTests {

    @Test
    void usesDefaultValuesWhenNoEnvironmentVariablesAreSet() {
        GenerationProperties props = GenerationProperties.fromEnvironment(Map.of());

        assertEquals(Duration.ofSeconds(30), props.connectionTimeout());
        assertEquals(Duration.ofMinutes(5), props.totalTimeout());
        assertEquals(3, props.maxRetries());
        assertEquals(Duration.ofMillis(800), props.retryBackoff());
        assertEquals(Duration.ofSeconds(5), props.heartbeatInterval());
    }

    @Test
    void readsAllValuesFromEnvironment() {
        GenerationProperties props = GenerationProperties.fromEnvironment(Map.of(
                GenerationProperties.CONNECTION_TIMEOUT_SECONDS_ENV, "15",
                GenerationProperties.TOTAL_TIMEOUT_SECONDS_ENV, "120",
                GenerationProperties.MAX_RETRIES_ENV, "5",
                GenerationProperties.RETRY_BACKOFF_MS_ENV, "500",
                GenerationProperties.HEARTBEAT_INTERVAL_SECONDS_ENV, "10"
        ));

        assertEquals(Duration.ofSeconds(15), props.connectionTimeout());
        assertEquals(Duration.ofSeconds(120), props.totalTimeout());
        assertEquals(5, props.maxRetries());
        assertEquals(Duration.ofMillis(500), props.retryBackoff());
        assertEquals(Duration.ofSeconds(10), props.heartbeatInterval());
    }

    @Test
    void zeroRetriesIsAllowedAsConfiguration() {
        GenerationProperties props = GenerationProperties.fromEnvironment(Map.of(
                GenerationProperties.MAX_RETRIES_ENV, "0"
        ));
        assertEquals(0, props.maxRetries());
    }

    @Test
    void negativeRetriesInConfigurationAreRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                GenerationProperties.fromEnvironment(Map.of(
                        GenerationProperties.MAX_RETRIES_ENV, "-1"
                )));
    }

    @Test
    void explicitConstructorValidatesPositiveDurations() {
        assertThrows(IllegalArgumentException.class, () ->
                new GenerationProperties(Duration.ZERO, Duration.ofMinutes(5), 3, Duration.ofMillis(800), Duration.ofSeconds(5)));
        assertThrows(IllegalArgumentException.class, () ->
                new GenerationProperties(Duration.ofSeconds(30), Duration.ZERO, 3, Duration.ofMillis(800), Duration.ofSeconds(5)));
        assertThrows(IllegalArgumentException.class, () ->
                new GenerationProperties(Duration.ofSeconds(30), Duration.ofMinutes(5), 3, Duration.ZERO, Duration.ofSeconds(5)));
        assertThrows(IllegalArgumentException.class, () ->
                new GenerationProperties(Duration.ofSeconds(30), Duration.ofMinutes(5), 3, Duration.ofMillis(800), Duration.ZERO));
    }

    @Test
    void toStringDoesNotLeakSecrets() {
        GenerationProperties props = new GenerationProperties(
                Duration.ofSeconds(30), Duration.ofMinutes(5), 3, Duration.ofMillis(800), Duration.ofSeconds(5));
        String text = props.toString();
        assertTrue(text.contains("connectionTimeout"));
        assertTrue(text.contains("totalTimeout"));
        assertTrue(text.contains("maxRetries"));
    }
}
