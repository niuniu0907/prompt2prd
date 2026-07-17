package com.prompt2prd.analysis.domain;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CompletenessDimensionScore(
        @NotNull RequirementDimension dimension,
        boolean applicable,
        @Min(0) @Max(100) int score,
        @NotNull List<String> reasons) {

    public CompletenessDimensionScore {
        if (score < 0 || score > 100) {
            throw new IllegalArgumentException("dimension score must be between 0 and 100");
        }
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }
}
