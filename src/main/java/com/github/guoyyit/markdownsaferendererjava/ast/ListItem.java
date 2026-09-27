package com.github.guoyyit.markdownsaferendererjava.ast;

public final class ListItem extends Node {
    public ListItem(SourceLocation location) { super(location); }
    @Override public NodeType type() { return NodeType.LIST_ITEM; }
}
