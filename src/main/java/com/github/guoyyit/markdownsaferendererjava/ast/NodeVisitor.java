package com.github.guoyyit.markdownsaferendererjava.ast;

/**
 * Visitor for traversing the document tree.
 */
public interface NodeVisitor {

    default void visit(Node node) {
        for (Node child : node.getChildren()) {
            visit(child);
        }
    }
}
