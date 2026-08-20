package fr.vriege.anilib.framework.concurrent.runtime;

import java.util.Objects;

public final class TaskPipelineException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final Reason reason;

    TaskPipelineException(Reason reason, String message) {
        super(message);
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    TaskPipelineException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    public Reason reason() {
        return reason;
    }

    public enum Reason {
        BUSY,
        CLOSED,
        INTERRUPTED,
        SUPERSEDED,
        TIMED_OUT
    }
}
