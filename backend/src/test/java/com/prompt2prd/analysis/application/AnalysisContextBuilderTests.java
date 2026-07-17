package com.prompt2prd.analysis.application;

import com.prompt2prd.analysis.domain.CompletenessScore;
import com.prompt2prd.analysis.domain.ProjectStage;
import com.prompt2prd.analysis.domain.ProjectSummary;
import com.prompt2prd.analysis.domain.RequirementItem;
import com.prompt2prd.analysis.domain.RequirementSourceType;
import com.prompt2prd.analysis.domain.RequirementState;
import com.prompt2prd.analysis.domain.RequirementStatus;
import com.prompt2prd.analysis.domain.RequirementType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AnalysisContextBuilderTests {

    private static final Instant NOW = Instant.parse("2026-07-17T09:00:00Z");
    private final AnalysisContextBuilder builder = new AnalysisContextBuilder();

    @Test
    void keepsOnlyTheMostRecentAnswerBatchInsteadOfTheFullConversation() {
        UUID oldBatch = UUID.randomUUID();
        UUID latestBatch = UUID.randomUUID();
        List<AnalysisContextBuilder.QuestionAnswerTurn> history = new ArrayList<>();
        for (int index = 0; index < 100; index++) {
            history.add(turn(oldBatch, "旧问题-" + index, "旧回答-" + index, NOW.minusSeconds(200 - index)));
        }
        history.add(turn(latestBatch, "最新问题一", "最新回答一", NOW));
        history.add(turn(latestBatch, "最新问题二", "最新回答二", NOW.plusSeconds(1)));

        AnalysisContextBuilder.AnalysisContext context = builder.build(
                state(), history, List.of("还缺少异常流程"), "{\"type\":\"object\"}");

        assertThat(context.recentAnswers()).hasSize(2)
                .extracting(AnalysisContextBuilder.QuestionAnswerTurn::answer)
                .containsExactly("最新回答一", "最新回答二");
        assertThat(context.toString()).doesNotContain("旧回答-99");
    }

    @Test
    void includesCurrentRequirementsAndSeparatesLockedRequirements() {
        AnalysisContextBuilder.AnalysisContext context = builder.build(
                state(), List.of(), List.of(), "schema");

        assertThat(context.currentRequirements()).hasSize(2);
        assertThat(context.lockedRequirements()).hasSize(1)
                .allMatch(RequirementItem::locked);
    }

    @Test
    void preservesProjectLanguageAndCopiesGapsAndSchema() {
        List<String> gaps = new ArrayList<>(List.of("  缺少验收标准  ", "", "   "));

        AnalysisContextBuilder.AnalysisContext context = builder.build(
                state(), List.of(), gaps, "  strict-schema  ");
        gaps.set(0, "被外部修改");

        assertThat(context.language()).isEqualTo("zh-CN");
        assertThat(context.missingInformation()).containsExactly("缺少验收标准");
        assertThat(context.outputSchema()).isEqualTo("strict-schema");
    }

    private RequirementState state() {
        UUID projectId = UUID.randomUUID();
        ProjectSummary project = new ProjectSummary(
                projectId, "需求分析器", "zh-CN", ProjectStage.CLARIFYING, 50);
        RequirementItem locked = requirement(projectId, "产品目标", true);
        RequirementItem current = requirement(projectId, "导出 PRD", false);
        return new RequirementState(
                project,
                List.of(locked, current),
                List.of(),
                List.of(),
                List.of(),
                new CompletenessScore(50, List.of(), 1, false)
        );
    }

    private RequirementItem requirement(UUID projectId, String title, boolean locked) {
        return new RequirementItem(
                UUID.randomUUID(),
                projectId,
                RequirementType.FEATURE,
                title,
                title + "的详细内容",
                RequirementStatus.CONFIRMED,
                RequirementSourceType.USER_EDIT,
                null,
                locked,
                Map.of(),
                NOW,
                NOW
        );
    }

    private AnalysisContextBuilder.QuestionAnswerTurn turn(
            UUID batchId,
            String question,
            String answer,
            Instant answeredAt) {
        return new AnalysisContextBuilder.QuestionAnswerTurn(
                UUID.randomUUID(), batchId, question, answer, answeredAt);
    }
}
