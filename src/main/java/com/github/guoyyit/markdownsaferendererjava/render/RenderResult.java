package com.github.guoyyit.markdownsaferendererjava.render;

import com.github.guoyyit.markdownsaferendererjava.ast.Node;
import com.github.guoyyit.markdownsaferendererjava.parser.ParseError;

import java.util.List;

/**
 * Immutable outcome of a full render: traversable AST, sanitized HTML,
 * table of contents (structured + HTML) and recoverable errors.
 */
public record RenderResult(
        Node document,
        String html,
        List<TocEntry> toc,
        String tocHtml,
        List<ParseError> errors) {

    public boolean hasErrors() {
        return errors != null && !errors.isEmpty();
    }
}
