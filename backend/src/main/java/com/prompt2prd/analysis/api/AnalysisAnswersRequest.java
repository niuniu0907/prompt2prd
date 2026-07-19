package com.prompt2prd.analysis.api;

import com.prompt2prd.analysis.domain.RequirementState;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AnalysisAnswersRequest(
        @NotNull @Valid RequirementState state,
        List<@Valid AnswerTurn> answers,
        String originalInput,
        String supplementalInput,
        List<@NotBlank String> missingInformation,
        @NotNull @Valid AnalysisModelSettings modelSettings) {
    public AnalysisAnswersRequest {
        answers = answers == null ? List.of() : List.copyOf(answers);
        originalInput = normalizeNullable(originalInput);
        supplementalInput = normalizeNullable(supplementalInput);
        missingInformation = missingInformation == null ? List.of() : List.copyOf(missingInformation);
    }

    @AssertTrue(message = "answers or supplementalInput must be provided")
    public boolean hasClarificationInput() {
        return !answers.isEmpty() || (supplementalInput != null && !supplementalInput.isBlank());
    }

    private static String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public record AnswerTurn(
            @NotNull UUID questionId,
            @NotNull UUID batchId,
            @NotBlank String question,
            @NotBlank String answer,
            @NotNull Instant answeredAt) {
    }
}
