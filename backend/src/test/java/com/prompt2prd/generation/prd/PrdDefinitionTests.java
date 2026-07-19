package com.prompt2prd.generation.prd;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PrdDefinitionTests {

    @Test
    void definesTwelveOrderedUniquePrdSections() {
        assertThat(PrdDefinition.sections()).hasSize(12);
        assertThat(PrdDefinition.sections()).extracting(PrdDefinition.Section::order)
                .containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12);
        assertThat(PrdDefinition.sections()).extracting(section -> section.key().wireName())
                .doesNotHaveDuplicates()
                .contains("product-background-goals", "product-scope", "acceptance-criteria", "risks-assumptions-open-items")
                .doesNotContain("architecture", "apis");
        assertThat(PrdDefinition.requireSection("pages").title())
                .isEqualTo("页面清单与页面状态");
        assertThat(PrdDefinition.sections()).extracting(PrdDefinition.Section::title)
                .anyMatch(title -> title.contains("目标"))
                .anyMatch(title -> title.contains("用户故事"))
                .anyMatch(title -> title.contains("业务规则"))
                .anyMatch(title -> title.contains("非功能需求"))
                .anyMatch(title -> title.contains("待确认事项"));
    }

    @Test
    void createsStableIdsForEveryTraceableArtifact() {
        assertThat(PrdDefinition.stableId(PrdDefinition.ArtifactType.REQUIREMENT, 1)).isEqualTo("REQ-001");
        assertThat(PrdDefinition.stableId(PrdDefinition.ArtifactType.USER_STORY, 1)).isEqualTo("US-001");
        assertThat(PrdDefinition.stableId(PrdDefinition.ArtifactType.BUSINESS_RULE, 8)).isEqualTo("BR-008");
        assertThat(PrdDefinition.stableId(PrdDefinition.ArtifactType.API, 12)).isEqualTo("API-012");
        assertThat(PrdDefinition.stableId(PrdDefinition.ArtifactType.PAGE, 2)).isEqualTo("PAGE-002");
        assertThat(PrdDefinition.stableId(PrdDefinition.ArtifactType.ACCEPTANCE, 37)).isEqualTo("AC-037");
        assertThat(PrdDefinition.stableId(PrdDefinition.ArtifactType.IMPLEMENTATION_PHASE, 4)).isEqualTo("PHASE-004");
        assertThat(PrdDefinition.DirectiveLevel.values())
                .containsExactly(PrdDefinition.DirectiveLevel.MUST,
                        PrdDefinition.DirectiveLevel.SHOULD, PrdDefinition.DirectiveLevel.MUST_NOT);
    }

    @Test
    void requiresEveryCoreFeatureToLinkToStoryRulesAndAcceptance() {
        PrdDefinition.TraceabilityReport valid = PrdDefinition.validateTraceability(List.of(
                new PrdDefinition.TraceLink("REQ-001", "US-001", List.of("BR-001"), List.of("AC-001"))));
        PrdDefinition.TraceabilityReport invalid = PrdDefinition.validateTraceability(List.of(
                new PrdDefinition.TraceLink("REQ-001", "US-001", List.of(), List.of("AC-001")),
                new PrdDefinition.TraceLink("REQ-001", null, List.of("wrong"), List.of())));

        assertThat(valid.valid()).isTrue();
        assertThat(invalid.valid()).isFalse();
        assertThat(invalid.errors()).anyMatch(error -> error.contains("business rules"))
                .anyMatch(error -> error.contains("Duplicate feature ID"))
                .anyMatch(error -> error.contains("user story"));
    }

    @Test
    void rejectsUnknownSectionAndInvalidSequence() {
        assertThatThrownBy(() -> PrdDefinition.requireSection("unknown")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PrdDefinition.stableId(PrdDefinition.ArtifactType.API, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
