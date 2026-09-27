package com.github.guoyyit.markdownsaferendererjava.ast;

/** Fenced or indented code block; content is literal text, never interpreted. */
public final class CodeBlock extends Node {
    private final String language;
    private final String code;

    public CodeBlock(SourceLocation location, String language, String code) {
        super(location);
        this.language = language;
        this.code = code;
    }

    @Override public NodeType type() { return NodeType.CODE_BLOCK; }
    public String getLanguage() { return language; }
    public String getCode() { return code; }
}
