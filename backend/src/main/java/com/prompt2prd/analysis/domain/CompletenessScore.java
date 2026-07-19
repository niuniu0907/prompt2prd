package com.prompt2prd.analysis.domain;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CompletenessScore(
        @Min(0) @Max(100) int total,
        @NotNull List<@Valid CompletenessDimensionScore> dimensions,
        @Min(0) int pendingCount,
        boolean hasCoreConflict) {

    public CompletenessScore {
        if (total < 0 || total > 100) {
            throw new IllegalArgumentException("total completeness must be between 0 and 100");
        }
        if (pendingCount < 0) {
            throw new IllegalArgumentException("pendingCount must not be negative");
        }
        dimensions = dimensions == null ? List.of() : List.copyOf(dimensions);
    }
}
