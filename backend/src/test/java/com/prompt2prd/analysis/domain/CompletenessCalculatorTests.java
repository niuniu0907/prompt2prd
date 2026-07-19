package com.prompt2prd.analysis.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CompletenessCalculatorTests {

    private static final UUID PROJECT_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final Instant NOW = Instant.parse("2026-07-17T12:00:00Z");
    private final CompletenessCalculator calculator = new CompletenessCalculator();

    @Test
    void allConfirmedDimensionsProduceOneHundred() {
        List<RequirementItem> items = new ArrayList<>();
        for (RequirementDimension dimension : RequirementDimension.values()) {
            items.add(item(dimension, RequirementStatus.CONFIRMED));
        }

        CompletenessScore score = calculator.calculate(input(items));

        assertThat(score.total()).isEqualTo(100);
        assertThat(score.dimensions()).allMatch(dimension -> dimension.score() == 100);
        assertThat(score.pendingCount()).isZero();
    }

    @Test
    void completelyMissingApplicableStateProducesZeroWithReasons() {
        CompletenessScore score = calculator.calculate(input(List.of()));

        assertThat(score.total()).isZero();
        assertThat(score.dimensions()).hasSize(10).allMatch(dimension -> dimension.score() == 0);
        assertThat(score.dimensions()).allSatisfy(dimension ->
                assertThat(dimension.reasons()).isNotEmpty());
    }

    @Test
    void fixedWeightsGiveCoreFlowFifteenAndFeaturesAndArchitectureTwelveEach() {
        CompletenessScore coreFlow = calculator.calculate(input(List.of(
                item(RequirementDimension.CORE_FLOW, RequirementStatus.CONFIRMED))));
        CompletenessScore features = calculator.calculate(input(List.of(
                item(RequirementDimension.FEATURES, RequirementStatus.CONFIRMED))));
        CompletenessScore architecture = calculator.calculate(input(List.of(
                item(RequirementDimension.ARCHITECTURE_CONSTRAINTS, RequirementStatus.CONFIRMED))));

        assertThat(coreFlow.total()).isEqualTo(15);
        assertThat(features.total()).isEqualTo(12);
        assertThat(architecture.total()).isEqualTo(12);
    }

    @Test
    void inferredScoresFortyWhilePendingAndConflictedScoreZero() {
        CompletenessScore inferred = calculator.calculate(onlyApplicable(
                RequirementDimension.PRODUCT_SCOPE,
                List.of(item(RequirementDimension.PRODUCT_SCOPE, RequirementStatus.INFERRED))));
        CompletenessScore pending = calculator.calculate(onlyApplicable(
                RequirementDimension.PRODUCT_SCOPE,
                List.of(item(RequirementDimension.PRODUCT_SCOPE, RequirementStatus.PENDING))));
        CompletenessScore conflicted = calculator.calculate(onlyApplicable(
                RequirementDimension.PRODUCT_SCOPE,
                List.of(item(RequirementDimension.PRODUCT_SCOPE, RequirementStatus.CONFLICTED))));

        assertThat(inferred.total()).isEqualTo(40);
        assertThat(pending.total()).isZero();
        assertThat(conflicted.total()).isZero();
    }

    @Test
    void missingTargetsAndUnansweredQuestionsStayInTheDenominator() {
        RequirementItem confirmed = item(
                RequirementDimension.BUSINESS_RULES, RequirementStatus.CONFIRMED);
        ClarificationQuestion pendingQuestion = question(RequirementDimension.BUSINESS_RULES);
        EnumMap<RequirementDimension, Integer> missing = new EnumMap<>(RequirementDimension.class);
        missing.put(RequirementDimension.BUSINESS_RULES, 1);
        CompletenessInput input = new CompletenessInput(
                List.of(confirmed), List.of(pendingQuestion), List.of(),
                allExcept(RequirementDimension.BUSINESS_RULES), missing);

        CompletenessScore score = calculator.calculate(input);

        assertThat(score.total()).isEqualTo(33);
        assertThat(score.pendingCount()).isEqualTo(1);
        assertThat(score.dimensions()).filteredOn(CompletenessDimensionScore::applicable)
                .singleElement().satisfies(dimension ->
                        assertThat(dimension.reasons()).anyMatch(reason -> reason.contains("missing")));
    }

    @Test
    void coreConflictCapsItsDimensionAtFifty() {
        RequirementItem confirmed = item(
                RequirementDimension.ROLES_PERMISSIONS, RequirementStatus.CONFIRMED);
        RequirementConflict conflict = new RequirementConflict(
                UUID.randomUUID(), PROJECT_ID, confirmed.id(), null,
                "仅宠物店", "允许个人", RequirementDimension.ROLES_PERMISSIONS,
                "角色准入冲突", true, ConflictStatus.OPEN, null, NOW, NOW, null);
        CompletenessInput input = new CompletenessInput(
                List.of(confirmed), List.of(), List.of(conflict),
                allExcept(RequirementDimension.ROLES_PERMISSIONS), Map.of());

        CompletenessScore score = calculator.calculate(input);

        assertThat(score.total()).isEqualTo(50);
        assertThat(score.hasCoreConflict()).isTrue();
    }

    @Test
    void nonApplicableDimensionsAreExcludedAndCannotLowerTheTotal() {
        CompletenessScore score = calculator.calculate(onlyApplicable(
                RequirementDimension.ACCEPTANCE,
                List.of(item(RequirementDimension.ACCEPTANCE, RequirementStatus.CONFIRMED))));

        assertThat(score.total()).isEqualTo(100);
        assertThat(score.dimensions()).filteredOn(dimension -> !dimension.applicable())
                .allMatch(dimension -> dimension.score() == 0);
    }

    private CompletenessInput input(List<RequirementItem> items) {
        return new CompletenessInput(items, List.of(), List.of(), EnumSet.noneOf(
                RequirementDimension.class), Map.of());
    }

    private CompletenessInput onlyApplicable(
            RequirementDimension dimension,
            List<RequirementItem> items) {
        return new CompletenessInput(items, List.of(), List.of(), allExcept(dimension), Map.of());
    }

    private EnumSet<RequirementDimension> allExcept(RequirementDimension dimension) {
        EnumSet<RequirementDimension> excluded = EnumSet.allOf(RequirementDimension.class);
        excluded.remove(dimension);
        return excluded;
    }

    private RequirementItem item(RequirementDimension dimension, RequirementStatus status) {
        RequirementType type = switch (dimension) {
            case PRODUCT_SCOPE -> RequirementType.PRODUCT_GOAL;
            case ROLES_PERMISSIONS -> RequirementType.ROLE;
            case CORE_FLOW -> RequirementType.USER_STORY;
            case FEATURES -> RequirementType.FEATURE;
            case BUSINESS_RULES -> RequirementType.BUSINESS_RULE;
            case EXCEPTIONS -> RequirementType.EXCEPTION_SCENARIO;
            case DATA_MODEL -> RequirementType.DATA_MODEL;
            case ARCHITECTURE_CONSTRAINTS -> RequirementType.TECHNICAL_CONSTRAINT;
            case PAGES_APIS -> RequirementType.API;
            case ACCEPTANCE -> RequirementType.ACCEPTANCE_CRITERION;
        };
        return new RequirementItem(
                UUID.randomUUID(), PROJECT_ID, type, dimension.name(), "content",
                status, RequirementSourceType.USER_ANSWER, null, false,
                Map.of(), NOW, NOW);
    }

    private ClarificationQuestion question(RequirementDimension dimension) {
        return new ClarificationQuestion(
                UUID.randomUUID(), PROJECT_ID, UUID.randomUUID(), 0, "question", "reason",
                dimension, "target", "semantic", QuestionInputType.TEXT, List.of(),
                List.of(), 4, QuestionStatus.PENDING, NOW, NOW);
    }
}
