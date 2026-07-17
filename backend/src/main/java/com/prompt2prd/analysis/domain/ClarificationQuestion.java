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
        @NotBlank String text,
        @NotBlank String reason,
        @NotNull RequirementDimension dimension,
        @NotBlank String targetField,
        @NotBlank String semanticKey,
        @NotNull QuestionInputType inputType,
        @NotNull List<@Valid ClarificationOption> options,
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
        if (!Double.isFinite(priority) || priority < 0 || priority > 5) {
            throw new IllegalArgumentException("priority must be between 0 and 5");
        }
        if (createdAt != null && updatedAt != null && updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
    }

    public ClarificationQuestion withPriority(double value) {
        return new ClarificationQuestion(id, projectId, batchId, text, reason, dimension,
                targetField, semanticKey, inputType, options, value, status, createdAt, updatedAt);
    }

    public ClarificationQuestion withStatus(QuestionStatus value, Instant now) {
        return new ClarificationQuestion(id, projectId, batchId, text, reason, dimension,
                targetField, semanticKey, inputType, options, priority, value, createdAt, now);
    }
}
