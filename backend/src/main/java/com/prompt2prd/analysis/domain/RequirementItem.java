package com.prompt2prd.analysis.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record RequirementItem(
        @NotNull UUID id,
        @NotNull UUID projectId,
        @NotNull RequirementType type,
        @NotBlank String title,
        @NotBlank String content,
        @NotNull RequirementStatus status,
        @NotNull RequirementSourceType sourceType,
        UUID sourceId,
        boolean locked,
        @NotNull Map<String, Object> metadata,
        @NotNull Instant createdAt,
        @NotNull Instant updatedAt) {

    public RequirementItem {
        if (locked && status != RequirementStatus.CONFIRMED) {
            throw new IllegalArgumentException("Only CONFIRMED requirements may be locked");
        }
        if (title != null) {
            title = title.trim();
        }
        if (content != null) {
            content = content.trim();
        }
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        if (createdAt != null && updatedAt != null && updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
    }

    public RequirementItem withStatus(RequirementStatus newStatus, Instant now) {
        return new RequirementItem(id, projectId, type, title, content, newStatus,
                sourceType, sourceId, newStatus == RequirementStatus.CONFIRMED && locked,
                metadata, createdAt, now);
    }
}
