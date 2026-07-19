package com.prompt2prd.analysis.application;

import com.prompt2prd.analysis.domain.ClarificationOption;
import com.prompt2prd.analysis.domain.ClarificationQuestion;
import com.prompt2prd.analysis.domain.CompletenessCalculator;
import com.prompt2prd.analysis.domain.CompletenessInput;
import com.prompt2prd.analysis.domain.PrdCoverageArea;
import com.prompt2prd.analysis.domain.QuestionInputType;
import com.prompt2prd.analysis.domain.QuestionSelector;
import com.prompt2prd.analysis.domain.QuestionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

@Component
public final class RequirementAnalyzer implements AnalysisEngine {

    private static final Logger log = LoggerFactory.getLogger(RequirementAnalyzer.class);
    private static final int SUFFICIENT_COMPLETENESS_THRESHOLD = 80;

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
        String promptText = prompt(command.context(), command.currentInput());
        int promptChars = promptText.length();
        int requirementCount = command.currentState().requirements().size();
        log.info("analysis_start promptChars={} requirementCount={} projectId={}",
                promptChars, requirementCount, command.currentState().project().id());
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
            if (shouldContinueClarification(selected, questions, completeness)) {
                UUID fallbackBatchId = idGenerator.get();
                selected = questionSelector.select(fallbackQuestionCandidates(
                        merged.state(), questions, fallbackBatchId, now));
                questions.addAll(selected);
                completeness = completenessCalculator.calculate(new CompletenessInput(
                        merged.state().requirements(), questions, merged.state().conflicts(),
                        java.util.Set.of(), java.util.Map.of()));
            }
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

    private boolean shouldContinueClarification(
            List<ClarificationQuestion> selected,
            List<ClarificationQuestion> questions,
            com.prompt2prd.analysis.domain.CompletenessScore completeness) {
        return selected.isEmpty()
                && completeness.total() < SUFFICIENT_COMPLETENESS_THRESHOLD
                && questions.stream().noneMatch(question -> question.status() == QuestionStatus.PENDING);
    }

    private List<com.prompt2prd.analysis.domain.QuestionCandidate> fallbackQuestionCandidates(
            RequirementState current,
            List<ClarificationQuestion> existingQuestions,
            UUID batchId,
            Instant now) {
        String language = current.project().language();
        return Arrays.stream(PrdCoverageArea.values())
                .filter(area -> existingQuestions.stream().noneMatch(question ->
                        question.targetField().startsWith(area.key() + ".")
                                || question.semanticKey().startsWith(area.key())))
                .limit(5)
                .map(area -> fallbackQuestionCandidate(current, area, batchId, now, language))
                .toList();
    }

    private com.prompt2prd.analysis.domain.QuestionCandidate fallbackQuestionCandidate(
            RequirementState current,
            PrdCoverageArea area,
            UUID batchId,
            Instant now,
            String language) {
        ClarificationQuestion question = new ClarificationQuestion(
                idGenerator.get(), current.project().id(), batchId, 0,
                fallbackQuestionText(area, language),
                fallbackQuestionReason(area, language),
                area.dimension(), area.key() + ".fallback",
                area.key() + "-fallback", QuestionInputType.SINGLE_SELECT,
                fallbackOptions(area, language), List.of(area.key()), 0, QuestionStatus.PENDING, now, now);
        return new com.prompt2prd.analysis.domain.QuestionCandidate(question, 4, 5, 3, 3);
    }

    private List<ClarificationOption> fallbackOptions(PrdCoverageArea area, String language) {
        boolean zh = language != null && language.toLowerCase(Locale.ROOT).startsWith("zh");
        String[][] values = switch (area) {
            case PRODUCT_CONTEXT -> zh
                    ? new String[][]{{"先明确产品目标", "帮助 PRD 说明为什么要做以及成功信号"}, {"先明确非目标", "避免后续开发范围发散"}}
                    : new String[][]{{"Clarify the product goal", "Defines why the product exists and the success signal"}, {"Clarify non-goals", "Keeps implementation scope focused"}};
            case ROLES_SCENARIOS -> zh
                    ? new String[][]{{"先按单一核心角色设计", "适合快速完成 MVP 主流程"}, {"需要多角色协作", "会补充权限、场景和交接规则"}}
                    : new String[][]{{"Start with one core role", "Fits an MVP flow"}, {"Use multiple collaborating roles", "Adds permissions and handoff rules"}};
            case FEATURE_SCOPE_PRIORITIES -> zh
                    ? new String[][]{{"只做最小功能闭环", "优先交付核心流程"}, {"先列完整功能范围", "便于后续拆分优先级"}}
                    : new String[][]{{"Build the minimum feature loop", "Prioritizes the core delivery flow"}, {"List the full feature scope", "Helps split later priorities"}};
            case CORE_BUSINESS_FLOW, USER_STORIES -> zh
                    ? new String[][]{{"按主流程继续确认", "补齐从开始到完成的关键步骤"}, {"按用户价值继续确认", "补齐角色目标和使用结果"}}
                    : new String[][]{{"Continue with the main flow", "Completes the steps from start to finish"}, {"Continue with user value", "Completes role goals and outcomes"}};
            case RULES_EXCEPTIONS -> zh
                    ? new String[][]{{"先确认正常业务规则", "补齐限制、状态和责任"}, {"先确认异常处理", "补齐失败、取消和争议场景"}}
                    : new String[][]{{"Clarify normal business rules", "Completes limits, states, and responsibility"}, {"Clarify exception handling", "Covers failure, cancellation, and dispute scenarios"}};
            case PAGES_STATES -> zh
                    ? new String[][]{{"只列核心页面", "适合先产出开发入口"}, {"补充页面状态和操作", "让 PRD 更利于实现和验收"}}
                    : new String[][]{{"List core pages only", "Good enough to start implementation"}, {"Include page states and actions", "Improves implementation and testing clarity"}};
            case DATA_ENTITIES_FIELDS -> zh
                    ? new String[][]{{"只描述核心数据实体", "先明确对象关系"}, {"补充关键字段和状态", "减少后续接口和存储歧义"}}
                    : new String[][]{{"Describe core entities only", "Clarifies object relationships"}, {"Include key fields and states", "Reduces API and storage ambiguity"}};
            case ACCEPTANCE_CRITERIA -> zh
                    ? new String[][]{{"先写业务验收规则", "便于确认功能是否达标"}, {"补充 Given/When/Then", "便于后续自动化测试"}}
                    : new String[][]{{"Use business acceptance rules", "Clarifies whether features are done"}, {"Use Given/When/Then scenarios", "Helps later automated testing"}};
            case NON_FUNCTIONAL -> zh
                    ? new String[][]{{"优先确认隐私和安全", "减少敏感信息和权限风险"}, {"优先确认性能和兼容性", "明确运行环境和响应要求"}}
                    : new String[][]{{"Prioritize privacy and security", "Reduces sensitive data and permission risk"}, {"Prioritize performance and compatibility", "Defines runtime and response expectations"}};
            case ASSUMPTIONS_RISKS_OPEN_ITEMS -> zh
                    ? new String[][]{{"把不确定内容列为待确认", "允许继续追问而不虚构结论"}, {"把主要风险转成问题", "优先解决会影响开发的决策"}}
                    : new String[][]{{"Keep unknowns as open items", "Allows follow-up without inventing conclusions"}, {"Turn major risks into questions", "Prioritizes decisions that affect implementation"}};
        };
        return List.of(
                new ClarificationOption(idGenerator.get(), values[0][0], values[0][1], true),
                new ClarificationOption(idGenerator.get(), values[1][0], values[1][1], false));
    }

    private String fallbackQuestionText(PrdCoverageArea area, String language) {
        boolean zh = language != null && language.toLowerCase(Locale.ROOT).startsWith("zh");
        return zh
                ? "关于「" + area.label() + "」，下一步你希望先确认哪类信息？"
                : "For " + area.label() + ", what should we clarify next?";
    }

    private String fallbackQuestionReason(PrdCoverageArea area, String language) {
        boolean zh = language != null && language.toLowerCase(Locale.ROOT).startsWith("zh");
        return zh
                ? "当前信息还没有达到生成 PRD 的最低完整度，需要继续补齐这一块。"
                : "The current information is below the minimum PRD completeness threshold, so this area needs another clarification step.";
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
        if (inputType == QuestionInputType.SINGLE_SELECT || inputType == QuestionInputType.MULTI_SELECT
                || inputType == QuestionInputType.AI_RECOMMENDED) {
            if (options.size() < 2) {
                throw new AnalysisRetryableException("Selectable questions require at least two options");
            }
        }
        ClarificationQuestion question = new ClarificationQuestion(
                idGenerator.get(), current.project().id(), batchId, 0,
                raw.text(), raw.reason(), dimension, raw.targetField(), raw.semanticKey(),
                inputType, options, safeCoverageCategories(raw), 0, QuestionStatus.PENDING, now, now);
        return new com.prompt2prd.analysis.domain.QuestionCandidate(
                question, raw.businessImpact(), raw.informationGap(),
                raw.dependencyCount(), raw.risk());
    }

    private List<String> safeCoverageCategories(AnalysisModelOutput.QuestionCandidate raw) {
        try {
            return raw.coverageCategories() != null ? raw.coverageCategories() : List.of();
        } catch (Exception ignored) {
            return List.of();
        }
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
        StringBuilder sb = new StringBuilder();
        sb.append("Project=").append(context.project().name())
                .append("\nLanguage=").append(context.language())
                .append("\nCurrent input=").append(truncate(currentInput, 2000))
                .append("\nCurrent requirements=").append(RequirementFormatter.compactSummary(context.currentRequirements()))
                .append("\nLocked requirements=").append(RequirementFormatter.compactSummary(context.lockedRequirements()));

        // Compact answer history — no UUIDs, timestamps, or batch IDs
        if (!context.answerHistory().isEmpty()) {
            sb.append("\nAnswer history (recent first):\n");
            List<AnalysisContextBuilder.QuestionAnswerTurn> recent = context.answerHistory().stream()
                    .sorted(java.util.Comparator.comparing(AnalysisContextBuilder.QuestionAnswerTurn::answeredAt).reversed())
                    .limit(60) // ~6 rounds × 10 questions
                    .toList();
            for (var turn : recent) {
                sb.append("Q: ").append(truncate(turn.question(), 200))
                        .append("\nA: ").append(truncate(turn.answer(), 200))
                        .append("\n\n");
            }
        }

        // Compact missing information
        if (!context.missingInformation().isEmpty()) {
            sb.append("\nMissing information:\n");
            for (String gap : context.missingInformation()) {
                sb.append("- ").append(truncate(gap, 200)).append("\n");
            }
        }

        sb.append("\nPRD coverage checklist=\n").append(PrdCoverageArea.promptChecklist())
                .append("\nOutput schema=").append(context.outputSchema());
        return sb.toString();
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) return value;
        return value.substring(0, maxLength) + "...";
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
