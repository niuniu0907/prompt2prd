package com.prompt2prd.generation.prd;

import com.prompt2prd.analysis.domain.RequirementItem;
import com.prompt2prd.analysis.domain.RequirementSourceType;
import com.prompt2prd.analysis.domain.RequirementStatus;
import com.prompt2prd.analysis.domain.RequirementType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PrdChangeAnalyzerTests {

    private static final UUID PROJECT_ID = UUID.fromString("123e4567-e89b-42d3-a456-426614174000");
    private static final Instant NOW = Instant.parse("2026-07-17T00:00:00Z");

    @Test
    void noChangeWhenContentIdentical() {
        var report = PrdChangeAnalyzer.analyze(
                "business-rules", "退款时限为 24 小时。", "退款时限为 24 小时。",
                List.of(refundRule(false)));

        assertThat(report.hasChanges()).isFalse();
        assertThat(report.syncedChanges()).isEmpty();
    }

    @Test
    void syncedWhenUniqueMatchFound() {
        var report = PrdChangeAnalyzer.analyze(
                "business-rules",
                "退款时限为 24 小时。",
                "退款时限为 48 小时。",
                List.of(refundRule(false)));

        assertThat(report.syncedChanges()).hasSize(1);
        assertThat(report.syncedChanges().getFirst().oldValue()).contains("24");
        assertThat(report.syncedChanges().getFirst().newValue()).contains("48");
    }

    @Test
    void conflictWhenTargetIsLocked() {
        var report = PrdChangeAnalyzer.analyze(
                "business-rules",
                "退款时限为 24 小时。",
                "退款时限为 48 小时。",
                List.of(refundRule(true)));

        assertThat(report.syncedChanges()).isEmpty();
        assertThat(report.conflictWarnings()).hasSize(1);
        assertThat(report.conflictWarnings().getFirst().reason()).contains("锁定");
    }

    @Test
    void pendingWhenNoMatchFound() {
        var report = PrdChangeAnalyzer.analyze(
                "business-rules",
                "- 旧规则：登录尝试次数上限为 3 次",
                "- 新规则：登录尝试次数上限为 5 次",
                List.of(refundRule(false)));

        assertThat(report.syncedChanges()).isEmpty();
        assertThat(report.pendingChanges()).isNotEmpty();
    }

    @Test
    void pendingWhenMultipleMatches() {
        RequirementItem ruleA = item("退款规则", "用户可在 24 小时内申请退款", RequirementStatus.CONFIRMED, false);
        RequirementItem ruleB = item("退款补充", "退款申请需在 24 小时内提交", RequirementStatus.CONFIRMED, false);
        var report = PrdChangeAnalyzer.analyze(
                "business-rules",
                "退款时限为 24 小时。",
                "退款时限为 48 小时。",
                List.of(ruleA, ruleB));

        assertThat(report.syncedChanges()).isEmpty();
        assertThat(report.pendingChanges()).isNotEmpty();
    }

    private static RequirementItem refundRule(boolean locked) {
        return item("退款规则", "退款时限为 24 小时", RequirementStatus.CONFIRMED, locked);
    }

    private static RequirementItem item(String title, String content, RequirementStatus status, boolean locked) {
        return new RequirementItem(UUID.randomUUID(), PROJECT_ID, RequirementType.BUSINESS_RULE,
                title, content, status, RequirementSourceType.USER_ANSWER, null, locked, Map.of(), NOW, NOW);
    }
}
