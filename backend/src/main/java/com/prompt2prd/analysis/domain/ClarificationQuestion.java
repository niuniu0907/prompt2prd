package com.prompt2prd.analysis.domain;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ClarificationQuestion(
        @NotNull UUID id,
        @NotNull UUID projectId,
        @NotNull UUID batchId,
        int roundNo,
        @NotBlank String text,
        @NotBlank String reason,
        @NotNull RequirementDimension dimension,
        @NotBlank String targetField,
        @NotBlank String semanticKey,
        @NotNull QuestionInputType inputType,
        @NotNull List<@Valid ClarificationOption> options,
        @NotNull List<@NotBlank String> coverageCategories,
        double priority,
        @NotNull QuestionStatus status,
        @NotNull Instant createdAt,
        @NotNull Instant updatedAt) {

    public ClarificationQuestion {
        if (text != null) {
            text = text.trim();
        }
        if (reason != null) {
            reason = reason.trim();
        }
        if (targetField != null) {
            targetField = targetField.trim();
        }
        if (semanticKey != null) {
            semanticKey = semanticKey.trim();
        }
        options = options == null ? List.of() : List.copyOf(options);
        coverageCategories = coverageCategories == null ? List.of() : List.copyOf(coverageCategories);
        if (!Double.isFinite(priority) || priority < 0 || priority > 5) {
            throw new IllegalArgumentException("priority must be between 0 and 5");
        }
        if (createdAt != null && updatedAt != null && updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
    }

    public ClarificationQuestion withPriority(double value) {
        return new ClarificationQuestion(id, projectId, batchId, roundNo, text, reason, dimension,
                targetField, semanticKey, inputType, options, coverageCategories, value, status, createdAt, updatedAt);
    }

    public ClarificationQuestion withStatus(QuestionStatus value, Instant now) {
        return new ClarificationQuestion(id, projectId, batchId, roundNo, text, reason, dimension,
                targetField, semanticKey, inputType, options, coverageCategories, priority, value, createdAt, now);
    }
}
