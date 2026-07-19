package com.prompt2prd.model.domain;

import java.util.Objects;

/**
 * A credential selected explicitly for one model request.
 *
 * <p>The value is intentionally runtime-only. Callers must never persist this
 * object, include it in error payloads, or log the {@link #apiKey()} accessor.</p>
 */
public record ModelConfig(KeySource keySource, String apiKey) {

    public ModelConfig {
        Objects.requireNonNull(keySource, "keySource");
        String label = keySource == KeySource.USER ? "User" : "System";
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException(label + " API key must not be blank");
        }
        apiKey = apiKey.trim();
    }

    public static ModelConfig user(String apiKey) {
        return new ModelConfig(KeySource.USER, apiKey);
    }

    public static ModelConfig system(String apiKey) {
        return new ModelConfig(KeySource.SYSTEM, apiKey);
    }

    @Override
    public String toString() {
        return "ModelConfig[keySource=" + keySource + ", apiKey=[REDACTED]]";
    }

    public enum KeySource {
        SYSTEM,
        USER
    }
}
