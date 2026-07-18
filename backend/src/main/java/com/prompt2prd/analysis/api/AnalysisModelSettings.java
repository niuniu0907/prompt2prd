package com.prompt2prd.analysis.api;

import com.prompt2prd.model.domain.ModelConfig;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;
import java.net.URI;

public record AnalysisModelSettings(
        @NotNull ModelConfig.KeySource keySource,
        @NotNull Provider provider,
        String baseUrl,
        @NotBlank String model,
        String apiKey,
        Map<String, Object> parameters) {
    public AnalysisModelSettings {
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
    }

    public enum Provider {
        OPENAI("https://api.openai.com/v1"),
        DEEPSEEK("https://api.deepseek.com/v1"),
        CUSTOM(null);

        private final String presetBaseUrl;

        Provider(String presetBaseUrl) {
            this.presetBaseUrl = presetBaseUrl;
        }

        public URI resolveBaseUrl(String customBaseUrl) {
            String value = presetBaseUrl == null ? customBaseUrl : presetBaseUrl;
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Model base URL must not be blank");
            }
            return URI.create(value.trim());
        }
    }
}
