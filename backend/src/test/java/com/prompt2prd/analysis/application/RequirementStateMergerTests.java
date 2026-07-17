package com.prompt2prd.analysis.application;

import com.prompt2prd.analysis.domain.CompletenessCalculator;
import com.prompt2prd.analysis.domain.CompletenessScore;
import com.prompt2prd.analysis.domain.ProjectStage;
import com.prompt2prd.analysis.domain.ProjectSummary;
import com.prompt2prd.analysis.domain.RequirementConflict;
import com.prompt2prd.analysis.domain.RequirementItem;
import com.prompt2prd.analysis.domain.RequirementSourceType;
import com.prompt2prd.analysis.domain.RequirementState;
import com.prompt2prd.analysis.domain.RequirementStatus;
import com.prompt2prd.analysis.domain.RequirementType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequirementStateMergerTests {

    private static final UUID PROJECT_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final Instant NOW = Instant.parse("2026-07-17T12:00:00Z");
    private final RequirementStateMerger merger = new RequirementStateMerger(
            new CompletenessCalculator(),
            Clock.fixed(NOW, ZoneOffset.UTC),
            () -> UUID.fromString("99999999-9999-4999-8999-999999999999"));

    @Test
    void addsANewValidatedFactAndRecalculatesCompleteness() {
        RequirementItem candidate = item(
                RequirementType.PRODUCT_GOAL, "目标", "提供可信寄养服务",
                RequirementStatus.CONFIRMED, RequirementSourceType.USER_ANSWER, false);

        RequirementStateMerger.MergeResult result = merger.merge(
                emptyState(), List.of(ValidatedRequirementPatch.of(null, candidate)));

        assertThat(result.state().requirements()).containsExactly(candidate);
        assertThat(result.appliedRequirements()).containsExactly(candidate);
        assertThat(result.state().project().completeness()).isEqualTo(10);
        assertThat(result.state().completeness().total()).isEqualTo(10);
    }

    @Test
    void suppressesNormalizedDuplicateFacts() {
        RequirementItem existing = item(
                RequirementType.FEATURE, "在线支付", " 支持 在线支付。 ",
                RequirementStatus.CONFIRMED, RequirementSourceType.USER_ANSWER, false);
        RequirementItem duplicate = item(
                RequirementType.FEATURE, "支付", "支持在线支付",
                RequirementStatus.INFERRED, RequirementSourceType.AI_INFERENCE, false);

        RequirementStateMerger.MergeResult result = merger.merge(
                state(existing), List.of(ValidatedRequirementPatch.of(null, duplicate)));

        assertThat(result.state().requirements()).containsExactly(existing);
        assertThat(result.appliedRequirements()).isEmpty();
        assertThat(result.createdConflicts()).isEmpty();
    }

    @Test
    void userAnswerOverridesAnEarlierInference() {
        RequirementItem inferred = item(
                RequirementType.BUSINESS_RULE, "退款时限", "退款时限为24小时",
                RequirementStatus.INFERRED, RequirementSourceType.AI_INFERENCE, false);
        RequirementItem userAnswer = item(
                RequirementType.BUSINESS_RULE, "退款时限", "退款时限为48小时",
                RequirementStatus.CONFIRMED, RequirementSourceType.USER_ANSWER, false);

        RequirementStateMerger.MergeResult result = merger.merge(
                state(inferred), List.of(ValidatedRequirementPatch.of(inferred.id(), userAnswer)));

        assertThat(result.state().requirements()).singleElement().satisfies(requirement -> {
            assertThat(requirement.content()).isEqualTo("退款时限为48小时");
            assertThat(requirement.status()).isEqualTo(RequirementStatus.CONFIRMED);
            assertThat(requirement.sourceType()).isEqualTo(RequirementSourceType.USER_ANSWER);
        });
        assertThat(result.createdConflicts()).isEmpty();
    }

    @Test
    void modelCannotOverwriteLockedContentAndCreatesAConflict() {
        RequirementItem locked = item(
                RequirementType.ROLE, "寄养方", "仅宠物店可以成为寄养方",
                RequirementStatus.CONFIRMED, RequirementSourceType.USER_ANSWER, true);
        RequirementItem candidate = item(
                RequirementType.ROLE, "寄养方", "个人也可以成为寄养方",
                RequirementStatus.INFERRED, RequirementSourceType.AI_INFERENCE, false);

        RequirementStateMerger.MergeResult result = merger.merge(
                state(locked), List.of(ValidatedRequirementPatch.of(locked.id(), candidate)));

        assertThat(result.state().requirements()).containsExactly(locked);
        assertThat(result.createdConflicts()).singleElement().satisfies(conflict -> {
            assertThat(conflict.leftRequirementId()).isEqualTo(locked.id());
            assertThat(conflict.rightContent()).isEqualTo(candidate.content());
            assertThat(conflict.core()).isTrue();
        });
    }

    @Test
    void contradictorySameTargetFactsCreateAConflictInsteadOfSilentChoice() {
        RequirementItem existing = item(
                RequirementType.EXCEPTION_SCENARIO, "支付失败", "订单保持待支付",
                RequirementStatus.CONFIRMED, RequirementSourceType.USER_ANSWER, false);
        RequirementItem contradictory = item(
                RequirementType.EXCEPTION_SCENARIO, "支付失败", "订单立即取消",
                RequirementStatus.INFERRED, RequirementSourceType.AI_INFERENCE, false);

        RequirementStateMerger.MergeResult result = merger.merge(
                state(existing), List.of(ValidatedRequirementPatch.of(null, contradictory)));

        assertThat(result.state().requirements()).containsExactly(existing);
        assertThat(result.createdConflicts()).hasSize(1);
    }

    @Test
    void mergerHasNoPersistenceOrRepositoryDependency() {
        assertThat(RequirementStateMerger.class.getDeclaredFields())
                .extracting(Field::getType)
                .allSatisfy(type -> assertThat(type.getName())
                        .doesNotContain("repository")
                        .doesNotContain("db"));
    }

    @Test
    void invalidPatchLeavesTheImmutableInputStateUnchanged() {
        RequirementItem existing = item(
                RequirementType.FEATURE, "预约", "支持预约",
                RequirementStatus.CONFIRMED, RequirementSourceType.USER_ANSWER, false);
        RequirementState current = state(existing);
        RequirementItem otherProject = new RequirementItem(
                UUID.randomUUID(), UUID.randomUUID(), RequirementType.FEATURE,
                "恶意补丁", "跨项目内容", RequirementStatus.INFERRED,
                RequirementSourceType.AI_INFERENCE, null, false, Map.of(), NOW, NOW);

        assertThatThrownBy(() -> merger.merge(
                current, List.of(ValidatedRequirementPatch.of(null, otherProject))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(current).isEqualTo(state(existing));
    }

    private RequirementState emptyState() {
        return new RequirementState(
                new ProjectSummary(PROJECT_ID, "项目", "zh-CN", ProjectStage.CLARIFYING, 0),
                List.of(), List.of(), List.of(), List.of(),
                new CompletenessScore(0, List.of(), 0, false));
    }

    private RequirementState state(RequirementItem... requirements) {
        RequirementState empty = emptyState();
        return new RequirementState(empty.project(), List.of(requirements), List.of(),
                List.of(), List.<RequirementConflict>of(), empty.completeness());
    }

    private RequirementItem item(
            RequirementType type,
            String title,
            String content,
            RequirementStatus status,
            RequirementSourceType source,
            boolean locked) {
        return new RequirementItem(
                UUID.randomUUID(), PROJECT_ID, type, title, content, status,
                source, null, locked, Map.of(), NOW, NOW);
    }
}
