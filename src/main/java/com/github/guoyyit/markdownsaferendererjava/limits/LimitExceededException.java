package com.github.guoyyit.markdownsaferendererjava.limits;

/** Thrown when a configured resource limit is exceeded. Never cached. */
public class LimitExceededException extends RuntimeException {
    private final RejectionReason reason;

    public LimitExceededException(RejectionReason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public RejectionReason getReason() { return reason; }
}
