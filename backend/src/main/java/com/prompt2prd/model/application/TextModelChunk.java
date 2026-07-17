package com.prompt2prd.model.application;

/** One ordered chunk from a text generation call. */
public record TextModelChunk(String requestId, long sequence, String content) {

    public TextModelChunk {
        requestId = ModelContractValidation.requireText(requestId, "requestId");
        if (sequence < 1) {
            throw new IllegalArgumentException("sequence must be greater than zero");
        }
        content = ModelContractValidation.requireText(content, "content");
    }
}
