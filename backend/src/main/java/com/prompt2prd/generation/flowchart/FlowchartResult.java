package com.prompt2prd.generation.flowchart;

import java.util.List;

public record FlowchartResult(
        DiagramResult mainFlow,
        List<DiagramResult> exceptionFlows,
        List<String> missingInformation) {

    public FlowchartResult {
        exceptionFlows = List.copyOf(exceptionFlows);
        missingInformation = List.copyOf(missingInformation);
    }

    public record DiagramResult(
            String key,
            Type type,
            String title,
            String mermaid,
            Status status,
            String errorCode,
            List<String> sourceRequirementIds) {

        public DiagramResult {
            sourceRequirementIds = List.copyOf(sourceRequirementIds);
        }
    }

    public enum Type { MAIN, EXCEPTION }
    public enum Status { GENERATED, FAILED }
}
