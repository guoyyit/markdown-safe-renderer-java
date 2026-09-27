package com.github.guoyyit.markdownsaferendererjava.ast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Base class for all document AST nodes. Supports traversal and source location. */
public abstract class Node {
    private final SourceLocation location;
    private final List<Node> children = new ArrayList<>();
    private Node parent;

    protected Node(SourceLocation location) {
        this.location = location == null ? SourceLocation.unknown() : location;
    }

    public abstract NodeType type();

    public SourceLocation location() { return location; }

    public Node parent() { return parent; }

    public List<Node> children() { return Collections.unmodifiableList(children); }

    public void addChild(Node child) {
        if (child != null) {
            child.parent = this;
            children.add(child);
        }
    }

    /** Depth-first pre-order traversal. */
    public void walk(NodeVisitor visitor) {
        visitor.visit(this);
        for (Node child : children) {
            child.walk(visitor);
        }
    }

    public interface NodeVisitor {
        void visit(Node node);
    }
}
