package com.prompt2prd.model.application;

import java.util.Objects;

/** Request to verify the currently selected model configuration. */
public record ModelConnectionRequest(ModelCallContext context) {

    public ModelConnectionRequest {
        Objects.requireNonNull(context, "context");
    }
}
