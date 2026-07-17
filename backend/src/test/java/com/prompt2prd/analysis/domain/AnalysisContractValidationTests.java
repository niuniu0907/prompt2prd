package com.prompt2prd.analysis.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnalysisContractValidationTests {

    private static final UUID PROJECT_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID REQUIREMENT_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final Instant NOW = Instant.parse("2026-07-17T12:00:00Z");
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void parsesTheSharedContractFixtureWithUtcInstantsAndKnownEnums() throws Exception {
        RequirementState state = objectMapper.readValue(
                Files.readString(sharedFixture()), RequirementState.class);

        assertThat(state.project().id()).isEqualTo(PROJECT_ID);
        assertThat(state.project().language()).isEqualTo("zh-CN");
        assertThat(state.requirements()).singleElement().satisfies(requirement -> {
            assertThat(requirement.status()).isEqualTo(RequirementStatus.CONFIRMED);
            assertThat(requirement.createdAt()).isEqualTo(NOW);
            assertThat(requirement.locked()).isTrue();
        });
        assertThat(state.questions()).singleElement().satisfies(question ->
                assertThat(question.dimension()).isEqualTo(RequirementDimension.ROLES_PERMISSIONS));
        assertThat(validator.validate(state)).isEmpty();
    }

    @Test
    void rejectsMissingIdsAndSourcesThroughJakartaValidation() {
        RequirementItem missingId = new RequirementItem(
                null, PROJECT_ID, RequirementType.FEATURE, "预约", "支持预约",
                RequirementStatus.PENDING, RequirementSourceType.AI_INFERENCE, null,
                false, Map.of(), NOW, NOW);
        RequirementItem missingSource = new RequirementItem(
                REQUIREMENT_ID, PROJECT_ID, RequirementType.FEATURE, "预约", "支持预约",
                RequirementStatus.PENDING, null, null, false, Map.of(), NOW, NOW);

        assertThat(validator.validate(missingId)).anyMatch(violation ->
                violation.getPropertyPath().toString().equals("id"));
        assertThat(validator.validate(missingSource)).anyMatch(violation ->
                violation.getPropertyPath().toString().equals("sourceType"));
    }

    @Test
    void rejectsLockingAnythingExceptAConfirmedRequirement() {
        assertThatThrownBy(() -> new RequirementItem(
                REQUIREMENT_ID, PROJECT_ID, RequirementType.BUSINESS_RULE,
                "支付规则", "先支付", RequirementStatus.INFERRED,
                RequirementSourceType.AI_INFERENCE, null, true, Map.of(), NOW, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CONFIRMED");
    }

    @Test
    void rejectsUnknownStatusDuringJsonParsing() throws Exception {
        String fixture = Files.readString(sharedFixture())
                .replaceFirst("\"CONFIRMED\"", "\"UNKNOWN\"");

        assertThatThrownBy(() -> objectMapper.readValue(fixture, RequirementState.class))
                .isInstanceOf(InvalidFormatException.class);
    }

    @Test
    void rejectsCompletenessOutsideZeroToOneHundred() {
        assertThatThrownBy(() -> new ProjectSummary(
                PROJECT_ID, "项目", "zh-CN", ProjectStage.CLARIFYING, 101))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CompletenessScore(-1, List.of(), 0, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void stateAndNestedCollectionsAreDefensiveCopies() {
        RequirementItem requirement = validRequirement();
        ArrayList<RequirementItem> mutableRequirements = new ArrayList<>(List.of(requirement));
        RequirementState state = new RequirementState(
                new ProjectSummary(PROJECT_ID, "项目", "zh-CN", ProjectStage.CLARIFYING, 0),
                mutableRequirements, List.of(), List.of(), List.of(),
                new CompletenessScore(0, List.of(), 0, false));

        mutableRequirements.clear();

        assertThat(state.requirements()).containsExactly(requirement);
        assertThatThrownBy(() -> state.requirements().add(requirement))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private RequirementItem validRequirement() {
        return new RequirementItem(
                REQUIREMENT_ID, PROJECT_ID, RequirementType.PRODUCT_GOAL,
                "目标", "明确目标", RequirementStatus.CONFIRMED,
                RequirementSourceType.INITIAL_INPUT, null, true, Map.of(), NOW, NOW);
    }

    private Path sharedFixture() {
        Path userDirectory = Path.of(System.getProperty("user.dir"));
        Path fromModule = userDirectory.resolve("..").resolve("contracts")
                .resolve("analysis-state.sample.json").normalize();
        if (Files.exists(fromModule)) {
            return fromModule;
        }
        return userDirectory.resolve("contracts").resolve("analysis-state.sample.json");
    }
}
