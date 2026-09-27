package com.github.guoyyit.markdownsaferendererjava.ast;

/** A position in the source document, 1-based line/column plus 0-based offset. */
public final class SourceLocation {
    private final int line;
    private final int column;
    private final int offset;

    public SourceLocation(int line, int column, int offset) {
        this.line = line;
        this.column = column;
        this.offset = offset;
    }

    public int getLine() { return line; }
    public int getColumn() { return column; }
    public int getOffset() { return offset; }

    public static SourceLocation unknown() { return new SourceLocation(0, 0, -1); }

    @Override
    public String toString() { return "line " + line + ", col " + column; }
}
