package com.github.guoyyit.markdownsaferendererjava.ast;

public final class Paragraph extends Node {
    public Paragraph(SourceLocation location) { super(location); }
    @Override public NodeType type() { return NodeType.PARAGRAPH; }
}
