package com.github.guoyyit.markdownsaferendererjava.ast;

/** Root node of a parsed document. */
public final class Document extends Node {
    public Document(SourceLocation location) { super(location); }
    @Override public NodeType type() { return NodeType.DOCUMENT; }
}
