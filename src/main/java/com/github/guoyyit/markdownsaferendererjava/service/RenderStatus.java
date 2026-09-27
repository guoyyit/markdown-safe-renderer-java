package com.github.guoyyit.markdownsaferendererjava.service;

/** Outcome classification of a render request. */
public enum RenderStatus {
    OK,
    REJECTED_LIMIT,
    REJECTED_OPTIONS,
    FAILED
}
