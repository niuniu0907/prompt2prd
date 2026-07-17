package com.prompt2prd.generation.flowchart;

import com.prompt2prd.analysis.domain.RequirementState;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record FlowchartRequest(
        @NotNull @Valid RequirementState state,
        String targetKey,
        @NotNull @Valid FlowchartModelSettings modelSettings) {

    public FlowchartRequest {
        targetKey = targetKey == null || targetKey.isBlank() ? null : targetKey.trim();
    }
}
