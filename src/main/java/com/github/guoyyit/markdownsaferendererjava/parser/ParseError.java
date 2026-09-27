package com.github.guoyyit.markdownsaferendererjava.parser;

import com.github.guoyyit.markdownsaferendererjava.ast.SourceLocation;

/**
 * A recoverable parsing problem. Parsing continues and the offending span is
 * skipped; the error explains what was skipped and where.
 */
public final class ParseError {
    public enum Severity { WARNING, ERROR }

    private final Severity severity;
    private final SourceLocation location;
    private final String message;
    private final String skippedReason;

    public ParseError(Severity severity, SourceLocation location, String message,
                      String skippedReason) {
        this.severity = severity;
        this.location = location;
        this.message = message;
        this.skippedReason = skippedReason;
    }

    public Severity getSeverity() { return severity; }
    public SourceLocation getLocation() { return location; }
    public String getMessage() { return message; }
    public String getSkippedReason() { return skippedReason; }

    @Override
    public String toString() {
        return severity + " at " + location + ": " + message
                + " (skipped: " + skippedReason + ")";
    }
}
