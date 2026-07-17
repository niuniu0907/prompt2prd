package com.prompt2prd.model.application;

import java.util.List;
import java.util.Objects;

final class ModelContractValidation {

    private ModelContractValidation() {
    }

    static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    static List<ModelMessage> requireMessages(List<ModelMessage> messages) {
        Objects.requireNonNull(messages, "messages");
        if (messages.isEmpty()) {
            throw new IllegalArgumentException("messages must not be empty");
        }
        return List.copyOf(messages);
    }
}
