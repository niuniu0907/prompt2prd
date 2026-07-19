package com.prompt2prd.analysis.domain;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ClarificationAnswer(
        @NotNull UUID id,
        @NotNull UUID projectId,
        @NotNull UUID questionId,
        @NotNull List<@NotNull UUID> selectedOptionIds,
        String customAnswer,
        String note,
        boolean skipped,
        @NotNull Instant createdAt,
        @NotNull Instant updatedAt) {

    public ClarificationAnswer {
        selectedOptionIds = selectedOptionIds == null ? List.of() : List.copyOf(selectedOptionIds);
        customAnswer = normalizeNullable(customAnswer);
        note = normalizeNullable(note);
        if (createdAt != null && updatedAt != null && updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
    }

    private static String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
