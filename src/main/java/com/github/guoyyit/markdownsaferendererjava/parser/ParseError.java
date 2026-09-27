package com.github.guoyyit.markdownsaferendererjava.parser;

import com.github.guoyyit.markdownsaferendererjava.ast.SourcePos;

/**
 * A recoverable parse problem. Parsing continues after the error;
 * the offending construct is skipped or degraded.
 */
public record ParseError(SourcePos position, String reason, String skippedFragment) {

    @Override
    public String toString() {
        return "ParseError[" + position + ": " + reason + "]";
    }
}
