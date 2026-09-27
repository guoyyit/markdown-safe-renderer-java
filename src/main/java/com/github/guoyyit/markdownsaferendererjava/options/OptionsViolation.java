package com.github.guoyyit.markdownsaferendererjava.options;

/** A problem found while validating a request's options. */
public final class OptionsViolation {
    private final String key;
    private final String detail;

    public OptionsViolation(String key, String detail) {
        this.key = key;
        this.detail = detail;
    }

    public String getKey() { return key; }
    public String getDetail() { return detail; }

    @Override
    public String toString() { return key + ": " + detail; }
}
