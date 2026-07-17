package com.prompt2prd.analysis.application;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** Raw structured model contract. String enums are converted only after validation. */
public record AnalysisModelOutput(
        @NotBlank String suggestedProjectName,
        @NotNull List<@Valid RequirementCandidate> requirements,
        @NotNull List<@Valid QuestionCandidate> questions,
        @NotNull List<@NotBlank String> missingInformation) {

    public AnalysisModelOutput {
        requirements = requirements == null ? null : List.copyOf(requirements);
        questions = questions == null ? null : List.copyOf(questions);
        missingInformation = missingInformation == null ? null : List.copyOf(missingInformation);
    }

    public record RequirementCandidate(
            String targetRequirementId,
            @NotBlank String type,
            @NotBlank String title,
            @NotBlank String content,
            @NotBlank String status) {
    }

    public record QuestionCandidate(
            @NotBlank String text,
            @NotBlank String reason,
            @NotBlank String dimension,
            @NotBlank String targetField,
            @NotBlank String semanticKey,
            @NotBlank String inputType,
            @NotNull List<@Valid OptionCandidate> options,
            @Min(1) @Max(5) int businessImpact,
            @Min(1) @Max(5) int informationGap,
            @Min(1) @Max(5) int dependencyCount,
            @Min(1) @Max(5) int risk) {

        public QuestionCandidate {
            options = options == null ? null : List.copyOf(options);
        }
    }

    public record OptionCandidate(
            @NotBlank String label,
            @NotBlank String impact,
            boolean recommended) {
    }
}
