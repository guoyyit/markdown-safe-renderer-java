package com.github.guoyyit.markdownsaferendererjava.ast;

/** Strong (bold) inline span. */
public final class Strong extends Node {
    public Strong(SourceLocation location) { super(location); }
    @Override public NodeType type() { return NodeType.STRONG; }
}
