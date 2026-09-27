package com.github.guoyyit.markdownsaferendererjava.limits;

/**
 * Distinguishable reasons a request was rejected.
 */
public enum LimitReason {
    INPUT_TOO_LONG,
    NESTING_TOO_DEEP,
    TABLE_TOO_LARGE,
    TOO_MANY_IMAGES,
    PROCESSING_TIMEOUT,
    INTERNAL_RESOURCE_LIMIT
}
