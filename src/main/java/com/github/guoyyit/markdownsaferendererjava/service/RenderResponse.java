package com.github.guoyyit.markdownsaferendererjava.service;

import com.github.guoyyit.markdownsaferendererjava.ast.Document;
import com.github.guoyyit.markdownsaferendererjava.limits.RejectionReason;
import com.github.guoyyit.markdownsaferendererjava.parser.ParseError;
import com.github.guoyyit.markdownsaferendererjava.toc.TocEntry;

import java.util.List;

/** Structured result of a render: AST, TOC, safe HTML, diagnostics, status. */
public final class RenderResponse {
    private final RenderStatus status;
    private final Document document;
    private final TocEntry toc;
    private final String html;
    private final List<ParseError> errors;
    private final RejectionReason rejectionReason;
    private final String message;

    public RenderResponse(RenderStatus status, Document document, TocEntry toc,
                          String html, List<ParseError> errors,
                          RejectionReason rejectionReason, String message) {
        this.status = status;
        this.document = document;
        this.toc = toc;
        this.html = html;
        this.errors = errors == null ? List.of() : List.copyOf(errors);
        this.rejectionReason = rejectionReason;
        this.message = message;
    }

    public RenderStatus getStatus() { return status; }
    public Document getDocument() { return document; }
    public TocEntry getToc() { return toc; }
    public String getHtml() { return html; }
    public List<ParseError> getErrors() { return errors; }
    public RejectionReason getRejectionReason() { return rejectionReason; }
    public String getMessage() { return message; }
}
