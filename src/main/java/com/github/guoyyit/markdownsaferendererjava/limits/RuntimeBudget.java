package com.github.guoyyit.markdownsaferendererjava.limits;

/** Per-request runtime budget: deadline and counted resources. */
public final class RuntimeBudget {
    private final ResourceLimits limits;
    private final long deadlineNanos;
    private int imageCount;
    private int maxDepthSeen;

    public RuntimeBudget(ResourceLimits limits) {
        this.limits = limits;
        this.deadlineNanos = System.nanoTime() + limits.getMaxProcessingMillis() * 1_000_000L;
    }

    public ResourceLimits limits() { return limits; }

    /** Throws LimitExceededException(PROCESSING_TIMEOUT) if the deadline elapsed. */
    public void checkTimeout() {
        if (System.nanoTime() > deadlineNanos) {
            throw new LimitExceededException(RejectionReason.PROCESSING_TIMEOUT,
                    "processing exceeded " + limits.getMaxProcessingMillis() + " ms");
        }
    }

    /** Records a nesting depth; throws if it exceeds the configured maximum. */
    public void recordDepth(int depth) {
        if (depth > maxDepthSeen) {
            maxDepthSeen = depth;
        }
        if (depth > limits.getMaxNestingDepth()) {
            throw new LimitExceededException(RejectionReason.NESTING_TOO_DEEP,
                    "nesting depth " + depth + " exceeds limit "
                            + limits.getMaxNestingDepth());
        }
    }

    /** Records one image; throws if the image count exceeds the configured maximum. */
    public void recordImage() {
        imageCount++;
        if (imageCount > limits.getMaxImages()) {
            throw new LimitExceededException(RejectionReason.TOO_MANY_IMAGES,
                    "image count exceeds limit " + limits.getMaxImages());
        }
    }

    public int getImageCount() { return imageCount; }
    public int getMaxDepthSeen() { return maxDepthSeen; }
}
