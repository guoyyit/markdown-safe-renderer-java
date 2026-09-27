package com.github.guoyyit.markdownsaferendererjava.ast;

public final class Image extends Node {
    private final String source;
    private final String altText;
    private final String title;

    public Image(SourceLocation location, String source, String altText, String title) {
        super(location);
        this.source = source;
        this.altText = altText;
        this.title = title;
    }

    @Override public NodeType type() { return NodeType.IMAGE; }
    public String getSource() { return source; }
    public String getAltText() { return altText; }
    public String getTitle() { return title; }
}
