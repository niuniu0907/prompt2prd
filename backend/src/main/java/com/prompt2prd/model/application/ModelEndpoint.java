package com.prompt2prd.model.application;

import java.net.URI;
import java.util.Map;
import java.util.Objects;

/** Runtime-only endpoint configuration for one model invocation. */
public record ModelEndpoint(
        URI baseUrl,
        String model,
        String apiKey,
        Map<String, Object> parameters
) {

    public ModelEndpoint {
        Objects.requireNonNull(baseUrl, "baseUrl");
        model = ModelContractValidation.requireText(model, "model");
        apiKey = Objects.requireNonNull(apiKey, "apiKey");
        parameters = Map.copyOf(Objects.requireNonNull(parameters, "parameters"));
    }

    /** Never expose credentials or parameter values through logs and diagnostics. */
    @Override
    public String toString() {
        return "ModelEndpoint[model=" + model
                + ", apiKey=[REDACTED], parameterNames=" + parameters.keySet() + "]";
    }
}
