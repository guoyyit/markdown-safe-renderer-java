package com.github.guoyyit.markdownsaferendererjava.parser;

import com.github.guoyyit.markdownsaferendererjava.ast.Node;

import java.util.List;

/**
 * Outcome of parsing: the (possibly partially recovered) document plus
 * all recoverable errors encountered.
 */
public record ParseResult(Node document, List<ParseError> errors) {

    public boolean hasErrors() {
        return !errors.isEmpty();
    }
}
