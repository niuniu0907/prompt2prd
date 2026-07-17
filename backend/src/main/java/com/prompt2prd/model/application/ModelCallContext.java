package com.prompt2prd.model.application;

import java.util.Objects;
import java.util.UUID;

/** Shared request identity, endpoint configuration, and cancellation state. */
public record ModelCallContext(
        String requestId,
        ModelEndpoint endpoint,
        ModelCancellationSignal cancellation
) {

    public ModelCallContext {
        requestId = ModelContractValidation.requireText(requestId, "requestId");
        UUID.fromString(requestId);
        Objects.requireNonNull(endpoint, "endpoint");
        Objects.requireNonNull(cancellation, "cancellation");
    }
}
