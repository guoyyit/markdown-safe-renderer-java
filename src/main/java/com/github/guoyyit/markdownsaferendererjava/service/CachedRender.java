package com.github.guoyyit.markdownsaferendererjava.service;

import com.github.guoyyit.markdownsaferendererjava.ast.Document;
import com.github.guoyyit.markdownsaferendererjava.parser.ParseError;
import com.github.guoyyit.markdownsaferendererjava.toc.TocEntry;

import java.util.List;

/**
 * Fully computed render product stored in the cache. Instances are never
 * mutated after construction; callers must treat the AST and TOC as read-only
 * shared views.
 */
public record CachedRender(Document document, TocEntry toc, String html,
                           List<ParseError> errors) {
    public CachedRender {
        errors = errors == null ? List.of() : List.copyOf(errors);
    }
}
