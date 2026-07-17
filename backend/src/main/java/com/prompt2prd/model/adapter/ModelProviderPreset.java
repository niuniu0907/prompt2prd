package com.prompt2prd.model.adapter;

import java.net.URI;

/** Well-known OpenAI-compatible endpoints exposed by the model settings UI. */
public enum ModelProviderPreset {

    OPENAI("https://api.openai.com/v1"),
    DEEPSEEK("https://api.deepseek.com/v1"),
    QWEN("https://dashscope.aliyuncs.com/compatible-mode/v1");

    private final URI baseUrl;

    ModelProviderPreset(String baseUrl) {
        this.baseUrl = URI.create(baseUrl);
    }

    public URI baseUrl() {
        return baseUrl;
    }
}
