package com.github.guoyyit.markdownsaferendererjava.ast;

/** An ordered or unordered list. */
public final class ListBlock extends Node {
    private final boolean ordered;
    private final int startNumber;

    public ListBlock(SourceLocation location, boolean ordered, int startNumber) {
        super(location);
        this.ordered = ordered;
        this.startNumber = startNumber;
    }

    @Override public NodeType type() { return NodeType.LIST_BLOCK; }
    public boolean isOrdered() { return ordered; }
    public int getStartNumber() { return startNumber; }
}
