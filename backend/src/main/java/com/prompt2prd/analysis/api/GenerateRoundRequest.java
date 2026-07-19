package com.prompt2prd.analysis.api;

import com.prompt2prd.analysis.domain.RequirementState;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record GenerateRoundRequest(
        @NotNull @Valid RequirementState state,
        @Min(1) int targetRoundNo,
        List<String> coveredAreas,
        List<@Valid QuestionSummary> currentVisibleQuestions,
        List<String> missingInformation,
        @NotNull @Valid AnalysisModelSettings modelSettings) {

    public GenerateRoundRequest {
        coveredAreas = coveredAreas == null ? List.of() : List.copyOf(coveredAreas);
        currentVisibleQuestions = currentVisibleQuestions == null
                ? List.of() : List.copyOf(currentVisibleQuestions);
        missingInformation = missingInformation == null ? List.of() : List.copyOf(missingInformation);
    }

    /** Minimal question info for overlap avoidance — no UUIDs or timestamps. */
    public record QuestionSummary(
            @jakarta.validation.constraints.NotBlank String text,
            @jakarta.validation.constraints.NotBlank String targetField,
            @jakarta.validation.constraints.NotBlank String semanticKey) {
    }
}
