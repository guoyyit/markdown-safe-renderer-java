package com.github.guoyyit.markdownsaferendererjava.options;

/**
 * How unknown render options are treated.
 */
public enum UnknownOptionHandling {
    /** Reject the request if an unknown option is present. */
    REJECT,
    /** Silently ignore unknown options. */
    IGNORE
}
