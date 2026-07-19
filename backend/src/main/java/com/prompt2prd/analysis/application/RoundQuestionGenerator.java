package com.prompt2prd.analysis.application;

import com.prompt2prd.analysis.domain.ClarificationOption;
import com.prompt2prd.analysis.domain.ClarificationQuestion;
import com.prompt2prd.analysis.domain.PrdCoverageArea;
import com.prompt2prd.analysis.domain.QuestionInputType;
import com.prompt2prd.analysis.domain.QuestionStatus;
import com.prompt2prd.analysis.domain.RequirementDimension;
import com.prompt2prd.model.application.ModelCallContext;
import com.prompt2prd.model.application.ModelGateway;
import com.prompt2prd.model.application.ModelMessage;
import com.prompt2prd.model.application.StructuredModelRequest;
import com.prompt2prd.model.application.StructuredModelResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Lightweight question-only generator for pre-generating the next clarification round.
 * Uses compact prompts, lower temperature, and smaller maxTokens.
 */
@Component
public final class RoundQuestionGenerator {

    private static final Logger log = LoggerFactory.getLogger(RoundQuestionGenerator.class);

    static final String QUESTION_OUTPUT_SCHEMA = """
            {
              "type": "object",
              "required": ["questions", "coverageCategories"],
              "properties": {
                "questions": {
                  "type": "array",
                  "description": "8-10 clarification questions for this round.",
                  "items": {
                    "type": "object",
                    "required": ["text", "reason", "dimension", "targetField", "semanticKey", "inputType", "options", "businessImpact", "informationGap", "dependencyCount", "risk"],
                    "properties": {
                      "text": { "type": "string" },
                      "reason": { "type": "string" },
                      "dimension": {
                        "type": "string",
                        "enum": ["PRODUCT_SCOPE", "ROLES_PERMISSIONS", "CORE_FLOW", "FEATURES", "BUSINESS_RULES", "EXCEPTIONS", "DATA_MODEL", "ARCHITECTURE_CONSTRAINTS", "PAGES_APIS", "ACCEPTANCE"]
                      },
                      "targetField": { "type": "string" },
                      "semanticKey": { "type": "string" },
                      "inputType": {
                        "type": "string",
                        "enum": ["SINGLE_SELECT", "MULTI_SELECT", "CUSTOM_TEXT", "SINGLE_SELECT_CUSTOM", "MULTI_SELECT_CUSTOM", "AI_RECOMMENDED"]
                      },
                      "options": {
                        "type": "array",
                        "items": {
                          "type": "object",
                          "required": ["label", "impact", "recommended"],
                          "properties": {
                            "label": { "type": "string" },
                            "impact": { "type": "string" },
                            "recommended": { "type": "boolean" }
                          }
                        }
                      },
                      "businessImpact": { "type": "integer", "minimum": 1, "maximum": 5 },
                      "informationGap": { "type": "integer", "minimum": 1, "maximum": 5 },
                      "dependencyCount": { "type": "integer", "minimum": 1, "maximum": 5 },
                      "risk": { "type": "integer", "minimum": 1, "maximum": 5 }
                    }
                  }
                },
                "coverageCategories": {
                  "type": "array",
                  "description": "PrdCoverageArea keys covered by this round's questions.",
                  "items": { "type": "string" }
                }
              }
            }
            """;

    private final ModelGateway modelGateway;
    private final Supplier<UUID> idGenerator;

    public RoundQuestionGenerator(ModelGateway modelGateway) {
        this(modelGateway, UUID::randomUUID);
    }

    public RoundQuestionGenerator(ModelGateway modelGateway, Supplier<UUID> idGenerator) {
        this.modelGateway = Objects.requireNonNull(modelGateway, "modelGateway");
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator");
    }

    /**
     * Generates 8-10 questions for the given round.
     *
     * @param context          the model call context
     * @param projectId        the project UUID
     * @param roundNo          the target round number
     * @param compactContext   compact analysis context built by CompactPromptBuilder
     * @param targetCoverageKeys which PrdCoverageArea keys this round should cover
     * @param alreadyCoveredKeys which keys are already covered (to avoid)
     * @return list of generated ClarificationQuestion with roundNo set
     */
    public Mono<List<ClarificationQuestion>> generate(
            ModelCallContext context,
            UUID projectId,
            int roundNo,
            CompactAnalysisContext compactContext,
            List<String> targetCoverageKeys,
            List<String> alreadyCoveredKeys) {

        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(compactContext, "compactContext");

        ModelMessage systemMessage = RoundPromptBuilder.buildPreGenerationSystemMessage(
                roundNo, targetCoverageKeys, alreadyCoveredKeys);
        ModelMessage userMessage = RoundPromptBuilder.buildUserMessage(compactContext);

        int promptChars = systemMessage.content().length() + userMessage.content().length();
        log.info("round_generation_start roundNo={} promptChars={} targetAreas={}",
                roundNo, promptChars, targetCoverageKeys.size());

        StructuredModelRequest<RoundQuestionsOutput> request = new StructuredModelRequest<>(
                context,
                List.of(systemMessage, userMessage),
                RoundQuestionsOutput.class,
                QUESTION_OUTPUT_SCHEMA);

        Instant now = Instant.now();
        UUID batchId = idGenerator.get();

        return modelGateway.generateStructured(request)
                .map(result -> convertToQuestions(result.value(), projectId, batchId, roundNo, now))
                .doOnSuccess(questions -> log.info(
                        "round_generation_complete roundNo={} questionCount={}",
                        roundNo, questions.size()))
                .doOnError(error -> log.warn(
                        "round_generation_failed roundNo={} error={}",
                        roundNo, error.getClass().getSimpleName()));
    }

    private List<ClarificationQuestion> convertToQuestions(
            RoundQuestionsOutput output,
            UUID projectId,
            UUID batchId,
            int roundNo,
            Instant now) {

        List<String> coverageCategories = output.coverageCategories() != null
                ? output.coverageCategories() : List.of();

        return output.questions().stream()
                .map(raw -> {
                    RequirementDimension dimension = parseDimension(raw.dimension());
                    QuestionInputType inputType = parseInputType(raw.inputType());
                    List<ClarificationOption> options = raw.options().stream()
                            .map(opt -> new ClarificationOption(
                                    idGenerator.get(), opt.label(), opt.impact(), opt.recommended()))
                            .toList();

                    return new ClarificationQuestion(
                            idGenerator.get(),
                            projectId,
                            batchId,
                            roundNo,
                            raw.text(),
                            raw.reason(),
                            dimension,
                            raw.targetField(),
                            raw.semanticKey(),
                            inputType,
                            options,
                            coverageCategories,
                            0,
                            QuestionStatus.PENDING,
                            now,
                            now);
                })
                .toList();
    }

    private static RequirementDimension parseDimension(String value) {
        try {
            return RequirementDimension.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException e) {
            return RequirementDimension.PRODUCT_SCOPE;
        }
    }

    private static QuestionInputType parseInputType(String value) {
        try {
            return QuestionInputType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException e) {
            return QuestionInputType.SINGLE_SELECT;
        }
    }

    /**
     * Raw model output for round question generation.
     */
    public record RoundQuestionsOutput(
            List<QuestionOutput> questions,
            List<String> coverageCategories) {

        public record QuestionOutput(
                String text,
                String reason,
                String dimension,
                String targetField,
                String semanticKey,
                String inputType,
                List<OptionOutput> options,
                int businessImpact,
                int informationGap,
                int dependencyCount,
                int risk) {

            public record OptionOutput(
                    String label,
                    String impact,
                    boolean recommended) {}
        }
    }
}
