package com.prompt2prd.analysis.application;

import com.prompt2prd.analysis.domain.PrdCoverageArea;
import com.prompt2prd.model.application.ModelMessage;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Builds round-aware prompts for clarification question generation.
 * Produces compact system and user messages with round-specific coverage instructions.
 */
public final class RoundPromptBuilder {

    private RoundPromptBuilder() {}

    /**
     * Builds the system message for question pre-generation.
     */
    public static ModelMessage buildPreGenerationSystemMessage(
            int targetRoundNo,
            List<String> targetCoverageKeys,
            List<String> alreadyCoveredKeys) {

        Objects.requireNonNull(targetCoverageKeys, "targetCoverageKeys");
        Objects.requireNonNull(alreadyCoveredKeys, "alreadyCoveredKeys");

        String coverageInstruction = buildCoverageInstruction(targetCoverageKeys, alreadyCoveredKeys);

        String systemPrompt = """
                You are generating clarification questions for Round %d of a requirements interview.

                %s

                Rules:
                - Generate 8-10 high-value questions. Do not generate fewer than 8 unless genuinely no meaningful questions remain.
                - Do not generate duplicate questions or questions already covered in previous rounds.
                - Mix input types: SINGLE_SELECT, MULTI_SELECT, CUSTOM_TEXT, SINGLE_SELECT_CUSTOM, MULTI_SELECT_CUSTOM, AI_RECOMMENDED.
                - For SINGLE_SELECT and MULTI_SELECT: provide 2-6 concrete, actionable options.
                - For AI_RECOMMENDED: provide 2-6 options and mark the best one as recommended=true.
                - For CUSTOM_TEXT: no options needed, user types freely.
                - Return only valid JSON matching the output schema.
                - Do not include Markdown, code fences, or explanatory text.
                - Every question must target one of the specified coverage areas above.
                - Avoid repeating any question listed in "current round questions" in the user message.
                """.formatted(targetRoundNo, coverageInstruction);

        return new ModelMessage(ModelMessage.Role.SYSTEM, systemPrompt);
    }

    /**
     * Builds the system message for answer processing (requirement extraction only, no questions).
     */
    public static ModelMessage buildAnswerProcessingSystemMessage() {
        return new ModelMessage(ModelMessage.Role.SYSTEM,
                "Extract facts, assumptions, gaps, and conflicts from the submitted answers. "
                        + "Return only the requested JSON object. "
                        + "Never mark model content as user-confirmed. "
                        + "Requirement status must be only INFERRED or PENDING. "
                        + "The questions array MUST be empty — do not generate clarification questions. "
                        + "Requirements should reference the PRD coverage areas:\n"
                        + PrdCoverageArea.promptChecklist() + "\n"
                        + "Every requirement targetField must start with one of: "
                        + PrdCoverageArea.targetFieldInstruction() + ".");
    }

    /**
     * Builds the user message from compact analysis context.
     */
    public static ModelMessage buildUserMessage(CompactAnalysisContext context) {
        Objects.requireNonNull(context, "context");

        StringBuilder sb = new StringBuilder();
        sb.append(context.projectSummary());

        if (!context.confirmedFacts().isEmpty()) {
            sb.append("\n\nConfirmed facts:\n");
            for (String fact : context.confirmedFacts()) {
                sb.append("- ").append(fact).append("\n");
            }
        }

        if (!context.pendingCategories().isEmpty()) {
            sb.append("\nPending analysis categories:\n");
            for (String category : context.pendingCategories()) {
                sb.append("- ").append(category).append("\n");
            }
        }

        if (!context.recentAnswers().isEmpty()) {
            sb.append("\nRecent Q&A:\n");
            for (CompactAnalysisContext.CompactQaTurn turn : context.recentAnswers()) {
                sb.append("Q: ").append(turn.questionText()).append("\n");
                sb.append("A: ").append(turn.answerText()).append("\n\n");
            }
        }

        if (context.currentUserAnswer() != null && !context.currentUserAnswer().isBlank()) {
            sb.append("\nCurrent input: ").append(context.currentUserAnswer());
        }

        if (!context.currentRoundQuestions().isEmpty()) {
            sb.append("\n\nCurrent round questions (do NOT repeat these):\n");
            for (String question : context.currentRoundQuestions()) {
                sb.append("- ").append(question).append("\n");
            }
        }

        return new ModelMessage(ModelMessage.Role.USER, sb.toString());
    }

    private static String buildCoverageInstruction(
            List<String> targetKeys,
            List<String> alreadyCoveredKeys) {

        StringBuilder sb = new StringBuilder();
        sb.append("Primary coverage areas for this round:\n");
        for (String key : targetKeys) {
            PrdCoverageArea area = findArea(key);
            if (area != null) {
                sb.append("- ").append(area.key()).append(" (").append(area.label())
                        .append("): ").append(area.guidance()).append("\n");
            } else {
                sb.append("- ").append(key).append("\n");
            }
        }

        if (!alreadyCoveredKeys.isEmpty()) {
            sb.append("\nAlready covered (do NOT target unless significant gaps remain):\n");
            for (String key : alreadyCoveredKeys) {
                PrdCoverageArea area = findArea(key);
                sb.append("- ").append(area != null ? area.label() : key).append("\n");
            }
        }

        return sb.toString();
    }

    private static PrdCoverageArea findArea(String key) {
        for (PrdCoverageArea area : PrdCoverageArea.values()) {
            if (area.key().equals(key)) {
                return area;
            }
        }
        return null;
    }

    /**
     * Returns the coverage area keys assigned to Round 1.
     * Round 1 focuses on product scope, roles, core flow, features, and business rules.
     */
    public static List<String> roundOneCoverageKeys() {
        return List.of(
                PrdCoverageArea.PRODUCT_CONTEXT.key(),
                PrdCoverageArea.ROLES_SCENARIOS.key(),
                PrdCoverageArea.FEATURE_SCOPE_PRIORITIES.key(),
                PrdCoverageArea.CORE_BUSINESS_FLOW.key(),
                PrdCoverageArea.RULES_EXCEPTIONS.key()
        );
    }

    /**
     * Returns the coverage area keys assigned to Round 2.
     * Round 2 focuses on user stories, exceptions, pages, data, acceptance, non-functional, and assumptions.
     */
    public static List<String> roundTwoCoverageKeys() {
        return List.of(
                PrdCoverageArea.USER_STORIES.key(),
                PrdCoverageArea.PAGES_STATES.key(),
                PrdCoverageArea.DATA_ENTITIES_FIELDS.key(),
                PrdCoverageArea.ACCEPTANCE_CRITERIA.key(),
                PrdCoverageArea.NON_FUNCTIONAL.key(),
                PrdCoverageArea.ASSUMPTIONS_RISKS_OPEN_ITEMS.key()
        );
    }

    /**
     * Returns uncovered coverage area keys not yet in coveredKeys, prioritizing areas
     * not yet addressed. If no areas remain, returns a subset of least-covered areas.
     */
    public static List<String> nextRoundCoverageKeys(
            List<String> coveredKeys, int maxAreas) {
        List<PrdCoverageArea> uncovered = java.util.Arrays.stream(PrdCoverageArea.values())
                .filter(area -> !coveredKeys.contains(area.key()))
                .collect(Collectors.toList());

        if (uncovered.isEmpty()) {
            // All areas covered — allow revisiting with lower priority
            return java.util.Arrays.stream(PrdCoverageArea.values())
                    .limit(maxAreas)
                    .map(PrdCoverageArea::key)
                    .collect(Collectors.toList());
        }

        return uncovered.stream()
                .limit(maxAreas)
                .map(PrdCoverageArea::key)
                .collect(Collectors.toList());
    }
}
