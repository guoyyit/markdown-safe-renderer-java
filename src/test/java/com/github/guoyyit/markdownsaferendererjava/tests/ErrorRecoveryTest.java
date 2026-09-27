package com.github.guoyyit.markdownsaferendererjava.tests;

import com.github.guoyyit.markdownsaferendererjava.ast.Document;
import com.github.guoyyit.markdownsaferendererjava.ast.NodeType;
import com.github.guoyyit.markdownsaferendererjava.limits.ResourceLimits;
import com.github.guoyyit.markdownsaferendererjava.limits.RuntimeBudget;
import com.github.guoyyit.markdownsaferendererjava.parser.MarkdownParser;
import com.github.guoyyit.markdownsaferendererjava.parser.ParseError;
import com.github.guoyyit.markdownsaferendererjava.parser.ParseResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Local error recovery: bad spans are skipped, the rest of the doc survives. */
class ErrorRecoveryTest {

    private final MarkdownParser parser = new MarkdownParser();

    private ParseResult parse(String md) {
        return parser.parse(md, new RuntimeBudget(ResourceLimits.defaults()));
    }

    private static int count(Document doc, NodeType type) {
        List<NodeType> seen = new ArrayList<>();
        doc.walk(n -> seen.add(n.type()));
        return (int) seen.stream().filter(t -> t == type).count();
    }

    @Test
    void unclosedFenceRecoversAndReports() {
        ParseResult r = parse("# Before\n\n```java\ncode without end\n");
        assertTrue(r.hasErrors());
        ParseError e = r.getErrors().get(0);
        assertEquals(ParseError.Severity.ERROR, e.getSeverity());
        assertTrue(e.getMessage().contains("unclosed"), e.getMessage());
        assertEquals(3, e.getLocation().getLine(), "error points at the fence line");
        assertNotNull(e.getSkippedReason());
        // the rest of the document is still structured
        assertEquals(1, count(r.getDocument(), NodeType.HEADING));
        assertEquals(1, count(r.getDocument(), NodeType.CODE_BLOCK));
    }

    @Test
    void contentAfterUnclosedConstructStillParses() {
        ParseResult r = parse("[broken link](\n\n# After\n");
        assertEquals(1, count(r.getDocument(), NodeType.HEADING),
                "heading after the broken link survives");
    }

    @Test
    void raggedTableRowWarnsButTableSurvives() {
        ParseResult r = parse("| A | B |\n|---|---|\n| 1 |\n| 2 | 3 |\n");
        assertTrue(r.hasErrors());
        ParseError e = r.getErrors().get(0);
        assertEquals(ParseError.Severity.WARNING, e.getSeverity());
        assertEquals(3, e.getLocation().getLine(), "warning points at the ragged row");
        assertTrue(e.getSkippedReason().contains("padded"), e.getSkippedReason());
        assertEquals(1, count(r.getDocument(), NodeType.TABLE));
        // both data rows present; the short one was padded
        assertEquals(3, count(r.getDocument(), NodeType.TABLE_ROW));
    }

    @Test
    void unclosedLinkReportsWarning() {
        ParseResult r = parse("text [label](https://x.com and more");
        assertTrue(r.hasErrors());
        assertEquals(ParseError.Severity.WARNING, r.getErrors().get(0).getSeverity());
        assertTrue(r.getErrors().get(0).getMessage().contains("unclosed link"));
    }

    @Test
    void unknownStructuresDegradeToParagraph() {
        ParseResult r = parse("::: unknown container\ncontent\n:::\n");
        assertFalse(r.hasErrors());
        assertEquals(1, count(r.getDocument(), NodeType.PARAGRAPH));
    }

    @Test
    void fixedInputRetriesCleanly() {
        String broken = "# T\n\n```\nunclosed\n";
        ParseResult first = parse(broken);
        assertTrue(first.hasErrors());

        String fixed = "# T\n\n```\nclosed\n```\n";
        ParseResult second = parse(fixed);
        assertFalse(second.hasErrors(), "fixed input parses without errors");
        assertEquals(1, count(second.getDocument(), NodeType.CODE_BLOCK));
    }

    @Test
    void multipleErrorsAllReported() {
        ParseResult r = parse("| A | B |\n|---|---|\n| 1 |\n\n```\nunclosed\n");
        assertEquals(2, r.getErrors().size(),
                "table warning and fence error both reported");
    }

    @Test
    void errorLocationsAreAccurate() {
        ParseResult r = parse("line1\n\nline3\n\nline5\n\n```\nunclosed\n");
        ParseError e = r.getErrors().get(0);
        assertEquals(7, e.getLocation().getLine(), "fence starts on line 7");
    }

    @Test
    void emptyAndNullInputSafe() {
        assertFalse(parse("").hasErrors());
        assertFalse(parse(null).hasErrors());
        assertEquals(0, count(parse("").getDocument(), NodeType.PARAGRAPH));
    }

    @Test
    void loneMarkersArePlainText() {
        ParseResult star = parse("a * single star\n");
        assertEquals(0, count(star.getDocument(), NodeType.EMPHASIS));
        ParseResult doubleStar = parse("double ** only\n");
        assertEquals(0, count(doubleStar.getDocument(), NodeType.STRONG));
        assertEquals(0, count(doubleStar.getDocument(), NodeType.EMPHASIS));
        ParseResult tick = parse("one ` tick\n");
        assertEquals(0, count(tick.getDocument(), NodeType.CODE_INLINE));
    }
}
