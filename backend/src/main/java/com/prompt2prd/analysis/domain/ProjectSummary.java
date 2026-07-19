package com.prompt2prd.analysis.domain;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ProjectSummary(
        @NotNull UUID id,
        @NotBlank String name,
        @NotBlank String language,
        @NotNull ProjectStage stage,
        @Min(0) @Max(100) int completeness) {

    public ProjectSummary {
        if (name != null) {
            name = name.trim();
        }
        if (language != null) {
            language = language.trim();
        }
        if (completeness < 0 || completeness > 100) {
            throw new IllegalArgumentException("completeness must be between 0 and 100");
        }
    }

    public ProjectSummary withCompleteness(int value) {
        return new ProjectSummary(id, name, language, stage, value);
    }
}
