package com.github.guoyyit.markdownsaferendererjava.ast;

public final class TableRow extends Node {
    private final boolean header;

    public TableRow(SourceLocation location, boolean header) {
        super(location);
        this.header = header;
    }

    @Override public NodeType type() { return NodeType.TABLE_ROW; }
    public boolean isHeader() { return header; }
}
