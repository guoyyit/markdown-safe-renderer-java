package com.github.guoyyit.markdownsaferendererjava.ast;

/** Inline code span; content is literal text. */
public final class CodeInline extends Node {
    private final String code;

    public CodeInline(SourceLocation location, String code) {
        super(location);
        this.code = code == null ? "" : code;
    }

    @Override public NodeType type() { return NodeType.CODE_INLINE; }
    public String getCode() { return code; }
}
