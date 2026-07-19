package com.prompt2prd.analysis.application;

import com.prompt2prd.analysis.domain.ClarificationAnswer;
import com.prompt2prd.analysis.domain.ClarificationQuestion;
import com.prompt2prd.analysis.domain.PrdCoverageArea;
import com.prompt2prd.analysis.domain.RequirementItem;
import com.prompt2prd.analysis.domain.RequirementState;
import com.prompt2prd.analysis.domain.RequirementStatus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Builds compact analysis context from full requirement state.
 * Strips UUIDs, timestamps, metadata, and deprecated requirements.
 * Never uses RequirementItem.toString(), List.toString(), or metadata.toString().
 */
public final class CompactPromptBuilder {

    private static final int MAX_RECENT_QA_ROUNDS = 3;
    private static final int MAX_CONFIRMED_FACTS = 30;

    private CompactPromptBuilder() {}

    /**
     * Builds a compact context for the analysis/answer-processing prompt.
     */
    public static CompactAnalysisContext forAnalysis(
            RequirementState state,
            List<AnalysisContextBuilder.QuestionAnswerTurn> answerHistory,
            String currentInput) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(currentInput, "currentInput");

        String projectSummary = buildProjectSummary(state);
        List<String> confirmedFacts = extractConfirmedFacts(state);
        List<String> pendingCategories = computePendingCategories(state);
        List<CompactAnalysisContext.CompactQaTurn> recentAnswers = buildRecentAnswers(
                state, answerHistory);
        List<String> currentRoundQuestions = extractCurrentRoundQuestionTexts(state);

        return new CompactAnalysisContext(
                projectSummary,
                confirmedFacts,
                pendingCategories,
                recentAnswers,
                currentInput,
                currentRoundQuestions,
                state.project().language());
    }

    /**
     * Builds a compact context for pre-generating the next round of questions.
     */
    public static CompactAnalysisContext forRoundGeneration(
            RequirementState state,
            int targetRoundNo,
            List<ClarificationQuestion> currentRoundQuestions,
            List<String> coveredAreas) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(currentRoundQuestions, "currentRoundQuestions");

        String projectSummary = buildProjectSummary(state);
        List<String> confirmedFacts = extractConfirmedFacts(state);
        List<String> pendingCategories = computePendingCategoriesExcluding(state, coveredAreas);
        List<CompactAnalysisContext.CompactQaTurn> recentAnswers = buildRecentAnswersFromState(state);
        List<String> currentQuestionTexts = currentRoundQuestions.stream()
                .map(ClarificationQuestion::text)
                .toList();

        return new CompactAnalysisContext(
                projectSummary,
                confirmedFacts,
                pendingCategories,
                recentAnswers,
                "Generate clarification questions for round " + targetRoundNo,
                currentQuestionTexts,
                state.project().language());
    }

    private static String buildProjectSummary(RequirementState state) {
        StringBuilder sb = new StringBuilder();
        sb.append("Project: ").append(state.project().name());
        sb.append("\nLanguage: ").append(state.project().language());
        sb.append("\nStage: ").append(state.project().stage().name());
        sb.append("\nCompleteness: ").append(state.project().completeness()).append("%");

        // Build a concise summary from confirmed requirements
        List<String> goalRequirements = state.requirements().stream()
                .filter(item -> item.status() == RequirementStatus.CONFIRMED
                        && item.type() == com.prompt2prd.analysis.domain.RequirementType.PRODUCT_GOAL)
                .map(item -> item.content())
                .toList();

        if (!goalRequirements.isEmpty()) {
            sb.append("\nProduct goals:\n");
            for (String goal : goalRequirements) {
                sb.append("- ").append(truncate(goal, 200)).append("\n");
            }
        }
        return sb.toString();
    }

    private static List<String> extractConfirmedFacts(RequirementState state) {
        return state.requirements().stream()
                .filter(item -> item.status() == RequirementStatus.CONFIRMED)
                .sorted(Comparator.comparing(RequirementItem::type))
                .limit(MAX_CONFIRMED_FACTS)
                .map(item -> "[" + item.type().name() + "] " + item.title() + ": " + truncate(item.content(), 200))
                .toList();
    }

    private static List<String> computePendingCategories(RequirementState state) {
        Set<String> coveredKeys = new HashSet<>();
        for (RequirementItem item : state.requirements()) {
            if (item.status() == RequirementStatus.CONFIRMED) {
                dimensionOf(item).ifPresent(coveredKeys::add);
            }
        }
        // Also check answered questions
        Map<UUID, ClarificationQuestion> questionsById = state.questions().stream()
                .collect(Collectors.toMap(ClarificationQuestion::id, q -> q, (a, b) -> a));
        for (ClarificationAnswer answer : state.answers()) {
            ClarificationQuestion question = questionsById.get(answer.questionId());
            if (question != null && !answer.skipped()) {
                dimensionOf(question).ifPresent(coveredKeys::add);
            }
        }

        List<String> pending = new ArrayList<>();
        for (PrdCoverageArea area : PrdCoverageArea.values()) {
            if (!coveredKeys.contains(area.dimension().name())) {
                pending.add(area.key() + " (" + area.label() + ")");
            }
        }
        return pending;
    }

    private static List<String> computePendingCategoriesExcluding(
            RequirementState state, List<String> coveredAreas) {
        Set<String> excludeKeys = new HashSet<>(coveredAreas != null ? coveredAreas : List.of());
        Set<String> coveredDims = new HashSet<>();
        for (RequirementItem item : state.requirements()) {
            if (item.status() == RequirementStatus.CONFIRMED) {
                dimensionOf(item).ifPresent(coveredDims::add);
            }
        }

        List<String> pending = new ArrayList<>();
        for (PrdCoverageArea area : PrdCoverageArea.values()) {
            if (!excludeKeys.contains(area.key()) && !coveredDims.contains(area.dimension().name())) {
                pending.add(area.key() + " (" + area.label() + ")");
            }
        }
        return pending;
    }

    private static List<CompactAnalysisContext.CompactQaTurn> buildRecentAnswers(
            RequirementState state,
            List<AnalysisContextBuilder.QuestionAnswerTurn> answerHistory) {
        if (answerHistory == null || answerHistory.isEmpty()) {
            return buildRecentAnswersFromState(state);
        }
        List<AnalysisContextBuilder.QuestionAnswerTurn> sorted = new ArrayList<>(answerHistory);
        sorted.sort(Comparator.comparing(AnalysisContextBuilder.QuestionAnswerTurn::answeredAt).reversed());
        List<AnalysisContextBuilder.QuestionAnswerTurn> recent = sorted.stream()
                .limit(MAX_RECENT_QA_ROUNDS * 10) // ~10 questions per round
                .toList();

        return recent.stream()
                .map(turn -> new CompactAnalysisContext.CompactQaTurn(
                        turn.question(), turn.answer(), 0))
                .toList();
    }

    private static List<CompactAnalysisContext.CompactQaTurn> buildRecentAnswersFromState(RequirementState state) {
        Map<UUID, ClarificationQuestion> questionsById = state.questions().stream()
                .collect(Collectors.toMap(ClarificationQuestion::id, q -> q, (a, b) -> a));

        return state.answers().stream()
                .sorted(Comparator.comparing(ClarificationAnswer::updatedAt).reversed())
                .limit(MAX_RECENT_QA_ROUNDS * 10)
                .map(answer -> {
                    ClarificationQuestion question = questionsById.get(answer.questionId());
                    String questionText = question != null ? question.text() : "(unknown question)";
                    String answerText = buildAnswerText(answer, question);
                    int roundNo = question != null ? question.roundNo() : 0;
                    return new CompactAnalysisContext.CompactQaTurn(questionText, answerText, roundNo);
                })
                .toList();
    }

    private static String buildAnswerText(ClarificationAnswer answer, ClarificationQuestion question) {
        if (answer.skipped()) return "(skipped)";
        StringBuilder sb = new StringBuilder();
        if (question != null) {
            Map<UUID, String> optionLabels = question.options().stream()
                    .collect(Collectors.toMap(o -> o.id(), o -> o.label(), (a, b) -> a));
            for (UUID optionId : answer.selectedOptionIds()) {
                String label = optionLabels.getOrDefault(optionId, optionId.toString());
                if (!sb.isEmpty()) sb.append(", ");
                sb.append(label);
            }
        }
        if (answer.customAnswer() != null && !answer.customAnswer().isBlank()) {
            if (!sb.isEmpty()) sb.append("; ");
            sb.append(answer.customAnswer());
        }
        return sb.isEmpty() ? "(no answer)" : sb.toString();
    }

    private static List<String> extractCurrentRoundQuestionTexts(RequirementState state) {
        return state.questions().stream()
                .filter(q -> q.status() == com.prompt2prd.analysis.domain.QuestionStatus.PENDING)
                .map(ClarificationQuestion::text)
                .toList();
    }

    private static java.util.Optional<String> dimensionOf(RequirementItem item) {
        Object override = item.metadata().get("dimension");
        if (override instanceof String value && !value.isBlank()) {
            return java.util.Optional.of(value.trim().toUpperCase(java.util.Locale.ROOT));
        }
        return switch (item.type()) {
            case PRODUCT_GOAL -> java.util.Optional.of("PRODUCT_SCOPE");
            case ROLE -> java.util.Optional.of("ROLES_PERMISSIONS");
            case USER_STORY -> java.util.Optional.of("CORE_FLOW");
            case FEATURE -> java.util.Optional.of("FEATURES");
            case BUSINESS_RULE -> java.util.Optional.of("BUSINESS_RULES");
            case EXCEPTION_SCENARIO -> java.util.Optional.of("EXCEPTIONS");
            case DATA_MODEL -> java.util.Optional.of("DATA_MODEL");
            case TECHNICAL_CONSTRAINT, NON_FUNCTIONAL_REQUIREMENT ->
                    java.util.Optional.of("ARCHITECTURE_CONSTRAINTS");
            case PAGE, API -> java.util.Optional.of("PAGES_APIS");
            case ACCEPTANCE_CRITERION -> java.util.Optional.of("ACCEPTANCE");
            default -> java.util.Optional.empty();
        };
    }

    private static java.util.Optional<String> dimensionOf(ClarificationQuestion question) {
        return java.util.Optional.of(question.dimension().name());
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) return "";
        if (value.length() <= maxLength) return value;
        return value.substring(0, maxLength) + "...";
    }
}
