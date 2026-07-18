package com.prompt2prd.analysis.application;

import com.prompt2prd.analysis.domain.ClarificationOption;
import com.prompt2prd.analysis.domain.ClarificationQuestion;
import com.prompt2prd.analysis.domain.CompletenessCalculator;
import com.prompt2prd.analysis.domain.CompletenessInput;
import com.prompt2prd.analysis.domain.PrdCoverageArea;
import com.prompt2prd.analysis.domain.QuestionInputType;
import com.prompt2prd.analysis.domain.QuestionSelector;
import com.prompt2prd.analysis.domain.QuestionStatus;
import com.prompt2prd.analysis.domain.RequirementDimension;
import com.prompt2prd.analysis.domain.RequirementItem;
import com.prompt2prd.analysis.domain.RequirementSourceType;
import com.prompt2prd.analysis.domain.RequirementState;
import com.prompt2prd.analysis.domain.RequirementStatus;
import com.prompt2prd.analysis.domain.RequirementType;
import com.prompt2prd.model.application.ModelGateway;
import com.prompt2prd.model.application.ModelMessage;
import com.prompt2prd.model.application.StructuredModelRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

@Component
public final class RequirementAnalyzer implements AnalysisEngine {

    static final String OUTPUT_SCHEMA = """
            {
              "type": "object",
              "required": ["suggestedProjectName", "requirements", "questions", "missingInformation"],
              "properties": {
                "suggestedProjectName": {
                  "type": "string",
                  "description": "A short project name in the project language."
                },
                "requirements": {
                  "type": "array",
                  "description": "Facts, assumptions, or gaps extracted from the current input. Use an empty array when none.",
                  "items": {
                    "type": "object",
                    "required": ["targetRequirementId", "type", "title", "content", "status"],
                    "properties": {
                      "targetRequirementId": {
                        "type": "string",
                        "description": "Existing requirement UUID to update, or an empty string for a new requirement."
                      },
                      "type": {
                        "type": "string",
                        "enum": ["PRODUCT_GOAL", "ROLE", "FEATURE", "USER_STORY", "BUSINESS_RULE", "EXCEPTION_SCENARIO", "TECHNICAL_CONSTRAINT", "DATA_MODEL", "ACCEPTANCE_CRITERION", "PAGE", "API", "IMPLEMENTATION_PHASE", "CODING_AGENT_CONSTRAINT", "NON_FUNCTIONAL_REQUIREMENT", "ASSUMPTION", "RISK_OPEN_ITEM", "MISSING_INFORMATION"]
                      },
                      "title": {
                        "type": "string",
                        "description": "Concise user-facing requirement title."
                      },
                      "content": {
                        "type": "string",
                        "description": "Concrete requirement content in the project language."
                      },
                      "status": {
                        "type": "string",
                        "enum": ["INFERRED", "PENDING"],
                        "description": "Model output must never mark content as CONFIRMED or CONFLICTED."
                      }
                    }
                  }
                },
                "questions": {
                  "type": "array",
                  "description": "Five to ten high-value clarification questions when important information is missing. Use an empty array only when no useful question remains.",
                  "items": {
                    "type": "object",
                    "required": ["text", "reason", "dimension", "targetField", "semanticKey", "inputType", "options", "businessImpact", "informationGap", "dependencyCount", "risk"],
                    "properties": {
                      "text": {
                        "type": "string",
                        "description": "The clarification question in the project language."
                      },
                      "reason": {
                        "type": "string",
                        "description": "Why this question matters."
                      },
                      "dimension": {
                        "type": "string",
                        "enum": ["PRODUCT_SCOPE", "ROLES_PERMISSIONS", "CORE_FLOW", "FEATURES", "BUSINESS_RULES", "EXCEPTIONS", "DATA_MODEL", "ARCHITECTURE_CONSTRAINTS", "PAGES_APIS", "ACCEPTANCE"]
                      },
                      "targetField": {
                        "type": "string",
                        "description": "Stable field being clarified. Prefix it with one PRD coverage key, such as productContext.goal, rolesScenarios.buyer, featureScopePriorities.mvp, coreBusinessFlow.orderLifecycle, userStories.buyerStory, rulesExceptions.refundRule, pagesStates.checkoutEmpty, dataEntitiesFields.order, apiRequirements.createOrder, acceptanceCriteria.refundScenario, nonFunctional.security, assumptionsRisksOpenItems.openRisk."
                      },
                      "semanticKey": {
                        "type": "string",
                        "description": "Stable deduplication key for this question."
                      },
                      "inputType": {
                        "type": "string",
                        "enum": ["SINGLE_SELECT", "MULTI_SELECT"],
                        "description": "Every clarification question must be selectable. Use SINGLE_SELECT when one answer applies and MULTI_SELECT when multiple answers may apply."
                      },
                      "options": {
                        "type": "array",
                        "description": "Required for every question. Provide at least two concrete options and mark the best/default option as recommended when applicable.",
                        "items": {
                          "type": "object",
                          "required": ["label", "impact", "recommended"],
                          "properties": {
                            "label": {
                              "type": "string"
                            },
                            "impact": {
                              "type": "string"
                            },
                            "recommended": {
                              "type": "boolean"
                            }
                          }
                        }
                      },
                      "businessImpact": {
                        "type": "integer",
                        "minimum": 1,
                        "maximum": 5
                      },
                      "informationGap": {
                        "type": "integer",
                        "minimum": 1,
                        "maximum": 5
                      },
                      "dependencyCount": {
                        "type": "integer",
                        "minimum": 1,
                        "maximum": 5
                      },
                      "risk": {
                        "type": "integer",
                        "minimum": 1,
                        "maximum": 5
                      }
                    }
                  }
                },
                "missingInformation": {
                  "type": "array",
                  "description": "Important missing information summarized in the project language.",
                  "items": {
                    "type": "string"
                  }
                }
              }
            }
            """;

    private final ModelGateway modelGateway;
    private final Validator validator;
    private final RequirementStateMerger merger;
    private final QuestionSelector questionSelector;
    private final CompletenessCalculator completenessCalculator;
    private final Clock clock;
    private final Supplier<UUID> idGenerator;

    @Autowired
    public RequirementAnalyzer(ModelGateway modelGateway) {
        this(modelGateway,
                Validation.buildDefaultValidatorFactory().getValidator(),
                new RequirementStateMerger(),
                new QuestionSelector(),
                new CompletenessCalculator(),
                Clock.systemUTC(),
                UUID::randomUUID);
    }

    RequirementAnalyzer(
            ModelGateway modelGateway,
            Validator validator,
            RequirementStateMerger merger,
            QuestionSelector questionSelector,
            CompletenessCalculator completenessCalculator,
            Clock clock,
            Supplier<UUID> idGenerator) {
        this.modelGateway = Objects.requireNonNull(modelGateway, "modelGateway");
        this.validator = Objects.requireNonNull(validator, "validator");
        this.merger = Objects.requireNonNull(merger, "merger");
        this.questionSelector = Objects.requireNonNull(questionSelector, "questionSelector");
        this.completenessCalculator = Objects.requireNonNull(completenessCalculator, "completenessCalculator");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator");
    }

    @Override
    public Mono<AnalysisResult> analyze(AnalysisCommand command) {
        Objects.requireNonNull(command, "command");
        StructuredModelRequest<AnalysisModelOutput> request = new StructuredModelRequest<>(
                command.modelContext(),
                List.of(
                        new ModelMessage(ModelMessage.Role.SYSTEM,
                                "Extract facts, assumptions, gaps, conflicts, and clarification questions. "
                                        + "Return only the requested JSON object. "
                                        + "Never mark model content as user-confirmed. "
                                        + "Requirement status must be only INFERRED or PENDING. "
                                        + "The clarification loop must converge toward these minimum PRD coverage areas:\n"
                                        + PrdCoverageArea.promptChecklist() + "\n"
                                        + "Do not say no useful question remains until the applicable areas are answered, "
                                        + "explicitly skipped, or listed as assumptions/risks/open items. "
                                        + "Every clarification question must be selectable: use only SINGLE_SELECT or MULTI_SELECT "
                                        + "and provide at least two concrete options. For open-ended topics, provide common choices "
                                        + "plus an option that lets the user describe another answer. "
                                        + "Every question targetField must start with one of: "
                                        + PrdCoverageArea.targetFieldInstruction() + "."),
                        new ModelMessage(ModelMessage.Role.USER,
                                prompt(command.context(), command.currentInput()))),
                AnalysisModelOutput.class,
                OUTPUT_SCHEMA);
        return modelGateway.generateStructured(request)
                .map(result -> convertAndMerge(command.currentState(), result.value()))
                .onErrorMap(this::mapRetryableFailure);
    }

    private AnalysisResult convertAndMerge(RequirementState current, AnalysisModelOutput output) {
        var violations = validator.validate(output);
        if (!violations.isEmpty()) {
            String field = violations.stream()
                    .map(ConstraintViolation::getPropertyPath)
                    .map(Object::toString)
                    .sorted()
                    .findFirst()
                    .orElse("output");
            throw new AnalysisRetryableException("Structured analysis output is invalid at " + field);
        }
        try {
            Instant now = clock.instant();
            List<ValidatedRequirementPatch> patches = output.requirements().stream()
                    .map(candidate -> toPatch(current, candidate, now))
                    .toList();
            RequirementStateMerger.MergeResult merged = merger.merge(current, patches);
            UUID batchId = idGenerator.get();
            List<com.prompt2prd.analysis.domain.QuestionCandidate> candidates = output.questions().stream()
                    .map(candidate -> toQuestionCandidate(current, batchId, candidate, now))
                    .toList();
            List<ClarificationQuestion> selected = questionSelector.select(candidates);
            List<ClarificationQuestion> questions = new ArrayList<>(merged.state().questions());
            questions.addAll(selected);
            var completeness = completenessCalculator.calculate(new CompletenessInput(
                    merged.state().requirements(), questions, merged.state().conflicts(),
                    java.util.Set.of(), java.util.Map.of()));
            RequirementState finalState = new RequirementState(
                    merged.state().project().withCompleteness(completeness.total()),
                    merged.state().requirements(), questions, merged.state().answers(),
                    merged.state().conflicts(), completeness);
            return new AnalysisResult(
                    finalState,
                    merged.appliedRequirements(),
                    selected,
                    merged.createdConflicts(),
                    normalizeGaps(output.missingInformation()),
                    output.suggestedProjectName().trim());
        } catch (AnalysisRetryableException exception) {
            throw exception;
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new AnalysisRetryableException("Structured analysis output contains invalid values", exception);
        }
    }

    private ValidatedRequirementPatch toPatch(
            RequirementState current,
            AnalysisModelOutput.RequirementCandidate raw,
            Instant now) {
        RequirementType type = parseEnum(RequirementType.class, raw.type());
        RequirementStatus status = parseEnum(RequirementStatus.class, raw.status());
        if (status != RequirementStatus.INFERRED && status != RequirementStatus.PENDING) {
            throw new AnalysisRetryableException("Model requirements must remain inferred or pending");
        }
        UUID targetId = raw.targetRequirementId() == null || raw.targetRequirementId().isBlank()
                ? null
                : UUID.fromString(raw.targetRequirementId().trim());
        RequirementDimension dimension = dimensionFor(type);
        RequirementItem item = new RequirementItem(
                idGenerator.get(), current.project().id(), type, raw.title(), raw.content(), status,
                RequirementSourceType.AI_INFERENCE, null, false,
                Map.of("dimension", dimension.name()), now, now);
        return ValidatedRequirementPatch.of(targetId, item);
    }

    private com.prompt2prd.analysis.domain.QuestionCandidate toQuestionCandidate(
            RequirementState current,
            UUID batchId,
            AnalysisModelOutput.QuestionCandidate raw,
            Instant now) {
        RequirementDimension dimension = parseEnum(RequirementDimension.class, raw.dimension());
        QuestionInputType inputType = parseEnum(QuestionInputType.class, raw.inputType());
        List<ClarificationOption> options = raw.options().stream()
                .map(option -> new ClarificationOption(
                        idGenerator.get(), option.label(), option.impact(), option.recommended()))
                .toList();
        if (inputType != QuestionInputType.SINGLE_SELECT && inputType != QuestionInputType.MULTI_SELECT) {
            throw new AnalysisRetryableException("Clarification questions must be selectable");
        }
        if (options.size() < 2) {
            throw new AnalysisRetryableException("Selectable questions require at least two options");
        }
        ClarificationQuestion question = new ClarificationQuestion(
                idGenerator.get(), current.project().id(), batchId,
                raw.text(), raw.reason(), dimension, raw.targetField(), raw.semanticKey(),
                inputType, options, 0, QuestionStatus.PENDING, now, now);
        return new com.prompt2prd.analysis.domain.QuestionCandidate(
                question, raw.businessImpact(), raw.informationGap(),
                raw.dependencyCount(), raw.risk());
    }

    private RequirementDimension dimensionFor(RequirementType type) {
        return switch (type) {
            case PRODUCT_GOAL -> RequirementDimension.PRODUCT_SCOPE;
            case ROLE -> RequirementDimension.ROLES_PERMISSIONS;
            case USER_STORY -> RequirementDimension.CORE_FLOW;
            case FEATURE -> RequirementDimension.FEATURES;
            case BUSINESS_RULE -> RequirementDimension.BUSINESS_RULES;
            case EXCEPTION_SCENARIO -> RequirementDimension.EXCEPTIONS;
            case DATA_MODEL -> RequirementDimension.DATA_MODEL;
            case TECHNICAL_CONSTRAINT -> RequirementDimension.ARCHITECTURE_CONSTRAINTS;
            case PAGE, API -> RequirementDimension.PAGES_APIS;
            case ACCEPTANCE_CRITERION -> RequirementDimension.ACCEPTANCE;
            case NON_FUNCTIONAL_REQUIREMENT -> RequirementDimension.ARCHITECTURE_CONSTRAINTS;
            case IMPLEMENTATION_PHASE, CODING_AGENT_CONSTRAINT, ASSUMPTION, RISK_OPEN_ITEM,
                    MISSING_INFORMATION -> RequirementDimension.PRODUCT_SCOPE;
        };
    }

    private <E extends Enum<E>> E parseEnum(Class<E> type, String value) {
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw new AnalysisRetryableException("Unknown " + type.getSimpleName(), exception);
        }
    }

    private List<String> normalizeGaps(List<String> gaps) {
        return gaps.stream().map(String::trim).filter(value -> !value.isEmpty()).distinct().toList();
    }

    private String prompt(AnalysisContextBuilder.AnalysisContext context, String currentInput) {
        return "Project=" + context.project().name()
                + "\nLanguage=" + context.language()
                + "\nCurrent input=" + currentInput
                + "\nCurrent requirements=" + context.currentRequirements()
                + "\nLocked requirements=" + context.lockedRequirements()
                + "\nAnswer history=" + context.answerHistory()
                + "\nMissing information=" + context.missingInformation()
                + "\nPRD coverage checklist=\n" + PrdCoverageArea.promptChecklist()
                + "\nOutput schema=" + context.outputSchema();
    }

    private Throwable mapRetryableFailure(Throwable failure) {
        return failure instanceof AnalysisRetryableException
                ? failure
                : failure;
    }

    public record AnalysisCommand(
            com.prompt2prd.model.application.ModelCallContext modelContext,
            RequirementState currentState,
            AnalysisContextBuilder.AnalysisContext context,
            String currentInput) {
        public AnalysisCommand(
                com.prompt2prd.model.application.ModelCallContext modelContext,
                RequirementState currentState,
                AnalysisContextBuilder.AnalysisContext context) {
            this(modelContext, currentState, context, "Continue analysis from the current state");
        }

        public AnalysisCommand {
            Objects.requireNonNull(modelContext, "modelContext");
            Objects.requireNonNull(currentState, "currentState");
            Objects.requireNonNull(context, "context");
            if (currentInput == null || currentInput.isBlank()) {
                throw new IllegalArgumentException("currentInput must not be blank");
            }
            currentInput = currentInput.trim();
            if (!currentState.project().id().equals(context.project().id())) {
                throw new IllegalArgumentException("Analysis context must belong to current project");
            }
        }
    }

    public record AnalysisResult(
            RequirementState state,
            List<RequirementItem> appliedRequirements,
            List<ClarificationQuestion> selectedQuestions,
            List<com.prompt2prd.analysis.domain.RequirementConflict> createdConflicts,
            List<String> missingInformation,
            String suggestedProjectName) {
        public AnalysisResult {
            Objects.requireNonNull(state, "state");
            appliedRequirements = List.copyOf(appliedRequirements);
            selectedQuestions = List.copyOf(selectedQuestions);
            createdConflicts = List.copyOf(createdConflicts);
            missingInformation = List.copyOf(missingInformation);
            if (suggestedProjectName == null || suggestedProjectName.isBlank()) {
                throw new IllegalArgumentException("suggestedProjectName must not be blank");
            }
        }
    }
}
