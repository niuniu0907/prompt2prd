package com.prompt2prd.common.config;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

/**
 * Central configuration for generation timeouts, retries, and heartbeat.
 * <p>
 * All values are read from environment variables with conservative defaults.
 * Controllers and orchestrators that issue model calls consult this component
 * rather than hard-coding duration constants.
 */
@Component
public final class GenerationProperties {

    public static final String CONNECTION_TIMEOUT_SECONDS_ENV = "PROMPT2PRD_GENERATION_CONNECTION_TIMEOUT_SECONDS";
    public static final String TOTAL_TIMEOUT_SECONDS_ENV = "PROMPT2PRD_GENERATION_TOTAL_TIMEOUT_SECONDS";
    public static final String MAX_RETRIES_ENV = "PROMPT2PRD_GENERATION_MAX_RETRIES";
    public static final String RETRY_BACKOFF_MS_ENV = "PROMPT2PRD_GENERATION_RETRY_BACKOFF_MS";
    public static final String HEARTBEAT_INTERVAL_SECONDS_ENV = "PROMPT2PRD_GENERATION_HEARTBEAT_INTERVAL_SECONDS";

    private static final Duration DEFAULT_CONNECTION_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration DEFAULT_TOTAL_TIMEOUT = Duration.ofMinutes(5);
    private static final int DEFAULT_MAX_RETRIES = 3;
    private static final Duration DEFAULT_RETRY_BACKOFF = Duration.ofMillis(800);
    private static final Duration DEFAULT_HEARTBEAT_INTERVAL = Duration.ofSeconds(5);

    private final Duration connectionTimeout;
    private final Duration totalTimeout;
    private final int maxRetries;
    private final Duration retryBackoff;
    private final Duration heartbeatInterval;

    public GenerationProperties() {
        this(System.getenv());
    }

    private GenerationProperties(Map<String, String> environment) {
        this.connectionTimeout = parseDuration(environment, CONNECTION_TIMEOUT_SECONDS_ENV, DEFAULT_CONNECTION_TIMEOUT);
        this.totalTimeout = parseDuration(environment, TOTAL_TIMEOUT_SECONDS_ENV, DEFAULT_TOTAL_TIMEOUT);
        this.maxRetries = parseInt(environment, MAX_RETRIES_ENV, DEFAULT_MAX_RETRIES);
        this.retryBackoff = parseMillis(environment, RETRY_BACKOFF_MS_ENV, DEFAULT_RETRY_BACKOFF);
        this.heartbeatInterval = parseDuration(environment, HEARTBEAT_INTERVAL_SECONDS_ENV, DEFAULT_HEARTBEAT_INTERVAL);
    }

    public GenerationProperties(
            Duration connectionTimeout,
            Duration totalTimeout,
            int maxRetries,
            Duration retryBackoff,
            Duration heartbeatInterval) {
        this.connectionTimeout = requirePositive(connectionTimeout, "connectionTimeout");
        this.totalTimeout = requirePositive(totalTimeout, "totalTimeout");
        this.retryBackoff = requirePositive(retryBackoff, "retryBackoff");
        this.heartbeatInterval = requirePositive(heartbeatInterval, "heartbeatInterval");
        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries must not be negative");
        }
        this.maxRetries = maxRetries;
    }

    /** Testable factory that preserves the production environment-variable boundary. */
    public static GenerationProperties fromEnvironment(Map<String, String> environment) {
        return new GenerationProperties(Map.copyOf(environment));
    }

    public Duration connectionTimeout() {
        return connectionTimeout;
    }

    public Duration totalTimeout() {
        return totalTimeout;
    }

    public int maxRetries() {
        return maxRetries;
    }

    public Duration retryBackoff() {
        return retryBackoff;
    }

    public Duration heartbeatInterval() {
        return heartbeatInterval;
    }

    @Override
    public String toString() {
        return "GenerationProperties[connectionTimeout=" + connectionTimeout
                + ", totalTimeout=" + totalTimeout
                + ", maxRetries=" + maxRetries
                + ", retryBackoff=" + retryBackoff
                + ", heartbeatInterval=" + heartbeatInterval + "]";
    }

    private static Duration parseDuration(Map<String, String> env, String key, Duration fallback) {
        String raw = env.get(key);
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return Duration.ofSeconds(Long.parseLong(raw.trim()));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + " must be a valid number of seconds");
        }
    }

    private static Duration parseMillis(Map<String, String> env, String key, Duration fallback) {
        String raw = env.get(key);
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return Duration.ofMillis(Long.parseLong(raw.trim()));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + " must be a valid number of milliseconds");
        }
    }

    private static int parseInt(Map<String, String> env, String key, int fallback) {
        String raw = env.get(key);
        if (raw == null || raw.isBlank()) return fallback;
        try {
            int value = Integer.parseInt(raw.trim());
            if (value < 0) throw new IllegalArgumentException(key + " must not be negative");
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + " must be a valid integer");
        }
    }

    private static Duration requirePositive(Duration duration, String name) {
        if (duration == null || duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return duration;
    }
}
