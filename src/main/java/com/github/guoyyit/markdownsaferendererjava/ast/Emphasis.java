package com.github.guoyyit.markdownsaferendererjava.ast;

/** Emphasis (italic) inline span. */
public final class Emphasis extends Node {
    public Emphasis(SourceLocation location) { super(location); }
    @Override public NodeType type() { return NodeType.EMPHASIS; }
}
