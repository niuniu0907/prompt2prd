package com.prompt2prd.common.config;

import com.prompt2prd.model.domain.ModelConfig;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * Server-side model credential settings.
 *
 * <p>Production deployments provide these values through
 * {@code PROMPT2PRD_MODEL_SYSTEM_KEY_ENABLED} and
 * {@code PROMPT2PRD_MODEL_SYSTEM_API_KEY}. The system-key capability remains
 * unavailable unless both values are present and the flag is {@code true}.</p>
 */
@Component
public final class ModelProperties {

    public static final String SYSTEM_KEY_ENABLED_ENV = "PROMPT2PRD_MODEL_SYSTEM_KEY_ENABLED";
    public static final String SYSTEM_API_KEY_ENV = "PROMPT2PRD_MODEL_SYSTEM_API_KEY";

    private final boolean systemKeyEnabled;
    private final String systemApiKey;

    public ModelProperties() {
        this(System.getenv());
    }

    private ModelProperties(Map<String, String> environment) {
        systemKeyEnabled = Boolean.parseBoolean(environment.get(SYSTEM_KEY_ENABLED_ENV));
        String configuredKey = environment.get(SYSTEM_API_KEY_ENV);
        systemApiKey = configuredKey == null ? "" : configuredKey.trim();
    }

    /** Testable factory that preserves the production environment-variable boundary. */
    public static ModelProperties fromEnvironment(Map<String, String> environment) {
        return new ModelProperties(Map.copyOf(environment));
    }

    public boolean systemKeyAvailable() {
        return systemKeyEnabled && !systemApiKey.isBlank();
    }

    public Optional<ModelConfig> systemConfig() {
        if (!systemKeyAvailable()) {
            return Optional.empty();
        }
        return Optional.of(ModelConfig.system(systemApiKey));
    }

    /** Resolves only the source the user selected; this method never falls back. */
    public ModelConfig resolve(ModelConfig.KeySource keySource, String userApiKey) {
        return switch (keySource) {
            case USER -> ModelConfig.user(userApiKey);
            case SYSTEM -> systemConfig().orElseThrow(
                    () -> new IllegalStateException("System key mode is unavailable")
            );
        };
    }

    @Override
    public String toString() {
        return "ModelProperties[systemKeyEnabled=" + systemKeyEnabled
                + ", systemKeyAvailable=" + systemKeyAvailable()
                + ", systemApiKey=[REDACTED]]";
    }
}
