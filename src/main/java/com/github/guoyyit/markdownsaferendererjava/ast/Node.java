package com.github.guoyyit.markdownsaferendererjava.ast;

import java.util.List;

/**
 * A node in the parsed document tree. Nodes are immutable after construction
 * so they can be safely shared across concurrent renders.
 */
public interface Node {

    NodeType getType();

    /** 1-based source position, may be null for synthetic nodes. */
    SourcePos getPosition();

    /** Child nodes; empty for leaves. Never null. */
    List<Node> getChildren();

    /** Literal text payload (text nodes, code blocks, raw html); null otherwise. */
    default String getLiteral() {
        return null;
    }

    /** Extra attributes keyed by name (href, src, level, ...). Never null. */
    default java.util.Map<String, String> getAttributes() {
        return java.util.Map.of();
    }

    default void accept(NodeVisitor visitor) {
        visitor.visit(this);
    }
}
