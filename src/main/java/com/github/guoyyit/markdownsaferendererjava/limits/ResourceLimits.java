package com.github.guoyyit.markdownsaferendererjava.limits;

/** Configurable resource limits for a single render operation. */
public final class ResourceLimits {
    private final int maxInputLength;
    private final int maxNestingDepth;
    private final int maxTableRows;
    private final int maxTableColumns;
    private final int maxImages;
    private final long maxProcessingMillis;

    public ResourceLimits(int maxInputLength, int maxNestingDepth, int maxTableRows,
                          int maxTableColumns, int maxImages, long maxProcessingMillis) {
        this.maxInputLength = maxInputLength;
        this.maxNestingDepth = maxNestingDepth;
        this.maxTableRows = maxTableRows;
        this.maxTableColumns = maxTableColumns;
        this.maxImages = maxImages;
        this.maxProcessingMillis = maxProcessingMillis;
    }

    public static ResourceLimits defaults() {
        return new ResourceLimits(1_000_000, 64, 200, 50, 100, 3_000);
    }

    public int getMaxInputLength() { return maxInputLength; }
    public int getMaxNestingDepth() { return maxNestingDepth; }
    public int getMaxTableRows() { return maxTableRows; }
    public int getMaxTableColumns() { return maxTableColumns; }
    public long getMaxProcessingMillis() { return maxProcessingMillis; }
    public int getMaxImages() { return maxImages; }
}
