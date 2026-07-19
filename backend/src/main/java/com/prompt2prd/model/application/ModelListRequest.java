package com.prompt2prd.model.application;

import java.net.URI;
import java.util.Objects;

/** Request to fetch model IDs exposed by an OpenAI-compatible /models endpoint. */
public record ModelListRequest(
        String requestId,
        URI baseUrl,
        String apiKey,
        ModelCancellationSignal cancellation) {

    public ModelListRequest {
        requestId = ModelContractValidation.requireText(requestId, "requestId");
        Objects.requireNonNull(baseUrl, "baseUrl");
        apiKey = Objects.requireNonNull(apiKey, "apiKey");
        Objects.requireNonNull(cancellation, "cancellation");
    }
}
