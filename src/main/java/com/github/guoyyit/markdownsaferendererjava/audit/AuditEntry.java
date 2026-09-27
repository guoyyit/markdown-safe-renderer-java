package com.github.guoyyit.markdownsaferendererjava.audit;

/**
 * One auditable event.
 */
public record AuditEntry(long timestampMillis, String kind, String detail) {

    public static final String OPTION_SWITCHED = "OPTION_SWITCHED";
    public static final String UNKNOWN_OPTION = "UNKNOWN_OPTION";
    public static final String RENDER_REJECTED = "RENDER_REJECTED";
    public static final String RENDER_ERROR = "RENDER_ERROR";
}
