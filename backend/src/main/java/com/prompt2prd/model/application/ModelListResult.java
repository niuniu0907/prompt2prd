package com.prompt2prd.model.application;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/** Successful model-list result; failures use {@link ModelGatewayException}. */
public record ModelListResult(List<AvailableModel> models, Duration latency) {

    public ModelListResult {
        models = List.copyOf(Objects.requireNonNull(models, "models"));
        Objects.requireNonNull(latency, "latency");
        if (latency.isNegative()) {
            throw new IllegalArgumentException("latency must not be negative");
        }
    }
}
