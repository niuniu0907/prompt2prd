package com.prompt2prd.analysis.application;

import java.util.List;
import java.util.Objects;

/**
 * Lightweight analysis context for model prompts.
 * Contains only essential information — no UUIDs, timestamps, full metadata,
 * version records, old events, or deprecated requirements.
 */
public record CompactAnalysisContext(
        String projectSummary,
        List<String> confirmedFacts,
        List<String> pendingCategories,
        List<CompactQaTurn> recentAnswers,
        String currentUserAnswer,
        List<String> currentRoundQuestions,
        String language) {

    public CompactAnalysisContext {
        Objects.requireNonNull(projectSummary, "projectSummary");
        confirmedFacts = List.copyOf(confirmedFacts);
        pendingCategories = List.copyOf(pendingCategories);
        recentAnswers = List.copyOf(recentAnswers);
        currentRoundQuestions = List.copyOf(currentRoundQuestions);
        Objects.requireNonNull(language, "language");
    }

    /** A single Q&A turn with only the essential text fields. */
    public record CompactQaTurn(
            String questionText,
            String answerText,
            int roundNo) {
        public CompactQaTurn {
            Objects.requireNonNull(questionText, "questionText");
            Objects.requireNonNull(answerText, "answerText");
            if (roundNo < 0) {
                throw new IllegalArgumentException("roundNo must be non-negative");
            }
        }
    }
}
