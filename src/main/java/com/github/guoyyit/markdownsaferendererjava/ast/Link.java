package com.github.guoyyit.markdownsaferendererjava.ast;

/** Inline link; children are the link text nodes. */
public final class Link extends Node {
    private final String destination;
    private final String title;

    public Link(SourceLocation location, String destination, String title) {
        super(location);
        this.destination = destination;
        this.title = title;
    }

    @Override public NodeType type() { return NodeType.LINK; }
    public String getDestination() { return destination; }
    public String getTitle() { return title; }
}
