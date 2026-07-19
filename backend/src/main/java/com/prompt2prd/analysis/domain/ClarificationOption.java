package com.prompt2prd.analysis.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ClarificationOption(
        @NotNull UUID id,
        @NotBlank String label,
        @NotBlank String impact,
        boolean recommended) {

    public ClarificationOption {
        if (label != null) {
            label = label.trim();
        }
        if (impact != null) {
            impact = impact.trim();
        }
    }
}
