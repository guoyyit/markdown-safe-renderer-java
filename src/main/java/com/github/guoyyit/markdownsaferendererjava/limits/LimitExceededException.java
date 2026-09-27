package com.github.guoyyit.markdownsaferendererjava.limits;

/**
 * Thrown when a request violates a resource limit.
 * No partial output is published; the cache is never polluted.
 */
public class LimitExceededException extends RuntimeException {

    private final LimitReason reason;

    public LimitExceededException(LimitReason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public LimitReason getReason() {
        return reason;
    }
}
