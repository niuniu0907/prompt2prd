package com.prompt2prd.analysis.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Fixed, explainable completeness calculation from design document V1.1. */
public final class CompletenessCalculator {

    public CompletenessScore calculate(CompletenessInput input) {
        if (input == null) {
            throw new IllegalArgumentException("input must not be null");
        }
        List<CompletenessDimensionScore> dimensions = new ArrayList<>();
        long weightedTotal = 0;
        int applicableWeight = 0;

        for (RequirementDimension dimension : RequirementDimension.values()) {
            if (input.notApplicable().contains(dimension)) {
                dimensions.add(new CompletenessDimensionScore(
                        dimension, false, 0, List.of("Dimension is not applicable")));
                continue;
            }
            DimensionCalculation calculated = calculateDimension(input, dimension);
            dimensions.add(new CompletenessDimensionScore(
                    dimension, true, calculated.score(), calculated.reasons()));
            weightedTotal += (long) calculated.score() * dimension.weight();
            applicableWeight += dimension.weight();
        }

        int total = applicableWeight == 0
                ? 0
                : clamp((int) Math.round((double) weightedTotal / applicableWeight));
        int pendingCount = (int) input.requirements().stream()
                .filter(item -> item.status() == RequirementStatus.PENDING
                        || item.status() == RequirementStatus.CONFLICTED)
                .count()
                + (int) input.questions().stream()
                .filter(question -> question.status() != QuestionStatus.ANSWERED)
                .count();
        boolean hasCoreConflict = input.conflicts().stream()
                .anyMatch(conflict -> conflict.status() == ConflictStatus.OPEN && conflict.core());
        return new CompletenessScore(total, dimensions, pendingCount, hasCoreConflict);
    }

    private DimensionCalculation calculateDimension(
            CompletenessInput input,
            RequirementDimension dimension) {
        List<RequirementItem> items = input.requirements().stream()
                .filter(item -> dimensionOf(item).filter(dimension::equals).isPresent())
                .toList();
        int missing = input.missingTargets().getOrDefault(dimension, 0);
        int unanswered = (int) input.questions().stream()
                .filter(question -> question.dimension() == dimension)
                .filter(question -> question.status() != QuestionStatus.ANSWERED)
                .count();
        int denominator = items.size() + missing + unanswered;
        List<String> reasons = new ArrayList<>();
        if (denominator == 0) {
            reasons.add("No target items are available for this dimension");
            return new DimensionCalculation(0, reasons);
        }

        int confirmed = 0;
        int inferred = 0;
        int zeroScore = 0;
        int points = 0;
        for (RequirementItem item : items) {
            switch (item.status()) {
                case CONFIRMED -> {
                    confirmed++;
                    points += 100;
                }
                case INFERRED -> {
                    inferred++;
                    points += 40;
                }
                case PENDING, CONFLICTED -> zeroScore++;
            }
        }
        int score = clamp((int) Math.round((double) points / denominator));
        reasons.add(confirmed + " confirmed item(s)");
        reasons.add(inferred + " inferred item(s)");
        if (zeroScore > 0) {
            reasons.add(zeroScore + " pending or conflicted item(s) score zero");
        }
        if (missing > 0) {
            reasons.add(missing + " missing target(s) remain in the denominator");
        }
        if (unanswered > 0) {
            reasons.add(unanswered + " unanswered question(s) remain in the denominator");
        }
        boolean coreConflict = input.conflicts().stream()
                .anyMatch(conflict -> conflict.dimension() == dimension
                        && conflict.core()
                        && conflict.status() == ConflictStatus.OPEN);
        if (coreConflict && score > 50) {
            score = 50;
            reasons.add("An open core conflict caps this dimension at 50");
        }
        return new DimensionCalculation(score, reasons);
    }

    public Optional<RequirementDimension> dimensionOf(RequirementItem item) {
        Object override = item.metadata().get("dimension");
        if (override instanceof String value) {
            try {
                return Optional.of(RequirementDimension.valueOf(
                        value.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                return Optional.empty();
            }
        }
        return switch (item.type()) {
            case PRODUCT_GOAL -> Optional.of(RequirementDimension.PRODUCT_SCOPE);
            case ROLE -> Optional.of(RequirementDimension.ROLES_PERMISSIONS);
            case USER_STORY -> Optional.of(RequirementDimension.CORE_FLOW);
            case FEATURE -> Optional.of(RequirementDimension.FEATURES);
            case BUSINESS_RULE -> Optional.of(RequirementDimension.BUSINESS_RULES);
            case EXCEPTION_SCENARIO -> Optional.of(RequirementDimension.EXCEPTIONS);
            case DATA_MODEL -> Optional.of(RequirementDimension.DATA_MODEL);
            case TECHNICAL_CONSTRAINT -> Optional.of(
                    RequirementDimension.ARCHITECTURE_CONSTRAINTS);
            case PAGE, API -> Optional.of(RequirementDimension.PAGES_APIS);
            case ACCEPTANCE_CRITERION -> Optional.of(RequirementDimension.ACCEPTANCE);
            case NON_FUNCTIONAL_REQUIREMENT -> Optional.of(
                    RequirementDimension.ARCHITECTURE_CONSTRAINTS);
            case IMPLEMENTATION_PHASE, CODING_AGENT_CONSTRAINT, ASSUMPTION,
                    RISK_OPEN_ITEM, MISSING_INFORMATION -> Optional.empty();
        };
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private record DimensionCalculation(int score, List<String> reasons) {
    }
}
