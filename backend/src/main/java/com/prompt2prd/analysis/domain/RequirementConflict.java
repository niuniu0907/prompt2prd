package com.prompt2prd.analysis.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record RequirementConflict(
        @NotNull UUID id,
        @NotNull UUID projectId,
        UUID leftRequirementId,
        UUID rightRequirementId,
        @NotBlank String leftContent,
        @NotBlank String rightContent,
        @NotNull RequirementDimension dimension,
        @NotBlank String impact,
        boolean core,
        @NotNull ConflictStatus status,
        String resolution,
        @NotNull Instant createdAt,
        @NotNull Instant updatedAt,
        Instant resolvedAt) {

    public RequirementConflict {
        leftContent = trim(leftContent);
        rightContent = trim(rightContent);
        impact = trim(impact);
        resolution = resolution == null || resolution.isBlank() ? null : resolution.trim();
        if (status == ConflictStatus.OPEN && resolvedAt != null) {
            throw new IllegalArgumentException("Open conflict cannot have resolvedAt");
        }
        if (status == ConflictStatus.RESOLVED && (resolution == null || resolvedAt == null)) {
            throw new IllegalArgumentException("Resolved conflict requires resolution and resolvedAt");
        }
        if (createdAt != null && updatedAt != null && updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
