package com.prompt2prd.generation.prd;

import com.prompt2prd.analysis.domain.CompletenessScore;
import com.prompt2prd.analysis.domain.ConflictStatus;
import com.prompt2prd.analysis.domain.ProjectStage;
import com.prompt2prd.analysis.domain.ProjectSummary;
import com.prompt2prd.analysis.domain.RequirementConflict;
import com.prompt2prd.analysis.domain.RequirementDimension;
import com.prompt2prd.analysis.domain.RequirementItem;
import com.prompt2prd.analysis.domain.RequirementSourceType;
import com.prompt2prd.analysis.domain.RequirementState;
import com.prompt2prd.analysis.domain.RequirementStatus;
import com.prompt2prd.analysis.domain.RequirementType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class PrdTestFixtures {
    static final UUID PROJECT_ID = UUID.fromString("123e4567-e89b-42d3-a456-426614174000");
    static final Instant NOW = Instant.parse("2026-07-17T00:00:00Z");

    private PrdTestFixtures() {}

    static RequirementState finalState() {
        return state(90, true, false, List.of());
    }

    static RequirementState state(int completeness, boolean architecture, boolean coreConflict,
                                  List<RequirementItem> extra) {
        List<RequirementItem> items = new ArrayList<>();
        items.add(item(RequirementType.FEATURE, "提交订单", "用户可以提交订单", RequirementStatus.CONFIRMED, Map.of()));
        items.add(item(RequirementType.BUSINESS_RULE, "订单规则", "提交后进入待审核", RequirementStatus.CONFIRMED, Map.of()));
        items.add(item(RequirementType.ACCEPTANCE_CRITERION, "订单验收", "提交成功后显示待审核", RequirementStatus.CONFIRMED, Map.of()));
        if (architecture) {
            items.add(item(RequirementType.TECHNICAL_CONSTRAINT, "主架构", "Vue 3 + Spring Boot 单体",
                    RequirementStatus.CONFIRMED, Map.of("kind", "ARCHITECTURE_CANDIDATE")));
        }
        items.addAll(extra);
        List<RequirementConflict> conflicts = coreConflict ? List.of(new RequirementConflict(
                UUID.randomUUID(), PROJECT_ID, null, null, "24 小时", "48 小时",
                RequirementDimension.BUSINESS_RULES, "退款规则冲突", true, ConflictStatus.OPEN,
                null, NOW, NOW, null)) : List.of();
        return new RequirementState(
                new ProjectSummary(PROJECT_ID, "测试项目", "zh-CN", ProjectStage.PRD, completeness),
                items, List.of(), List.of(), conflicts,
                new CompletenessScore(completeness, List.of(), 0, coreConflict));
    }

    static RequirementItem item(RequirementType type, String title, String content,
                                RequirementStatus status, Map<String, Object> metadata) {
        return new RequirementItem(UUID.randomUUID(), PROJECT_ID, type, title, content, status,
                RequirementSourceType.USER_ANSWER, null, false, metadata, NOW, NOW);
    }
}
