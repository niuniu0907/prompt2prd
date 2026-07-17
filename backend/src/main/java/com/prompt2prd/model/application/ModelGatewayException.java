package com.prompt2prd.model.application;

import java.util.Objects;

/** Provider-neutral failure raised by a {@link ModelGateway} implementation. */
public final class ModelGatewayException extends RuntimeException {

    private final Kind kind;

    public ModelGatewayException(Kind kind, String message) {
        super(message);
        this.kind = Objects.requireNonNull(kind, "kind");
    }

    public ModelGatewayException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = Objects.requireNonNull(kind, "kind");
    }

    public Kind kind() {
        return kind;
    }

    public enum Kind {
        UNREACHABLE,
        AUTHENTICATION,
        MODEL_NOT_FOUND,
        RATE_LIMITED,
        FORMAT_INCOMPATIBLE,
        TIMEOUT,
        CANCELLED,
        INTERNAL
    }
}
