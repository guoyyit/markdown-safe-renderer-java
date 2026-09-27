package com.github.guoyyit.markdownsaferendererjava.ast;

public final class QuoteBlock extends Node {
    public QuoteBlock(SourceLocation location) { super(location); }
    @Override public NodeType type() { return NodeType.QUOTE; }
}
