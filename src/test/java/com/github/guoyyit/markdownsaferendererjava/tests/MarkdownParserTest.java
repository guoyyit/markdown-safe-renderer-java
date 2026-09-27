package com.github.guoyyit.markdownsaferendererjava.tests;

import com.github.guoyyit.markdownsaferendererjava.ast.Node;
import com.github.guoyyit.markdownsaferendererjava.ast.NodeType;
import com.github.guoyyit.markdownsaferendererjava.limits.ResourceLimits;
import com.github.guoyyit.markdownsaferendererjava.parser.MarkdownParser;
import com.github.guoyyit.markdownsaferendererjava.parser.ParseResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MarkdownParserTest {

    private final MarkdownParser parser = new MarkdownParser(ResourceLimits.defaults());

    private List<Node> blocks(ParseResult r) {
        return r.document().getChildren();
    }

    @Test
    void parsesHeadingsParagraphsAndBreaks() {
        ParseResult r = parser.parse("# Title\n\nSome text here.\n\n---\n\n## Sub\n");
        assertFalse(r.hasErrors());
        List<Node> b = blocks(r);
        assertEquals(4, b.size());
        assertEquals(NodeType.HEADING, b.get(0).getType());
        assertEquals("1", b.get(0).getAttributes().get("level"));
        assertEquals(NodeType.PARAGRAPH, b.get(1).getType());
        assertEquals(NodeType.THEMATIC_BREAK, b.get(2).getType());
        assertEquals("2", b.get(3).getAttributes().get("level"));
        // positions are 1-based and usable for downstream location
        assertEquals(1, b.get(0).getPosition().line());
        assertEquals(7, b.get(3).getPosition().line());
    }

    @Test
    void parsesInlineLinksImagesAndCodeSpans() {
        ParseResult r = parser.parse(
                "See [docs](https://example.com \"hint\") and ![logo](https://img/x.png) plus `code`.\n");
        Node para = blocks(r).get(0);
        List<Node> in = para.getChildren();
        Node link = in.stream().filter(n -> n.getType() == NodeType.LINK).findFirst().orElseThrow();
        assertEquals("https://example.com", link.getAttributes().get("href"));
        assertEquals("hint", link.getAttributes().get("title"));
        Node img = in.stream().filter(n -> n.getType() == NodeType.IMAGE).findFirst().orElseThrow();
        assertEquals("https://img/x.png", img.getAttributes().get("src"));
        assertEquals("logo", img.getAttributes().get("alt"));
        Node code = in.stream().filter(n -> n.getType() == NodeType.CODE_SPAN).findFirst().orElseThrow();
        assertEquals("code", code.getLiteral());
    }

    @Test
    void parsesOrderedAndUnorderedListsWithNesting() {
        ParseResult r = parser.parse("- a\n- b\n  1. x\n  2. y\n- c\n");
        List<Node> b = blocks(r);
        assertEquals(1, b.size());
        Node ul = b.get(0);
        assertEquals(NodeType.UNORDERED_LIST, ul.getType());
        assertEquals(3, ul.getChildren().size());
        Node second = ul.getChildren().get(1);
        Node nested = second.getChildren().stream()
                .filter(n -> n.getType() == NodeType.ORDERED_LIST).findFirst().orElseThrow();
        assertEquals(2, nested.getChildren().size());
    }

    @Test
    void parsesTablesWithAlignment() {
        ParseResult r = parser.parse("| a | b |\n|:--|--:|\n| 1 | 2 |\n");
        List<Node> b = blocks(r);
        assertEquals(1, b.size());
        Node table = b.get(0);
        assertEquals(NodeType.TABLE, table.getType());
        assertEquals(2, table.getChildren().size()); // header + 1 row
        Node header = table.getChildren().get(0);
        assertEquals("true", header.getAttributes().get("header"));
        assertEquals("left", header.getChildren().get(0).getAttributes().get("align"));
        assertEquals("right", header.getChildren().get(1).getAttributes().get("align"));
    }

    @Test
    void parsesCodeBlocksAndQuotes() {
        ParseResult r = parser.parse("```java\nint x = 1;\n```\n\n> quoted\n> text\n");
        List<Node> b = blocks(r);
        assertEquals(NodeType.CODE_BLOCK, b.get(0).getType());
        assertEquals("java", b.get(0).getAttributes().get("info"));
        assertEquals("int x = 1;\n", b.get(0).getLiteral());
        assertEquals(NodeType.BLOCK_QUOTE, b.get(1).getType());
        assertEquals(NodeType.PARAGRAPH, b.get(1).getChildren().get(0).getType());
    }

    @Test
    void traversableViaVisitor() {
        ParseResult r = parser.parse("# A\n\ntext\n\n- i1\n- i2\n");
        int[] count = {0};
        r.document().accept(new com.github.guoyyit.markdownsaferendererjava.ast.NodeVisitor() {
            @Override
            public void visit(Node node) {
                count[0]++;
                com.github.guoyyit.markdownsaferendererjava.ast.NodeVisitor.super.visit(node);
            }
        });
        assertTrue(count[0] >= 6, "visitor should reach all nodes, got " + count[0]);
    }
}
