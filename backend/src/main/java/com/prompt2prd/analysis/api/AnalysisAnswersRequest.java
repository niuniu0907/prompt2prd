package com.prompt2prd.analysis.api;

import com.prompt2prd.analysis.domain.RequirementState;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AnalysisAnswersRequest(
        @NotNull @Valid RequirementState state,
        @NotEmpty List<@Valid AnswerTurn> answers,
        List<@NotBlank String> missingInformation,
        @NotNull @Valid AnalysisModelSettings modelSettings) {
    public AnalysisAnswersRequest {
        answers = answers == null ? null : List.copyOf(answers);
        missingInformation = missingInformation == null ? List.of() : List.copyOf(missingInformation);
    }

    public record AnswerTurn(
            @NotNull UUID questionId,
            @NotNull UUID batchId,
            @NotBlank String question,
            @NotBlank String answer,
            @NotNull Instant answeredAt) {
    }
}
