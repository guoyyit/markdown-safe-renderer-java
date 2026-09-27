package com.github.guoyyit.markdownsaferendererjava.ast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Factory and base implementation for document nodes.
 */
public final class Nodes {

    private Nodes() {
    }

    public static MutableNode node(NodeType type, SourcePos pos) {
        return new MutableNode(type, pos);
    }

    public static MutableNode text(String literal, SourcePos pos) {
        MutableNode n = new MutableNode(NodeType.TEXT, pos);
        n.setLiteral(literal);
        return n;
    }

    /** Mutable during parsing; call {@link #freeze()} before publishing. */
    public static final class MutableNode implements Node {
        private final NodeType type;
        private final SourcePos pos;
        private final List<Node> children = new ArrayList<>();
        private final Map<String, String> attributes = new LinkedHashMap<>();
        private String literal;
        private boolean frozen;

        private MutableNode(NodeType type, SourcePos pos) {
            this.type = type;
            this.pos = pos;
        }

        public void addChild(Node child) {
            checkMutable();
            children.add(child);
        }

        public void setLiteral(String literal) {
            checkMutable();
            this.literal = literal;
        }

        public void putAttribute(String key, String value) {
            checkMutable();
            attributes.put(key, value);
        }

        /** Recursively freezes this node so it becomes safely publishable. */
        public MutableNode freeze() {
            for (Node c : children) {
                if (c instanceof MutableNode m) {
                    m.freeze();
                }
            }
            this.frozen = true;
            return this;
        }

        private void checkMutable() {
            if (frozen) {
                throw new IllegalStateException("node is frozen");
            }
        }

        @Override
        public NodeType getType() {
            return type;
        }

        @Override
        public SourcePos getPosition() {
            return pos;
        }

        @Override
        public List<Node> getChildren() {
            return Collections.unmodifiableList(children);
        }

        @Override
        public String getLiteral() {
            return literal;
        }

        @Override
        public Map<String, String> getAttributes() {
            return Collections.unmodifiableMap(attributes);
        }
    }
}
