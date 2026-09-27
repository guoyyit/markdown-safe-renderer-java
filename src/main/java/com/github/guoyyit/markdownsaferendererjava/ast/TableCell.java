package com.github.guoyyit.markdownsaferendererjava.ast;

public final class TableCell extends Node {
    public TableCell(SourceLocation location) { super(location); }
    @Override public NodeType type() { return NodeType.TABLE_CELL; }
}
