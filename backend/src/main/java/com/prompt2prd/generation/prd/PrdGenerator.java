package com.prompt2prd.generation.prd;

import com.prompt2prd.analysis.domain.ConflictStatus;
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
        return "Project=" + state.project().name()
                + "\nLanguage=" + state.project().language()
                + "\nDocument mode=" + mode
                + "\nSection=" + definition.key().wireName() + " | " + definition.title()
                + "\nCompleteness=" + state.completeness().total()
                + "\nCurrent structured requirements=" + currentRequirements
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
