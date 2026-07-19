package com.prompt2prd.generation.flowchart;

import com.prompt2prd.analysis.domain.RequirementItem;
import com.prompt2prd.analysis.domain.RequirementStatus;
import com.prompt2prd.analysis.domain.RequirementType;
import com.prompt2prd.model.application.ModelCallContext;
import com.prompt2prd.model.application.ModelGateway;
import com.prompt2prd.model.application.ModelMessage;
import com.prompt2prd.model.application.StructuredModelRequest;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Component
public final class FlowchartGenerator {

    static final String OUTPUT_SCHEMA = """
            {"type":"object","required":["mainFlow","exceptionFlows"],
             "properties":{"mainFlow":{"type":["object","null"]},
             "exceptionFlows":{"type":"array"}}}
            """;

    private final ModelGateway modelGateway;

    public FlowchartGenerator(ModelGateway modelGateway) {
        this.modelGateway = Objects.requireNonNull(modelGateway, "modelGateway");
    }

    public Mono<FlowchartResult> generate(Command command) {
        Objects.requireNonNull(command, "command");
        List<RequirementItem> confirmed = command.state().requirements().stream()
                .filter(item -> item.status() == RequirementStatus.CONFIRMED)
                .toList();
        if (confirmed.isEmpty()) {
            return Mono.just(new FlowchartResult(null, List.of(),
                    List.of("请先确认至少一条核心业务流程或业务规则。")));
        }

        Set<UUID> confirmedExceptionIds = confirmed.stream()
                .filter(item -> item.type() == RequirementType.EXCEPTION_SCENARIO)
                .map(RequirementItem::id)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        StructuredModelRequest<FlowchartModelOutput> request = new StructuredModelRequest<>(
                command.modelContext(),
                List.of(
                        new ModelMessage(ModelMessage.Role.SYSTEM,
                                "Generate Mermaid flowcharts only from confirmed facts. "
                                        + "Do not invent exception responsibility, compensation, or state rules. "
                                        + "Return every diagram independently so one failure does not hide siblings."),
                        new ModelMessage(ModelMessage.Role.USER,
                                prompt(command, confirmed, confirmedExceptionIds))),
                FlowchartModelOutput.class,
                OUTPUT_SCHEMA);
        return modelGateway.generateStructured(request)
                .map(result -> convert(result.value(), confirmedExceptionIds, command.targetKey()));
    }

    private FlowchartResult convert(FlowchartModelOutput output, Set<UUID> confirmedExceptionIds,
                                    String targetKey) {
        if (output == null) {
            return new FlowchartResult(failed("main", FlowchartResult.Type.MAIN,
                    "主业务流程", "EMPTY_MODEL_OUTPUT", List.of()), List.of(), List.of());
        }
        FlowchartResult.DiagramResult main = convertDiagram(
                output.mainFlow(), FlowchartResult.Type.MAIN, "main", Set.of());
        List<FlowchartResult.DiagramResult> exceptions = new ArrayList<>();
        for (FlowchartModelOutput.DiagramCandidate candidate : output.exceptionFlows()) {
            FlowchartResult.DiagramResult converted = convertDiagram(
                    candidate, FlowchartResult.Type.EXCEPTION, null, confirmedExceptionIds);
            if (converted != null) exceptions.add(converted);
        }
        List<String> missing = confirmedExceptionIds.isEmpty()
                ? List.of("尚无已确认的异常场景，请补充触发条件、责任角色、处理动作和结束状态。")
                : List.of();
        if (confirmedExceptionIds.isEmpty()) exceptions = List.of();
        if (targetKey != null) {
            if (targetKey.equals("main")) {
                exceptions = List.of();
            } else {
                main = null;
                exceptions = exceptions.stream().filter(item -> item.key().equals(targetKey)).toList();
            }
        }
        return new FlowchartResult(main, exceptions, missing);
    }

    private FlowchartResult.DiagramResult convertDiagram(
            FlowchartModelOutput.DiagramCandidate candidate,
            FlowchartResult.Type type,
            String forcedKey,
            Set<UUID> allowedSources) {
        if (candidate == null) {
            return type == FlowchartResult.Type.MAIN
                    ? failed("main", type, "主业务流程", "MISSING_MAIN_FLOW", List.of())
                    : null;
        }
        List<String> sourceIds = normalizeSourceIds(candidate.sourceRequirementIds());
        if (type == FlowchartResult.Type.EXCEPTION) {
            if (sourceIds.isEmpty() || sourceIds.stream().map(this::parseUuid)
                    .anyMatch(id -> id == null || !allowedSources.contains(id))) {
                return failed(normalizeKey(candidate.key(), "exception-invalid-source"), type,
                        normalizeText(candidate.title(), "异常流程"),
                        "UNCONFIRMED_EXCEPTION_SOURCE", sourceIds);
            }
        }
        String key = forcedKey == null
                ? normalizeKey(candidate.key(), "exception-" + sourceIds.getFirst())
                : forcedKey;
        String title = normalizeText(candidate.title(), type == FlowchartResult.Type.MAIN ? "主业务流程" : "异常流程");
        if (candidate.errorCode() != null && !candidate.errorCode().isBlank()) {
            return failed(key, type, title, candidate.errorCode().trim(), sourceIds);
        }
        if (candidate.mermaid() == null || candidate.mermaid().isBlank()) {
            return failed(key, type, title, "EMPTY_MERMAID", sourceIds);
        }
        return new FlowchartResult.DiagramResult(key, type, title, candidate.mermaid().trim(),
                FlowchartResult.Status.GENERATED, null, sourceIds);
    }

    private FlowchartResult.DiagramResult failed(String key, FlowchartResult.Type type,
                                                  String title, String errorCode,
                                                  List<String> sourceIds) {
        return new FlowchartResult.DiagramResult(key, type, title, "",
                FlowchartResult.Status.FAILED, errorCode, sourceIds);
    }

    private List<String> normalizeSourceIds(List<String> sourceIds) {
        return sourceIds.stream().filter(Objects::nonNull).map(String::trim)
                .filter(value -> !value.isEmpty()).distinct().toList();
    }

    private UUID parseUuid(String value) {
        try { return UUID.fromString(value); } catch (RuntimeException ignored) { return null; }
    }

    private String normalizeKey(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String normalizeText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String prompt(Command command, List<RequirementItem> confirmed,
                          Set<UUID> confirmedExceptionIds) {
        return "Project=" + command.state().project().name()
                + "\nLanguage=" + command.state().project().language()
                + "\nTarget diagram key=" + (command.targetKey() == null ? "ALL" : command.targetKey())
                + "\nConfirmed requirements=" + confirmed
                + "\nAllowed exception source IDs=" + confirmedExceptionIds
                + "\nMain flow must contain start, decisions, state transitions, and end."
                + "\nException flows must cite one or more allowed exception source IDs.";
    }

    public record Command(
            ModelCallContext modelContext,
            com.prompt2prd.analysis.domain.RequirementState state,
            String targetKey) {
        public Command {
            Objects.requireNonNull(modelContext, "modelContext");
            Objects.requireNonNull(state, "state");
            targetKey = targetKey == null || targetKey.isBlank() ? null : targetKey.trim();
        }
    }
}
