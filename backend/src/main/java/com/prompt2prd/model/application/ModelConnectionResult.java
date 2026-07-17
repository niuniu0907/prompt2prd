package com.prompt2prd.model.application;

import java.time.Duration;
import java.util.Objects;

/** Successful connection-test result; failures use {@link ModelGatewayException}. */
public record ModelConnectionResult(String model, Duration latency) {

    public ModelConnectionResult {
        model = ModelContractValidation.requireText(model, "model");
        Objects.requireNonNull(latency, "latency");
        if (latency.isNegative()) {
            throw new IllegalArgumentException("latency must not be negative");
        }
    }
}
