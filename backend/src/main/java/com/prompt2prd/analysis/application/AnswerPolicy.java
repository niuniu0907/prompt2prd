package com.prompt2prd.analysis.application;

import com.prompt2prd.analysis.domain.ClarificationQuestion;
import com.prompt2prd.analysis.domain.QuestionStatus;
import com.prompt2prd.analysis.domain.RequirementItem;
import com.prompt2prd.analysis.domain.RequirementSourceType;
import com.prompt2prd.analysis.domain.RequirementStatus;
import com.prompt2prd.analysis.domain.RequirementType;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class AnswerPolicy {

    public List<ClarificationQuestion> skipOne(
            List<ClarificationQuestion> questions,
            UUID questionId) {
        Objects.requireNonNull(questions, "questions must not be null");
        Objects.requireNonNull(questionId, "questionId must not be null");
        Instant now = Instant.now();
        return questions.stream()
                .map(question -> question.id().equals(questionId)
                        ? question.withStatus(QuestionStatus.SKIPPED, now)
                        : question)
                .toList();
    }

    public List<ClarificationQuestion> skipBatch(
            List<ClarificationQuestion> questions,
            UUID batchId) {
        Objects.requireNonNull(questions, "questions must not be null");
        Objects.requireNonNull(batchId, "batchId must not be null");
        Instant now = Instant.now();
        return questions.stream()
                .map(question -> question.batchId().equals(batchId)
                        ? question.withStatus(QuestionStatus.SKIPPED, now)
                        : question)
                .toList();
    }

    public RecommendationOutcome recommend(
            ClarificationQuestion question,
            UUID requirementId,
            String recommendation,
            String impact,
            Instant now) {
        Objects.requireNonNull(question, "question must not be null");
        Objects.requireNonNull(requirementId, "requirementId must not be null");
        requireText(recommendation, "recommendation");
        requireText(impact, "impact");
        Objects.requireNonNull(now, "now must not be null");

        Map<String, Object> metadata = Map.of(
                "dimension", question.dimension().name(),
                "questionId", question.id().toString(),
                "impact", impact.trim()
        );
        RequirementItem item = new RequirementItem(
                requirementId,
                question.projectId(),
                RequirementType.ASSUMPTION,
                "推荐：" + question.targetField(),
                recommendation.trim(),
                RequirementStatus.PENDING,
                RequirementSourceType.AI_RECOMMENDATION,
                question.id(),
                false,
                metadata,
                now,
                now
        );
        return new RecommendationOutcome(question, item);
    }

    public RecommendationOutcome accept(RecommendationOutcome outcome, Instant now) {
        Objects.requireNonNull(outcome, "outcome must not be null");
        Objects.requireNonNull(now, "now must not be null");
        RequirementItem current = outcome.recommendation();
        RequirementItem accepted = new RequirementItem(
                current.id(), current.projectId(), current.type(), current.title(), current.content(),
                RequirementStatus.CONFIRMED, RequirementSourceType.USER_ANSWER, current.sourceId(),
                false, current.metadata(), current.createdAt(), now);
        return new RecommendationOutcome(
                outcome.question().withStatus(QuestionStatus.ANSWERED, now), accepted);
    }

    public RecommendationOutcome reject(RecommendationOutcome outcome, Instant now) {
        Objects.requireNonNull(outcome, "outcome must not be null");
        Objects.requireNonNull(now, "now must not be null");
        RequirementItem current = outcome.recommendation();
        Map<String, Object> metadata = new HashMap<>(current.metadata());
        metadata.put("recommendationRejected", true);
        RequirementItem rejected = new RequirementItem(
                current.id(), current.projectId(), current.type(), current.title(), current.content(),
                RequirementStatus.PENDING, RequirementSourceType.AI_RECOMMENDATION, current.sourceId(),
                false, metadata, current.createdAt(), now);
        return new RecommendationOutcome(
                outcome.question().withStatus(QuestionStatus.PENDING, now), rejected);
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }

    public record RecommendationOutcome(
            ClarificationQuestion question,
            RequirementItem recommendation) {
        public RecommendationOutcome {
            Objects.requireNonNull(question, "question must not be null");
            Objects.requireNonNull(recommendation, "recommendation must not be null");
            if (!question.projectId().equals(recommendation.projectId())) {
                throw new IllegalArgumentException("question and recommendation must share a project");
            }
        }
    }
}
