package com.prompt2prd.model.application;

import java.util.List;
import java.util.Objects;

/** Request for one fully aggregated and typed model response. */
public record StructuredModelRequest<T>(
        ModelCallContext context,
        List<ModelMessage> messages,
        Class<T> responseType,
        String outputSchema
) {

    public StructuredModelRequest {
        Objects.requireNonNull(context, "context");
        messages = ModelContractValidation.requireMessages(messages);
        Objects.requireNonNull(responseType, "responseType");
        outputSchema = ModelContractValidation.requireText(outputSchema, "outputSchema");
    }
}
