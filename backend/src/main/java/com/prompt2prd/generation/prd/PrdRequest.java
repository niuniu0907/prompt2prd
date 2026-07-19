package com.prompt2prd.generation.prd;

import com.prompt2prd.analysis.domain.RequirementState;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record PrdRequest(
        @NotNull @Valid RequirementState state,
        @NotNull List<String> missingInformation,
        @NotNull @Valid PrdModelSettings modelSettings) {

    public PrdRequest {
        missingInformation = missingInformation == null ? List.of() : missingInformation.stream()
                .filter(java.util.Objects::nonNull).map(String::trim).filter(value -> !value.isEmpty())
                .distinct().toList();
    }
}
