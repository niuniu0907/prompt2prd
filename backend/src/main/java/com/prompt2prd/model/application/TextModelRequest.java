package com.prompt2prd.model.application;

import java.util.List;
import java.util.Objects;

/** Request for an ordered stream of plain text chunks. */
public record TextModelRequest(
        ModelCallContext context,
        List<ModelMessage> messages
) {

    public TextModelRequest {
        Objects.requireNonNull(context, "context");
        messages = ModelContractValidation.requireMessages(messages);
    }
}
