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

    private static final String ARCHITECTURE_KIND = "ARCHITECTURE_CANDIDATE";
    private final ModelGateway modelGateway;

    public PrdGenerator(ModelGateway modelGateway) {
        this.modelGateway = Objects.requireNonNull(modelGateway, "modelGateway");
    }

    public PrdGenerationPlan plan(RequirementState state, List<String> requestedMissing, String sectionKey) {
        Objects.requireNonNull(state, "state");
        List<RequirementItem> confirmed = state.requirements().stream()
                .filter(item -> item.status() == RequirementStatus.CONFIRMED)
                .toList();
        List<RequirementItem> architectures = confirmed.stream()
                .filter(this::isArchitecture)
                .toList();
        RequirementItem selectedArchitecture = architectures.size() == 1 ? architectures.getFirst() : null;

        List<String> missing = new ArrayList<>();
        if (requestedMissing != null) requestedMissing.stream().filter(Objects::nonNull)
                .map(String::trim).filter(value -> !value.isEmpty()).forEach(missing::add);
        if (state.completeness().total() < 80) {
            missing.add("需求完整度为 " + state.completeness().total() + "，最终文档要求至少 80。");
        }
        if (architectures.isEmpty()) missing.add("尚未确认主架构。");
        if (architectures.size() > 1) missing.add("存在多个已确认主架构，必须只保留一个。");
        boolean openCoreConflict = state.conflicts().stream()
                .anyMatch(conflict -> conflict.core() && conflict.status() == ConflictStatus.OPEN);
        if (openCoreConflict) missing.add("存在未解决的核心冲突。");
        state.requirements().stream()
                .filter(item -> item.status() != RequirementStatus.CONFIRMED)
                .filter(item -> item.type() == RequirementType.MISSING_INFORMATION
                        || item.type() == RequirementType.ASSUMPTION)
                .map(item -> "待确认：" + item.title())
                .forEach(missing::add);
        List<String> missingItems = missing.stream().distinct().toList();

        PrdGenerationPlan.Mode mode = missingItems.isEmpty()
                ? PrdGenerationPlan.Mode.FINAL : PrdGenerationPlan.Mode.DRAFT;
        List<PrdDefinition.Section> definitions = sectionKey == null
                ? PrdDefinition.sections()
                : List.of(PrdDefinition.requireSection(sectionKey));
        List<PrdGenerationPlan.SectionPlan> sections = definitions.stream()
                .map(definition -> new PrdGenerationPlan.SectionPlan(
                        definition, prompt(state, definition, mode, missingItems, confirmed, selectedArchitecture)))
                .toList();
        return new PrdGenerationPlan(mode, sections, missingItems, confirmed, selectedArchitecture);
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
                                + "Never present pending content as confirmed."),
                new ModelMessage(ModelMessage.Role.USER, section.prompt())));
        return modelGateway.streamText(request);
    }

    private boolean isArchitecture(RequirementItem item) {
        return item.type() == RequirementType.TECHNICAL_CONSTRAINT
                && ARCHITECTURE_KIND.equals(item.metadata().get("kind"));
    }

    private String prompt(
            RequirementState state,
            PrdDefinition.Section definition,
            PrdGenerationPlan.Mode mode,
            List<String> missing,
            List<RequirementItem> confirmed,
            RequirementItem selectedArchitecture) {
        return "Project=" + state.project().name()
                + "\nLanguage=" + state.project().language()
                + "\nDocument mode=" + mode
                + "\nSection=" + definition.key().wireName() + " | " + definition.title()
                + "\nCompleteness=" + state.completeness().total()
                + "\nConfirmed requirements=" + confirmed
                + "\nSelected architecture=" + (selectedArchitecture == null ? "UNCONFIRMED" : selectedArchitecture)
                + "\nMissing or pending items=" + missing
                + "\nRules: every requirement/rule/API/page/acceptance/phase uses a stable ID; "
                + "every core feature links to a user story, business rule, and acceptance criterion; "
                + "implementation directives use MUST, SHOULD, or MUST NOT; "
                + "draft mode must visibly label unresolved facts and must not invent final architecture details.";
    }
}
