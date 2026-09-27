package com.github.guoyyit.markdownsaferendererjava.tests;

import com.github.guoyyit.markdownsaferendererjava.ast.NodeType;
import com.github.guoyyit.markdownsaferendererjava.limits.ResourceLimits;
import com.github.guoyyit.markdownsaferendererjava.parser.MarkdownParser;
import com.github.guoyyit.markdownsaferendererjava.parser.ParseError;
import com.github.guoyyit.markdownsaferendererjava.parser.ParseResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ErrorRecoveryTest {

    private final MarkdownParser parser = new MarkdownParser(ResourceLimits.defaults());

    @Test
    void unclosedFenceIsReportedButDocumentStillParsed() {
        ParseResult r = parser.parse("# Header\n\n```java\ncode without closing fence\n");
        assertTrue(r.hasErrors());
        ParseError err = r.errors().get(0);
        assertTrue(err.reason().contains("unclosed fenced code block"), err.reason());
        assertEquals(3, err.position().line(), "error must locate the opening fence");
        // heading + code block both survive
        assertEquals(NodeType.HEADING, r.document().getChildren().get(0).getType());
        assertEquals(NodeType.CODE_BLOCK, r.document().getChildren().get(1).getType());
    }

    @Test
    void unclosedCodeSpanBecomesLiteralText() {
        ParseResult r = parser.parse("a `broken span still text\n");
        assertTrue(r.hasErrors());
        assertTrue(r.errors().get(0).reason().contains("unclosed code span"));
        assertEquals(1, r.document().getChildren().size());
        assertEquals(NodeType.PARAGRAPH, r.document().getChildren().get(0).getType());
    }

    @Test
    void malformedTableRowIsSkippedRestSurvives() {
        String md = "| a | b |\n|---|---|\n| 1 | 2 |\n| bad |\n| 3 | 4 |\n";
        ParseResult r = parser.parse(md);
        assertTrue(r.hasErrors());
        assertTrue(r.errors().get(0).reason().contains("table row"), r.errors().get(0).reason());
        assertEquals(4, r.errors().get(0).position().line());
        NodeType table = r.document().getChildren().get(0).getType();
        assertEquals(NodeType.TABLE, table);
        // header + 2 valid rows; bad row skipped
        assertEquals(3, r.document().getChildren().get(0).getChildren().size());
    }

    @Test
    void invalidTableHeaderFallsBackToParagraph() {
        ParseResult r = parser.parse("| only one | extra |\n|---|\n");
        assertTrue(r.hasErrors());
        assertTrue(r.errors().get(0).reason().contains("column mismatch"));
        assertEquals(NodeType.PARAGRAPH, r.document().getChildren().get(0).getType());
    }

    @Test
    void repairedDocumentParsesWithoutErrors() {
        String broken = "# Header\n\n```java\nunclosed\n";
        assertTrue(parser.parse(broken).hasErrors());
        String fixed = "# Header\n\n```java\nclosed\n```\n";
        ParseResult r = parser.parse(fixed);
        assertFalse(r.hasErrors());
    }

    @Test
    void unknownGarbageStructureDoesNotCrash() {
        String md = ">>>> weird\n\n{{{json-ish}}}\n\n###\n\n1) list with ** unbalanced\n";
        ParseResult r = parser.parse(md);
        assertNotNull(r.document());
        // parsing always yields a traversable tree
        r.document().accept(new com.github.guoyyit.markdownsaferendererjava.ast.NodeVisitor() {
        });
    }
}
