package com.prompt2prd.analysis.application;

import com.prompt2prd.analysis.domain.ProjectSummary;
import com.prompt2prd.analysis.domain.RequirementItem;
import com.prompt2prd.analysis.domain.RequirementState;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class AnalysisContextBuilder {

    public AnalysisContext build(
            RequirementState state,
            List<QuestionAnswerTurn> answerHistory,
            List<String> missingInformation,
            String outputSchema) {
        Objects.requireNonNull(state, "state must not be null");
        requireText(outputSchema, "outputSchema");

        List<QuestionAnswerTurn> history = answerHistory == null
                ? List.of()
                : answerHistory.stream()
                        .sorted(Comparator.comparing(QuestionAnswerTurn::answeredAt)
                                .thenComparing(QuestionAnswerTurn::questionId))
                        .toList();
        List<String> gaps = missingInformation == null
                ? List.of()
                : missingInformation.stream()
                        .filter(Objects::nonNull)
                        .map(String::trim)
                        .filter(value -> !value.isEmpty())
                        .toList();
        List<RequirementItem> currentRequirements = List.copyOf(state.requirements());
        List<RequirementItem> lockedRequirements = currentRequirements.stream()
                .filter(RequirementItem::locked)
                .toList();

        return new AnalysisContext(
                state.project(),
                currentRequirements,
                lockedRequirements,
                history,
                gaps,
                state.project().language(),
                outputSchema.trim()
        );
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }

    public record QuestionAnswerTurn(
            UUID questionId,
            UUID batchId,
            String question,
            String answer,
            Instant answeredAt) {
        public QuestionAnswerTurn {
            Objects.requireNonNull(questionId, "questionId must not be null");
            Objects.requireNonNull(batchId, "batchId must not be null");
            requireText(question, "question");
            requireText(answer, "answer");
            Objects.requireNonNull(answeredAt, "answeredAt must not be null");
            question = question.trim();
            answer = answer.trim();
        }
    }

    public record AnalysisContext(
            ProjectSummary project,
            List<RequirementItem> currentRequirements,
            List<RequirementItem> lockedRequirements,
            List<QuestionAnswerTurn> answerHistory,
            List<String> missingInformation,
            String language,
            String outputSchema) {
        public AnalysisContext {
            Objects.requireNonNull(project, "project must not be null");
            currentRequirements = List.copyOf(currentRequirements);
            lockedRequirements = List.copyOf(lockedRequirements);
            answerHistory = List.copyOf(answerHistory);
            missingInformation = List.copyOf(missingInformation);
            requireText(language, "language");
            requireText(outputSchema, "outputSchema");
            language = language.trim();
            outputSchema = outputSchema.trim();
        }
    }
}
