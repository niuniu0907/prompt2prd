package com.prompt2prd.model.application;

import java.util.Objects;

/** Complete typed model output; partial structured data is never represented. */
public record StructuredModelResult<T>(T value, String model) {

    public StructuredModelResult {
        Objects.requireNonNull(value, "value");
        model = ModelContractValidation.requireText(model, "model");
    }
}
