package com.github.guoyyit.markdownsaferendererjava.options;

import java.time.Instant;

/** One auditable event: option switch, rejection, or internal exception. */
public final class AuditEvent {
    private final Instant timestamp;
    private final String kind;
    private final String detail;

    public AuditEvent(Instant timestamp, String kind, String detail) {
        this.timestamp = timestamp;
        this.kind = kind;
        this.detail = detail;
    }

    public Instant getTimestamp() { return timestamp; }
    public String getKind() { return kind; }
    public String getDetail() { return detail; }

    @Override
    public String toString() { return timestamp + " [" + kind + "] " + detail; }
}
