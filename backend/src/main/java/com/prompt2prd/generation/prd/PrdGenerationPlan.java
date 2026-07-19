package com.prompt2prd.generation.prd;

import com.prompt2prd.analysis.domain.RequirementItem;

import java.util.List;

public record PrdGenerationPlan(
        Mode mode,
        List<SectionPlan> sections,
        List<String> missingItems,
        List<RequirementItem> confirmedRequirements,
        RequirementItem selectedArchitecture) {

    public PrdGenerationPlan {
        java.util.Objects.requireNonNull(mode, "mode");
        sections = List.copyOf(sections);
        missingItems = List.copyOf(missingItems);
        confirmedRequirements = List.copyOf(confirmedRequirements);
    }

    public enum Mode { DRAFT, FINAL }

    public record SectionPlan(PrdDefinition.Section definition, String prompt) {
        public SectionPlan {
            java.util.Objects.requireNonNull(definition, "definition");
            if (prompt == null || prompt.isBlank()) throw new IllegalArgumentException("prompt must not be blank");
            prompt = prompt.trim();
        }
    }
}
