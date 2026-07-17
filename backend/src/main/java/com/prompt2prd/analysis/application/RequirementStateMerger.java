package com.prompt2prd.analysis.application;

import com.prompt2prd.analysis.domain.CompletenessCalculator;
import com.prompt2prd.analysis.domain.CompletenessInput;
import com.prompt2prd.analysis.domain.CompletenessScore;
import com.prompt2prd.analysis.domain.ConflictStatus;
import com.prompt2prd.analysis.domain.ProjectSummary;
import com.prompt2prd.analysis.domain.RequirementConflict;
import com.prompt2prd.analysis.domain.RequirementDimension;
import com.prompt2prd.analysis.domain.RequirementItem;
import com.prompt2prd.analysis.domain.RequirementSourceType;
import com.prompt2prd.analysis.domain.RequirementState;

import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/** Copy-on-write, deterministic merge of already validated candidate patches. */
public final class RequirementStateMerger {

    private final CompletenessCalculator completenessCalculator;
    private final Clock clock;
    private final Supplier<UUID> idGenerator;

    public RequirementStateMerger() {
        this(new CompletenessCalculator(), Clock.systemUTC(), UUID::randomUUID);
    }

    RequirementStateMerger(
            CompletenessCalculator completenessCalculator,
            Clock clock,
            Supplier<UUID> idGenerator) {
        this.completenessCalculator = Objects.requireNonNull(
                completenessCalculator, "completenessCalculator");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator");
    }

    public MergeResult merge(
            RequirementState current,
            List<ValidatedRequirementPatch> patches) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(patches, "patches");
        validateAll(current, patches);

        List<RequirementItem> requirements = new ArrayList<>(current.requirements());
        List<RequirementConflict> conflicts = new ArrayList<>(current.conflicts());
        List<RequirementItem> applied = new ArrayList<>();
        List<RequirementConflict> createdConflicts = new ArrayList<>();

        for (ValidatedRequirementPatch patch : patches) {
            RequirementItem candidate = patch.candidate();
            Optional<Integer> exactIndex = exactDuplicateIndex(requirements, candidate);
            if (exactIndex.isPresent()) {
                RequirementItem existing = requirements.get(exactIndex.get());
                if (sourceRank(candidate.sourceType()) > sourceRank(existing.sourceType())) {
                    RequirementItem replacement = replace(existing, candidate);
                    requirements.set(exactIndex.get(), replacement);
                    applied.add(replacement);
                }
                continue;
            }

            Optional<Integer> targetIndex = targetIndex(requirements, patch);
            if (targetIndex.isEmpty()) {
                requirements.add(candidate);
                applied.add(candidate);
                continue;
            }

            int index = targetIndex.get();
            RequirementItem existing = requirements.get(index);
            if (sourceRank(candidate.sourceType()) > sourceRank(existing.sourceType())) {
                RequirementItem replacement = replace(existing, candidate);
                requirements.set(index, replacement);
                applied.add(replacement);
                continue;
            }
            if (patch.targetRequirementId() != null
                    && !existing.locked()
                    && isAiSource(existing.sourceType())
                    && isAiSource(candidate.sourceType())) {
                RequirementItem replacement = replace(existing, candidate);
                requirements.set(index, replacement);
                applied.add(replacement);
                continue;
            }

            RequirementConflict conflict = createConflict(existing, candidate);
            conflicts.add(conflict);
            createdConflicts.add(conflict);
        }

        CompletenessScore completeness = completenessCalculator.calculate(new CompletenessInput(
                requirements, current.questions(), conflicts, java.util.Set.of(), java.util.Map.of()));
        ProjectSummary project = current.project().withCompleteness(completeness.total());
        RequirementState merged = new RequirementState(
                project, requirements, current.questions(), current.answers(), conflicts, completeness);
        return new MergeResult(merged, applied, createdConflicts);
    }

    private void validateAll(
            RequirementState current,
            List<ValidatedRequirementPatch> patches) {
        for (ValidatedRequirementPatch patch : patches) {
            if (patch == null) {
                throw new IllegalArgumentException("patch must not be null");
            }
            if (!current.project().id().equals(patch.candidate().projectId())) {
                throw new IllegalArgumentException("Cross-project patch is not allowed");
            }
            if (patch.targetRequirementId() != null && current.requirements().stream()
                    .noneMatch(item -> item.id().equals(patch.targetRequirementId()))) {
                throw new IllegalArgumentException("Target requirement does not exist");
            }
        }
    }

    private Optional<Integer> exactDuplicateIndex(
            List<RequirementItem> requirements,
            RequirementItem candidate) {
        for (int index = 0; index < requirements.size(); index++) {
            RequirementItem existing = requirements.get(index);
            if (existing.type() == candidate.type()
                    && normalize(existing.content()).equals(normalize(candidate.content()))) {
                return Optional.of(index);
            }
        }
        return Optional.empty();
    }

    private Optional<Integer> targetIndex(
            List<RequirementItem> requirements,
            ValidatedRequirementPatch patch) {
        for (int index = 0; index < requirements.size(); index++) {
            RequirementItem existing = requirements.get(index);
            if (patch.targetRequirementId() != null
                    && existing.id().equals(patch.targetRequirementId())) {
                return Optional.of(index);
            }
        }
        RequirementItem candidate = patch.candidate();
        for (int index = 0; index < requirements.size(); index++) {
            RequirementItem existing = requirements.get(index);
            if (existing.type() == candidate.type()
                    && normalize(existing.title()).equals(normalize(candidate.title()))) {
                return Optional.of(index);
            }
        }
        return Optional.empty();
    }

    private RequirementItem replace(RequirementItem existing, RequirementItem candidate) {
        return new RequirementItem(
                existing.id(), existing.projectId(), candidate.type(), candidate.title(),
                candidate.content(), candidate.status(), candidate.sourceType(),
                candidate.sourceId(), false, candidate.metadata(), existing.createdAt(),
                candidate.updatedAt());
    }

    private RequirementConflict createConflict(
            RequirementItem existing,
            RequirementItem candidate) {
        RequirementDimension dimension = completenessCalculator.dimensionOf(existing)
                .or(() -> completenessCalculator.dimensionOf(candidate))
                .orElse(RequirementDimension.FEATURES);
        boolean core = dimension == RequirementDimension.PRODUCT_SCOPE
                || dimension == RequirementDimension.ROLES_PERMISSIONS
                || dimension == RequirementDimension.CORE_FLOW
                || dimension == RequirementDimension.BUSINESS_RULES;
        Instant now = clock.instant();
        return new RequirementConflict(
                idGenerator.get(), existing.projectId(), existing.id(), candidate.id(),
                existing.content(), candidate.content(), dimension,
                "Conflicting candidate requires explicit user resolution", core,
                ConflictStatus.OPEN, null, now, now, null);
    }

    private boolean isAiSource(RequirementSourceType sourceType) {
        return sourceType == RequirementSourceType.AI_INFERENCE
                || sourceType == RequirementSourceType.AI_RECOMMENDATION;
    }

    private int sourceRank(RequirementSourceType sourceType) {
        return switch (sourceType) {
            case AI_INFERENCE -> 10;
            case AI_RECOMMENDATION -> 20;
            case INITIAL_INPUT, UPLOADED_FILE -> 30;
            case USER_ANSWER -> 40;
            case USER_EDIT -> 50;
            case VERSION_RESTORE -> 60;
        };
    }

    private String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]", "");
    }

    public record MergeResult(
            RequirementState state,
            List<RequirementItem> appliedRequirements,
            List<RequirementConflict> createdConflicts) {

        public MergeResult {
            appliedRequirements = List.copyOf(appliedRequirements);
            createdConflicts = List.copyOf(createdConflicts);
        }
    }
}
