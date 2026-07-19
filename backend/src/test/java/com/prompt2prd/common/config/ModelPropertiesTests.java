package com.prompt2prd.common.config;

import com.prompt2prd.model.domain.ModelConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModelPropertiesTests {

    private static final String TEST_KEY = "sk-step17-sensitive-value";

    @Test
    void systemKeyModeIsUnavailableWhenNeitherSettingExists() {
        contextRunner(Map.of()).run(context -> {
            ModelProperties properties = context.getBean(ModelProperties.class);

            assertThat(properties.systemKeyAvailable()).isFalse();
            assertThat(properties.systemConfig()).isEmpty();
        });
    }

    @Test
    void systemKeyModeIsUnavailableWhenOnlyTheKeyExists() {
        contextRunner(Map.of(ModelProperties.SYSTEM_API_KEY_ENV, TEST_KEY))
                .run(context -> assertThat(context.getBean(ModelProperties.class)
                        .systemKeyAvailable()).isFalse());
    }

    @Test
    void systemKeyModeIsUnavailableWhenOnlyTheEnableFlagExists() {
        contextRunner(Map.of(ModelProperties.SYSTEM_KEY_ENABLED_ENV, "true"))
                .run(context -> assertThat(context.getBean(ModelProperties.class)
                        .systemKeyAvailable()).isFalse());
    }

    @Test
    void systemKeyModeIsAvailableOnlyWhenTheFlagAndKeyBothExist() {
        contextRunner(Map.of(
                        ModelProperties.SYSTEM_KEY_ENABLED_ENV, "true",
                        ModelProperties.SYSTEM_API_KEY_ENV, TEST_KEY
                ))
                .run(context -> {
                    ModelProperties properties = context.getBean(ModelProperties.class);

                    assertThat(properties.systemKeyAvailable()).isTrue();
                    assertThat(properties.systemConfig())
                            .contains(ModelConfig.system(TEST_KEY));
                    assertThat(properties.toString()).doesNotContain(TEST_KEY);
                });
    }

    @Test
    void anExplicitUserSelectionNeverFallsBackToTheSystemKey() {
        contextRunner(Map.of(
                        ModelProperties.SYSTEM_KEY_ENABLED_ENV, "true",
                        ModelProperties.SYSTEM_API_KEY_ENV, TEST_KEY
                ))
                .run(context -> {
                    ModelProperties properties = context.getBean(ModelProperties.class);

                    assertThatThrownBy(() -> properties.resolve(ModelConfig.KeySource.USER, " "))
                            .isInstanceOf(IllegalArgumentException.class)
                            .hasMessage("User API key must not be blank")
                            .hasMessageNotContaining(TEST_KEY);
                });
    }

    private ApplicationContextRunner contextRunner(Map<String, String> environment) {
        return new ApplicationContextRunner()
                .withBean(ModelProperties.class, () -> ModelProperties.fromEnvironment(environment));
    }
}
