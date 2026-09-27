package com.github.guoyyit.markdownsaferendererjava.ast;

public final class ThematicBreak extends Node {
    public ThematicBreak(SourceLocation location) { super(location); }
    @Override public NodeType type() { return NodeType.THEMATIC_BREAK; }
}
