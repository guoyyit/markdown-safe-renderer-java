package com.github.guoyyit.markdownsaferendererjava.limits;

/** Distinguishable reasons why a request is rejected. */
public enum RejectionReason {
    INPUT_TOO_LONG,
    NESTING_TOO_DEEP,
    TABLE_TOO_LARGE,
    TOO_MANY_IMAGES,
    PROCESSING_TIMEOUT
}
