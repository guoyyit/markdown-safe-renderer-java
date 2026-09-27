package com.github.guoyyit.markdownsaferendererjava.ast;

/** A table; children are TableRow nodes, first row is the header. */
public final class TableBlock extends Node {
    public TableBlock(SourceLocation location) { super(location); }
    @Override public NodeType type() { return NodeType.TABLE; }
}
