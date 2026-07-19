package com.prompt2prd.analysis.api;

import com.prompt2prd.analysis.domain.RequirementState;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record AnalysisRequest(
        @NotNull @Valid RequirementState state,
        @NotBlank String input,
        List<@NotBlank String> missingInformation,
        @NotNull @Valid AnalysisModelSettings modelSettings) {
    public AnalysisRequest {
        missingInformation = missingInformation == null ? List.of() : List.copyOf(missingInformation);
    }
}
