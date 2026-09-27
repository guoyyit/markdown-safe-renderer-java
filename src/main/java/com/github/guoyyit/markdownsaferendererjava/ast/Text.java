package com.github.guoyyit.markdownsaferendererjava.ast;

/** Literal text run. */
public final class Text extends Node {
    private final String content;

    public Text(SourceLocation location, String content) {
        super(location);
        this.content = content == null ? "" : content;
    }

    @Override public NodeType type() { return NodeType.TEXT; }
    public String getContent() { return content; }
}
