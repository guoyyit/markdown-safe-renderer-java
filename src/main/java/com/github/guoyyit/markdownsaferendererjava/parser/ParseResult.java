package com.github.guoyyit.markdownsaferendererjava.parser;

import com.github.guoyyit.markdownsaferendererjava.ast.Document;

import java.util.List;

/** Outcome of parsing: a document (possibly partially recovered) plus diagnostics. */
public final class ParseResult {
    private final Document document;
    private final List<ParseError> errors;

    public ParseResult(Document document, List<ParseError> errors) {
        this.document = document;
        this.errors = List.copyOf(errors);
    }

    public Document getDocument() { return document; }
    public List<ParseError> getErrors() { return errors; }
    public boolean hasErrors() { return !errors.isEmpty(); }
}
