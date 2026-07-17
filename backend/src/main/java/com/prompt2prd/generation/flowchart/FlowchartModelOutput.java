package com.prompt2prd.generation.flowchart;

import java.util.List;

/** Raw structured model output. Diagram-level validation is intentionally independent. */
public record FlowchartModelOutput(
        DiagramCandidate mainFlow,
        List<DiagramCandidate> exceptionFlows) {

    public FlowchartModelOutput {
        exceptionFlows = exceptionFlows == null ? List.of() : List.copyOf(exceptionFlows);
    }

    public record DiagramCandidate(
            String key,
            String title,
            String mermaid,
            List<String> sourceRequirementIds,
            String errorCode) {
        public DiagramCandidate {
            sourceRequirementIds = sourceRequirementIds == null
                    ? List.of()
                    : List.copyOf(sourceRequirementIds);
        }
    }
}
