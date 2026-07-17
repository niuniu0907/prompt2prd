package com.prompt2prd.analysis.application;

import com.prompt2prd.analysis.domain.RequirementItem;

import java.util.Objects;
import java.util.UUID;

/** A structurally checked candidate patch; raw model DTOs cannot enter the merger directly. */
public final class ValidatedRequirementPatch {

    private final UUID targetRequirementId;
    private final RequirementItem candidate;

    private ValidatedRequirementPatch(UUID targetRequirementId, RequirementItem candidate) {
        this.targetRequirementId = targetRequirementId;
        this.candidate = candidate;
    }

    public static ValidatedRequirementPatch of(
            UUID targetRequirementId,
            RequirementItem candidate) {
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(candidate.id(), "candidate.id");
        Objects.requireNonNull(candidate.projectId(), "candidate.projectId");
        Objects.requireNonNull(candidate.type(), "candidate.type");
        Objects.requireNonNull(candidate.status(), "candidate.status");
        Objects.requireNonNull(candidate.sourceType(), "candidate.sourceType");
        Objects.requireNonNull(candidate.createdAt(), "candidate.createdAt");
        Objects.requireNonNull(candidate.updatedAt(), "candidate.updatedAt");
        if (candidate.title() == null || candidate.title().isBlank()
                || candidate.content() == null || candidate.content().isBlank()) {
            throw new IllegalArgumentException("Candidate title and content must not be blank");
        }
        return new ValidatedRequirementPatch(targetRequirementId, candidate);
    }

    public UUID targetRequirementId() {
        return targetRequirementId;
    }

    public RequirementItem candidate() {
        return candidate;
    }
}
