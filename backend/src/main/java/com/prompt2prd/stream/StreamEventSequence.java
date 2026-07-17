package com.prompt2prd.stream;

import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Per-request event factory enforcing monotonic IDs and one terminal state. */
public final class StreamEventSequence {

    private final String requestId;
    private final Clock clock;
    private long lastEventId;
    private boolean terminated;

    public StreamEventSequence(String requestId) {
        this(requestId, Clock.systemUTC());
    }

    public StreamEventSequence(String requestId, Clock clock) {
        Objects.requireNonNull(requestId, "requestId");
        UUID.fromString(requestId);
        this.requestId = requestId;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public synchronized StreamEvent next(StreamEventType type, Map<String, Object> data) {
        Objects.requireNonNull(type, "type");
        if (terminated) {
            throw new IllegalStateException("No events are allowed after a terminal event");
        }
        StreamEvent event = new StreamEvent(
                requestId, ++lastEventId, type, data, clock.instant());
        if (type.terminal()) {
            terminated = true;
        }
        return event;
    }
}
