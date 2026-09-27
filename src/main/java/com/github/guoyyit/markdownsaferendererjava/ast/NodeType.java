package com.github.guoyyit.markdownsaferendererjava.ast;

/** Kinds of structured document nodes. */
public enum NodeType {
    DOCUMENT,
    HEADING,
    PARAGRAPH,
    LIST_BLOCK,
    LIST_ITEM,
    TABLE,
    TABLE_ROW,
    TABLE_CELL,
    CODE_BLOCK,
    QUOTE,
    LINK,
    IMAGE,
    TEXT,
    RAW_HTML,
    THEMATIC_BREAK,
    STRONG,
    EMPHASIS,
    CODE_INLINE
}
