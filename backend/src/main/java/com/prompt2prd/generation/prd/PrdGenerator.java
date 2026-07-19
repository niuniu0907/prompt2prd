package com.prompt2prd.generation.prd;

import com.prompt2prd.analysis.application.RequirementFormatter;
import com.prompt2prd.analysis.domain.ConflictStatus;
import com.prompt2prd.analysis.domain.ClarificationAnswer;
import com.prompt2prd.analysis.domain.ClarificationQuestion;
import com.prompt2prd.analysis.domain.RequirementItem;
import com.prompt2prd.analysis.domain.RequirementState;
import com.prompt2prd.analysis.domain.RequirementStatus;
import com.prompt2prd.analysis.domain.RequirementType;
import com.prompt2prd.model.application.ModelCallContext;
import com.prompt2prd.model.application.ModelGateway;
import com.prompt2prd.model.application.ModelMessage;
import com.prompt2prd.model.application.TextModelChunk;
import com.prompt2prd.model.application.TextModelRequest;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Builds deterministic PRD plans and delegates only section prose to the model. */
@Component
public final class PrdGenerator {

    private final ModelGateway modelGateway;

    public PrdGenerator(ModelGateway modelGateway) {
        this.modelGateway = Objects.requireNonNull(modelGateway, "modelGateway");
    }

    public PrdGenerationPlan plan(RequirementState state, List<String> requestedMissing, String sectionKey) {
        Objects.requireNonNull(state, "state");
        List<RequirementItem> prdRequirements = state.requirements().stream()
                .filter(item -> item.type() != RequirementType.TECHNICAL_CONSTRAINT)
                .toList();

        List<String> missing = new ArrayList<>();
        if (requestedMissing != null) requestedMissing.stream().filter(Objects::nonNull)
                .map(String::trim).filter(value -> !value.isEmpty()).forEach(missing::add);
        boolean openCoreConflict = state.conflicts().stream()
                .anyMatch(conflict -> conflict.core() && conflict.status() == ConflictStatus.OPEN);
        if (openCoreConflict) missing.add("存在未解决的核心冲突，相关章节必须标记为待确认或冲突。");
        state.requirements().stream()
                .filter(item -> item.status() != RequirementStatus.CONFIRMED)
                .filter(item -> item.type() != RequirementType.TECHNICAL_CONSTRAINT)
                .map(item -> statusLabel(item.status()) + "：" + item.title())
                .forEach(missing::add);
        List<String> missingItems = missing.stream().distinct().toList();

        PrdGenerationPlan.Mode mode = missingItems.isEmpty()
                ? PrdGenerationPlan.Mode.FINAL : PrdGenerationPlan.Mode.DRAFT;
        List<PrdDefinition.Section> definitions = sectionKey == null
                ? PrdDefinition.sections()
                : List.of(PrdDefinition.requireSection(sectionKey));
        List<PrdGenerationPlan.SectionPlan> sections = definitions.stream()
                .map(definition -> new PrdGenerationPlan.SectionPlan(
                        definition, prompt(state, definition, mode, missingItems, prdRequirements)))
                .toList();
        return new PrdGenerationPlan(mode, sections, missingItems, prdRequirements, null);
    }

    public Flux<TextModelChunk> streamSection(
            ModelCallContext context,
            PrdGenerationPlan.SectionPlan section) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(section, "section");
        TextModelRequest request = new TextModelRequest(context, List.of(
                new ModelMessage(ModelMessage.Role.SYSTEM,
                        "Generate one implementation-ready PRD section. Never modify requirement state. "
                                + "Use stable IDs and only the directive words MUST, SHOULD, and MUST NOT. "
                                + "Never present pending content as confirmed. "
                                + "Do not invent login, payment, permission, database, deployment, API, architecture, "
                                + "or flowchart details that the user did not provide. "
                                + "Do not generate technical architecture, interface design, database table design, "
                                + "flowchart diagrams, deployment topology, or system design documents inside the PRD."),
                new ModelMessage(ModelMessage.Role.USER, section.prompt())));
        return modelGateway.streamText(request);
    }

    private String prompt(
            RequirementState state,
            PrdDefinition.Section definition,
            PrdGenerationPlan.Mode mode,
            List<String> missing,
            List<RequirementItem> currentRequirements) {
        // Filter requirements to only those relevant to this section
        List<RequirementItem> sectionReqs = currentRequirements.stream()
                .filter(item -> isRelevantToSection(item.type(), definition.key()))
                .toList();
        List<String> sectionEvidence = clarificationEvidence(state).stream()
                .filter(evidence -> isEvidenceRelevantToSection(evidence, definition.key()))
                .toList();

        return "Project=" + state.project().name()
                + "\nLanguage=" + state.project().language()
                + "\nDocument mode=" + mode
                + "\nSection=" + definition.key().wireName() + " | " + definition.title()
                + "\nCompleteness=" + state.completeness().total()
                + "\nRelevant requirements=" + RequirementFormatter.compactSummary(sectionReqs)
                + "\nAll requirements (for cross-reference)=" + RequirementFormatter.compactSummary(currentRequirements)
                + "\nRelevant clarification answers=" + sectionEvidence
                + "\nMissing or pending items=" + missing
                + "\nRules: generate only from current information; "
                + "confirmed content is written normally; AI_INFERENCE or AI_RECOMMENDATION content must be marked as AI推断，待确认; "
                + "UNANALYZED or missing sections keep the chapter and write 状态：待分析; "
                + "PENDING candidate sections write 状态：待确认; SKIPPED sections write 用户暂未提供; "
                + "do not fill a chapter just to make it look complete; "
                + "every requirement/rule/page/acceptance uses a stable ID when enough information exists; "
                + "every core feature links to a user story, business rule, and acceptance criterion; "
                + "implementation directives use MUST, SHOULD, or MUST NOT; "
                + "business processes must be text summaries unless the user generated an optional flowchart attachment; "
                + "draft mode must visibly label unresolved facts; "
                + "technical architecture, API design, database table design, and flowcharts belong to optional attachments.";
    }

    /** Returns true if the requirement type is directly relevant to the given section. */
    private static boolean isRelevantToSection(RequirementType type, PrdDefinition.SectionKey key) {
        return switch (key) {
            case PRODUCT_BACKGROUND_GOALS, PRODUCT_SCOPE -> type == RequirementType.PRODUCT_GOAL
                    || type == RequirementType.RISK_OPEN_ITEM;
            case TARGET_USERS_SCENARIOS -> type == RequirementType.ROLE
                    || type == RequirementType.USER_STORY;
            case FEATURE_MODULES_PRIORITY -> type == RequirementType.FEATURE;
            case BUSINESS_RULES -> type == RequirementType.BUSINESS_RULE;
            case EXCEPTION_SCENARIOS -> type == RequirementType.EXCEPTION_SCENARIO;
            case PAGE_LIST_STATES -> type == RequirementType.PAGE;
            case DATA_REQUIREMENTS -> type == RequirementType.DATA_MODEL;
            case ACCEPTANCE_CRITERIA -> type == RequirementType.ACCEPTANCE_CRITERION
                    || type == RequirementType.BUSINESS_RULE;
            case NON_FUNCTIONAL_REQUIREMENTS -> type == RequirementType.NON_FUNCTIONAL_REQUIREMENT;
            case RISKS_ASSUMPTIONS_OPEN_ITEMS -> type == RequirementType.ASSUMPTION
                    || type == RequirementType.RISK_OPEN_ITEM
                    || type == RequirementType.MISSING_INFORMATION;
            case USER_STORIES -> type == RequirementType.USER_STORY
                    || type == RequirementType.ROLE;
        };
    }

    private static boolean isEvidenceRelevantToSection(String evidence, PrdDefinition.SectionKey key) {
        String lower = evidence.toLowerCase(java.util.Locale.ROOT);
        return switch (key) {
            case PRODUCT_BACKGROUND_GOALS, PRODUCT_SCOPE ->
                lower.contains("目标") || lower.contains("goal")
                    || lower.contains("产品") || lower.contains("product");
            case TARGET_USERS_SCENARIOS, USER_STORIES ->
                lower.contains("角色") || lower.contains("role")
                    || lower.contains("用户") || lower.contains("user") || lower.contains("故事");
            case FEATURE_MODULES_PRIORITY ->
                lower.contains("功能") || lower.contains("feature")
                    || lower.contains("优先级") || lower.contains("priority");
            case BUSINESS_RULES ->
                lower.contains("规则") || lower.contains("rule") || lower.contains("限制");
            case EXCEPTION_SCENARIOS ->
                lower.contains("异常") || lower.contains("exception");
            case PAGE_LIST_STATES ->
                lower.contains("页面") || lower.contains("page")
                    || lower.contains("状态") || lower.contains("state");
            case DATA_REQUIREMENTS ->
                lower.contains("数据") || lower.contains("data")
                    || lower.contains("字段") || lower.contains("field");
            case ACCEPTANCE_CRITERIA ->
                lower.contains("验收") || lower.contains("acceptance")
                    || lower.contains("测试") || lower.contains("test");
            case NON_FUNCTIONAL_REQUIREMENTS ->
                lower.contains("性能") || lower.contains("performance")
                    || lower.contains("安全") || lower.contains("security");
            case RISKS_ASSUMPTIONS_OPEN_ITEMS ->
                lower.contains("假设") || lower.contains("assumption")
                    || lower.contains("风险") || lower.contains("risk") || lower.contains("待定");
        };
    }

    private List<String> clarificationEvidence(RequirementState state) {
        Map<java.util.UUID, ClarificationQuestion> questionsById = state.questions().stream()
                .collect(java.util.stream.Collectors.toMap(
                        ClarificationQuestion::id,
                        question -> question,
                        (left, right) -> left));
        return state.answers().stream()
                .map(answer -> answerEvidence(answer, questionsById.get(answer.questionId())))
                .filter(value -> !value.isBlank())
                .toList();
    }

    private String answerEvidence(ClarificationAnswer answer, ClarificationQuestion question) {
        if (question == null) return "";
        List<String> selectedLabels = question.options().stream()
                .filter(option -> answer.selectedOptionIds().contains(option.id()))
                .map(option -> option.label())
                .toList();
        List<String> parts = new ArrayList<>();
        if (answer.skipped()) parts.add("用户跳过");
        if (!selectedLabels.isEmpty()) parts.add(String.join("、", selectedLabels));
        if (answer.customAnswer() != null) parts.add(answer.customAnswer());
        if (answer.note() != null) parts.add("备注：" + answer.note());
        if (parts.isEmpty()) return "";
        return question.text() + " -> " + String.join("；", parts);
    }

    private String statusLabel(RequirementStatus status) {
        return switch (status) {
            case UNANALYZED -> "待分析";
            case INFERRED -> "AI推断待确认";
            case PENDING -> "待确认";
            case CONFIRMED -> "已确认";
            case SKIPPED -> "用户暂未提供";
            case NOT_APPLICABLE -> "不适用";
            case CONFLICTED -> "存在冲突";
        };
    }
}
