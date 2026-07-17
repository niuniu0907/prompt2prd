package com.prompt2prd.model.application;

import java.util.concurrent.atomic.AtomicBoolean;

import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

/**
 * Explicit cancellation signal shared by controllers, orchestrators, and the
 * model adapter. Reactive subscription cancellation remains supported as well.
 */
public final class ModelCancellationSignal {

    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final Sinks.Empty<Void> sink = Sinks.empty();

    /** Signals cancellation once and returns whether this call changed the state. */
    public boolean cancel() {
        if (!cancelled.compareAndSet(false, true)) {
            return false;
        }
        sink.tryEmitEmpty();
        return true;
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    /** Completes once cancellation is requested; otherwise remains pending. */
    public Mono<Void> whenCancelled() {
        return sink.asMono();
    }
}
