package com.prompt2prd.analysis.domain;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record CompletenessInput(
        List<RequirementItem> requirements,
        List<ClarificationQuestion> questions,
        List<RequirementConflict> conflicts,
        Set<RequirementDimension> notApplicable,
        Map<RequirementDimension, Integer> missingTargets) {

    public CompletenessInput {
        requirements = requirements == null ? List.of() : List.copyOf(requirements);
        questions = questions == null ? List.of() : List.copyOf(questions);
        conflicts = conflicts == null ? List.of() : List.copyOf(conflicts);
        notApplicable = notApplicable == null || notApplicable.isEmpty()
                ? Set.of()
                : Set.copyOf(EnumSet.copyOf(notApplicable));
        EnumMap<RequirementDimension, Integer> checked =
                new EnumMap<>(RequirementDimension.class);
        if (missingTargets != null) {
            missingTargets.forEach((dimension, count) -> {
                if (dimension == null || count == null || count < 0) {
                    throw new IllegalArgumentException("Missing-target counts must be non-negative");
                }
                checked.put(dimension, count);
            });
        }
        missingTargets = Map.copyOf(checked);
    }

    public static CompletenessInput fromState(RequirementState state) {
        return new CompletenessInput(
                state.requirements(), state.questions(), state.conflicts(), Set.of(), Map.of());
    }
}
