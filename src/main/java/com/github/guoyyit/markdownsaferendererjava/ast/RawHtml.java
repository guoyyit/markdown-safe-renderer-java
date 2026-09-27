package com.github.guoyyit.markdownsaferendererjava.ast;

/** A raw HTML block or inline fragment from the source; sanitized before rendering. */
public final class RawHtml extends Node {
    private final String html;

    public RawHtml(SourceLocation location, String html) {
        super(location);
        this.html = html == null ? "" : html;
    }

    @Override public NodeType type() { return NodeType.RAW_HTML; }
    public String getHtml() { return html; }
}
