package com.prompt2prd.model.application;

import java.util.Objects;

/** A provider-independent prompt message. */
public record ModelMessage(Role role, String content) {

    public ModelMessage {
        Objects.requireNonNull(role, "role");
        content = ModelContractValidation.requireText(content, "content");
    }

    public enum Role {
        SYSTEM,
        USER,
        ASSISTANT
    }
}
