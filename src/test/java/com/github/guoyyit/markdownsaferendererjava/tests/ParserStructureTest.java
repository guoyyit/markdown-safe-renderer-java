package com.github.guoyyit.markdownsaferendererjava.tests;

import com.github.guoyyit.markdownsaferendererjava.ast.*;
import com.github.guoyyit.markdownsaferendererjava.limits.ResourceLimits;
import com.github.guoyyit.markdownsaferendererjava.limits.RuntimeBudget;
import com.github.guoyyit.markdownsaferendererjava.parser.MarkdownParser;
import com.github.guoyyit.markdownsaferendererjava.parser.ParseResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Structural parsing tests: every supported construct yields a traversable AST. */
class ParserStructureTest {

    private final MarkdownParser parser = new MarkdownParser();

    private ParseResult parse(String md) {
        return parser.parse(md, new RuntimeBudget(ResourceLimits.defaults()));
    }

    private static List<Node> ofType(Document doc, NodeType type) {
        List<Node> out = new ArrayList<>();
        doc.walk(n -> {
            if (n.type() == type) {
                out.add(n);
            }
        });
        return out;
    }

    @Test
    void parsesHeadingsWithLevels() {
        ParseResult r = parse("# H1\n\n## H2\n\n### H3\n");
        List<Node> headings = ofType(r.getDocument(), NodeType.HEADING);
        assertEquals(3, headings.size());
        assertEquals(1, ((Heading) headings.get(0)).getLevel());
        assertEquals(2, ((Heading) headings.get(1)).getLevel());
        assertEquals(3, ((Heading) headings.get(2)).getLevel());
        assertFalse(r.hasErrors());
    }

    @Test
    void parsesParagraphWithInlineMarkup() {
        ParseResult r = parse("Hello **bold** *em* `code` end");
        List<Node> paragraphs = ofType(r.getDocument(), NodeType.PARAGRAPH);
        assertEquals(1, paragraphs.size());
        assertEquals(1, ofType(r.getDocument(), NodeType.STRONG).size());
        assertEquals(1, ofType(r.getDocument(), NodeType.EMPHASIS).size());
        assertEquals(1, ofType(r.getDocument(), NodeType.CODE_INLINE).size());
    }

    @Test
    void parsesUnorderedAndOrderedLists() {
        ParseResult r = parse("- a\n- b\n\n1. one\n2. two\n");
        List<Node> lists = ofType(r.getDocument(), NodeType.LIST_BLOCK);
        assertEquals(2, lists.size());
        assertFalse(((ListBlock) lists.get(0)).isOrdered());
        assertTrue(((ListBlock) lists.get(1)).isOrdered());
        assertEquals(4, ofType(r.getDocument(), NodeType.LIST_ITEM).size());
    }

    @Test
    void parsesNestedList() {
        ParseResult r = parse("- a\n  - nested\n- b\n");
        List<Node> lists = ofType(r.getDocument(), NodeType.LIST_BLOCK);
        assertEquals(2, lists.size(), "outer and nested list");
        assertEquals(3, ofType(r.getDocument(), NodeType.LIST_ITEM).size());
    }

    @Test
    void parsesTableWithHeaderAndRows() {
        ParseResult r = parse("| A | B |\n|---|---|\n| 1 | 2 |\n| 3 | 4 |\n");
        List<Node> tables = ofType(r.getDocument(), NodeType.TABLE);
        assertEquals(1, tables.size());
        List<Node> rows = ofType(r.getDocument(), NodeType.TABLE_ROW);
        assertEquals(3, rows.size());
        assertTrue(((TableRow) rows.get(0)).isHeader());
        assertEquals(6, ofType(r.getDocument(), NodeType.TABLE_CELL).size());
    }

    @Test
    void parsesFencedCodeBlockWithLanguage() {
        ParseResult r = parse("```java\nint x = 1;\n```\n");
        List<Node> code = ofType(r.getDocument(), NodeType.CODE_BLOCK);
        assertEquals(1, code.size());
        CodeBlock block = (CodeBlock) code.get(0);
        assertEquals("java", block.getLanguage());
        assertTrue(block.getCode().contains("int x = 1;"));
    }

    @Test
    void parsesQuoteBlock() {
        ParseResult r = parse("> quoted\n> more\n");
        List<Node> quotes = ofType(r.getDocument(), NodeType.QUOTE);
        assertEquals(1, quotes.size());
        assertFalse(ofType(r.getDocument(), NodeType.PARAGRAPH).isEmpty());
    }

    @Test
    void parsesLinkAndImage() {
        ParseResult r = parse("[text](https://example.com \"t\") ![alt](https://i.png)");
        List<Node> links = ofType(r.getDocument(), NodeType.LINK);
        assertEquals(1, links.size());
        assertEquals("https://example.com", ((Link) links.get(0)).getDestination());
        assertEquals("t", ((Link) links.get(0)).getTitle());
        List<Node> images = ofType(r.getDocument(), NodeType.IMAGE);
        assertEquals(1, images.size());
        assertEquals("https://i.png", ((Image) images.get(0)).getSource());
        assertEquals("alt", ((Image) images.get(0)).getAltText());
    }

    @Test
    void parsesRawHtmlBlock() {
        ParseResult r = parse("<div class=\"x\">raw</div>\n");
        assertEquals(1, ofType(r.getDocument(), NodeType.RAW_HTML).size());
    }

    @Test
    void nodesCarrySourceLocations() {
        ParseResult r = parse("# Title\n\npara\n");
        List<Node> headings = ofType(r.getDocument(), NodeType.HEADING);
        assertEquals(1, headings.get(0).location().getLine());
        List<Node> paragraphs = ofType(r.getDocument(), NodeType.PARAGRAPH);
        assertEquals(3, paragraphs.get(0).location().getLine());
    }

    @Test
    void astIsTraversableViaWalk() {
        ParseResult r = parse("# A\n\ntext\n\n- x\n");
        List<NodeType> visited = new ArrayList<>();
        r.getDocument().walk(n -> visited.add(n.type()));
        assertTrue(visited.contains(NodeType.DOCUMENT));
        assertTrue(visited.contains(NodeType.HEADING));
        assertTrue(visited.contains(NodeType.PARAGRAPH));
        assertTrue(visited.contains(NodeType.LIST_BLOCK));
        // parents are linked
        r.getDocument().walk(n -> {
            if (n.type() != NodeType.DOCUMENT) {
                assertNotNull(n.parent());
            }
        });
    }
}
