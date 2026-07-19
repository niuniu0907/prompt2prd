package com.prompt2prd.model.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModelConfigTests {

    @Test
    void keepsTheExplicitKeySourceWithoutExposingTheCredential() {
        String key = "sk-model-config-sensitive";
        ModelConfig config = ModelConfig.user(key);

        assertThat(config.keySource()).isEqualTo(ModelConfig.KeySource.USER);
        assertThat(config.apiKey()).isEqualTo(key);
        assertThat(config.toString()).doesNotContain(key);
        assertThat(config.toString()).contains("USER", "[REDACTED]");
    }

    @Test
    void rejectsBlankCredentialsForBothSources() {
        assertThatThrownBy(() -> ModelConfig.user("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("User API key must not be blank");
        assertThatThrownBy(() -> ModelConfig.system(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("System API key must not be blank");
    }
}
