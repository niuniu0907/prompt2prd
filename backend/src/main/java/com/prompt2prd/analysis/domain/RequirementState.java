package com.prompt2prd.analysis.domain;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record RequirementState(
        @NotNull @Valid ProjectSummary project,
        @NotNull List<@Valid RequirementItem> requirements,
        @NotNull List<@Valid ClarificationQuestion> questions,
        @NotNull List<@Valid ClarificationAnswer> answers,
        @NotNull List<@Valid RequirementConflict> conflicts,
        @NotNull @Valid CompletenessScore completeness) {

    public RequirementState {
        requirements = requirements == null ? List.of() : List.copyOf(requirements);
        questions = questions == null ? List.of() : List.copyOf(questions);
        answers = answers == null ? List.of() : List.copyOf(answers);
        conflicts = conflicts == null ? List.of() : List.copyOf(conflicts);
    }
}
