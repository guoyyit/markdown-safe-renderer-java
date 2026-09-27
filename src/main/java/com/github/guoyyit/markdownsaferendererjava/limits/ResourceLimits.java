package com.github.guoyyit.markdownsaferendererjava.limits;

/**
 * Configurable resource limits for a single render request.
 */
public record ResourceLimits(
        int maxInputChars,
        int maxDepth,
        int maxTableRows,
        int maxTableCols,
        int maxImages,
        long timeoutMillis) {

    public static ResourceLimits defaults() {
        return new ResourceLimits(1_000_000, 64, 1_000, 100, 100, 5_000);
    }
}
