package com.prompt2prd.model.application;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Vendor-neutral boundary for every model operation used by business modules.
 * Implementations may use Spring AI or another compatible client, while
 * analysis, architecture, and generation code depend only on this interface.
 */
public interface ModelGateway {

    /** Returns a typed result only after the complete structured response is available. */
    <T> Mono<StructuredModelResult<T>> generateStructured(StructuredModelRequest<T> request);

    /** Streams ordered text chunks for long-form content such as PRD sections. */
    Flux<TextModelChunk> streamText(TextModelRequest request);

    /** Verifies that the selected endpoint, credentials, and model are usable. */
    Mono<ModelConnectionResult> testConnection(ModelConnectionRequest request);
}
