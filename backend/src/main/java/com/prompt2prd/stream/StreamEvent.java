package com.prompt2prd.stream;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record StreamEvent(
        String requestId,
        long eventId,
        StreamEventType type,
        Map<String, Object> data,
        Instant timestamp) {

    public StreamEvent {
        Objects.requireNonNull(requestId, "requestId");
        UUID.fromString(requestId);
        if (eventId < 1) {
            throw new IllegalArgumentException("eventId must be positive");
        }
        Objects.requireNonNull(type, "type");
        data = Map.copyOf(Objects.requireNonNull(data, "data"));
        Objects.requireNonNull(timestamp, "timestamp");
        for (String field : type.requiredDataFields()) {
            if (!data.containsKey(field) || data.get(field) == null) {
                throw new IllegalArgumentException(
                        "Event " + type.wireName() + " requires data field " + field);
            }
        }
    }
}
