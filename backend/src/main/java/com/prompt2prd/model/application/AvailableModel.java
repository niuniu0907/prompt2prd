package com.prompt2prd.model.application;

/** One model returned by an OpenAI-compatible /models endpoint. */
public record AvailableModel(String id, String displayName) {

    public AvailableModel {
        id = ModelContractValidation.requireText(id, "id");
        displayName = ModelContractValidation.requireText(displayName, "displayName");
    }
}
