package com.prompt2prd.analysis.application;

import com.prompt2prd.analysis.domain.ClarificationQuestion;
import com.prompt2prd.analysis.domain.CompletenessCalculator;
import com.prompt2prd.analysis.domain.CompletenessInput;
import com.prompt2prd.analysis.domain.QuestionInputType;
import com.prompt2prd.analysis.domain.QuestionStatus;
import com.prompt2prd.analysis.domain.RequirementDimension;
import com.prompt2prd.analysis.domain.RequirementSourceType;
import com.prompt2prd.analysis.domain.RequirementStatus;
import com.prompt2prd.analysis.domain.RequirementType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AnswerPolicyTests {

    private static final Instant NOW = Instant.parse("2026-07-17T08:00:00Z");
    private final AnswerPolicy policy = new AnswerPolicy();

    @Test
    void skipsOneQuestionWithoutChangingOtherQuestions() {
        ClarificationQuestion first = question("目标用户是谁？", UUID.randomUUID());
        ClarificationQuestion second = question("核心流程是什么？", first.batchId());

        List<ClarificationQuestion> result = policy.skipOne(List.of(first, second), first.id());

        assertThat(result).extracting(ClarificationQuestion::status)
                .containsExactly(QuestionStatus.SKIPPED, QuestionStatus.PENDING);
        assertThat(scoreQuestion(result.getFirst())).isZero();
    }

    @Test
    void skipsEveryQuestionInTheSelectedBatchOnly() {
        UUID selectedBatch = UUID.randomUUID();
        ClarificationQuestion first = question("目标用户是谁？", selectedBatch);
        ClarificationQuestion second = question("核心流程是什么？", selectedBatch);
        ClarificationQuestion later = question("约束条件是什么？", UUID.randomUUID());

        List<ClarificationQuestion> result = policy.skipBatch(List.of(first, second, later), selectedBatch);

        assertThat(result).extracting(ClarificationQuestion::status)
                .containsExactly(QuestionStatus.SKIPPED, QuestionStatus.SKIPPED, QuestionStatus.PENDING);
        assertThat(result.subList(0, 2)).allMatch(question -> scoreQuestion(question) == 0);
    }

    @Test
    void recommendationStaysPendingUntilUserAcceptsIt() {
        ClarificationQuestion question = question("目标用户是谁？", UUID.randomUUID());

        AnswerPolicy.RecommendationOutcome outcome = policy.recommend(
                question,
                UUID.randomUUID(),
                "优先服务首次创建产品需求的产品经理",
                "会影响角色、流程和验收范围",
                NOW
        );

        assertThat(outcome.question().status()).isEqualTo(QuestionStatus.PENDING);
        assertThat(outcome.recommendation().status()).isEqualTo(RequirementStatus.PENDING);
        assertThat(outcome.recommendation().sourceType()).isEqualTo(RequirementSourceType.AI_RECOMMENDATION);
        assertThat(outcome.recommendation().metadata()).containsEntry("impact", "会影响角色、流程和验收范围");
        assertThat(score(outcome)).isZero();
    }

    @Test
    void acceptingRecommendationConfirmsRequirementAndAnswersQuestion() {
        AnswerPolicy.RecommendationOutcome recommended = recommendation();

        AnswerPolicy.RecommendationOutcome accepted = policy.accept(recommended, NOW.plusSeconds(10));

        assertThat(accepted.question().status()).isEqualTo(QuestionStatus.ANSWERED);
        assertThat(accepted.recommendation().status()).isEqualTo(RequirementStatus.CONFIRMED);
        assertThat(accepted.recommendation().sourceType()).isEqualTo(RequirementSourceType.USER_ANSWER);
        assertThat(score(accepted)).isEqualTo(100);
    }

    @Test
    void rejectingRecommendationKeepsQuestionAndRequirementPending() {
        AnswerPolicy.RecommendationOutcome rejected = policy.reject(recommendation(), NOW.plusSeconds(10));

        assertThat(rejected.question().status()).isEqualTo(QuestionStatus.PENDING);
        assertThat(rejected.recommendation().status()).isEqualTo(RequirementStatus.PENDING);
        assertThat(rejected.recommendation().metadata()).containsEntry("recommendationRejected", true);
        assertThat(score(rejected)).isZero();
    }

    private AnswerPolicy.RecommendationOutcome recommendation() {
        return policy.recommend(
                question("目标用户是谁？", UUID.randomUUID()),
                UUID.randomUUID(),
                "优先服务首次创建产品需求的产品经理",
                "会影响角色、流程和验收范围",
                NOW
        );
    }

    private int score(AnswerPolicy.RecommendationOutcome outcome) {
        CompletenessInput input = new CompletenessInput(
                List.of(outcome.recommendation()),
                List.of(outcome.question()),
                List.of(),
                EnumSet.complementOf(EnumSet.of(RequirementDimension.ROLES_PERMISSIONS)),
                java.util.Map.of()
        );
        return new CompletenessCalculator().calculate(input).total();
    }

    private int scoreQuestion(ClarificationQuestion question) {
        CompletenessInput input = new CompletenessInput(
                List.of(),
                List.of(question),
                List.of(),
                EnumSet.complementOf(EnumSet.of(RequirementDimension.ROLES_PERMISSIONS)),
                java.util.Map.of()
        );
        return new CompletenessCalculator().calculate(input).total();
    }

    private ClarificationQuestion question(String text, UUID batchId) {
        UUID projectId = UUID.randomUUID();
        return new ClarificationQuestion(
                UUID.randomUUID(),
                projectId,
                batchId,
                0,
                text,
                "用于明确角色边界",
                RequirementDimension.ROLES_PERMISSIONS,
                "user-role",
                "user-role",
                QuestionInputType.TEXT,
                List.of(),
                List.of(),
                4.0,
                QuestionStatus.PENDING,
                NOW,
                NOW
        );
    }
}
