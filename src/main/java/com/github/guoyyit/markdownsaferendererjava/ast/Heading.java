package com.github.guoyyit.markdownsaferendererjava.ast;

/** A heading with level 1..6. Anchor and number are assigned later by the TOC pass. */
public final class Heading extends Node {
    private final int level;
    private String anchor;
    private String number;

    public Heading(SourceLocation location, int level) {
        super(location);
        this.level = level;
    }

    @Override public NodeType type() { return NodeType.HEADING; }
    public int getLevel() { return level; }
    public String getAnchor() { return anchor; }
    public void setAnchor(String anchor) { this.anchor = anchor; }
    public String getNumber() { return number; }
    public void setNumber(String number) { this.number = number; }
}
