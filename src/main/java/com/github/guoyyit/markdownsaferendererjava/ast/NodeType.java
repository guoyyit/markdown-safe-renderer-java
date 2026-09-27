package com.github.guoyyit.markdownsaferendererjava.ast;

/**
 * Kinds of structured document nodes produced by the parser.
 */
public enum NodeType {
    DOCUMENT,
    HEADING,
    PARAGRAPH,
    TEXT,
    LINK,
    IMAGE,
    CODE_BLOCK,
    BLOCK_QUOTE,
    ORDERED_LIST,
    UNORDERED_LIST,
    LIST_ITEM,
    TABLE,
    TABLE_ROW,
    TABLE_CELL,
    RAW_HTML,
    CODE_SPAN,
    THEMATIC_BREAK
}
