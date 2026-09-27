package com.github.guoyyit.markdownsaferendererjava.ast;

/**
 * 1-based source position of a node, used for downstream location and error reporting.
 */
public record SourcePos(int line, int column) {

    public static SourcePos of(int line, int column) {
        return new SourcePos(line, column);
    }

    @Override
    public String toString() {
        return line + ":" + column;
    }
}
