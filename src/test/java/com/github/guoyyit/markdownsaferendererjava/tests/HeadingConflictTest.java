package com.github.guoyyit.markdownsaferendererjava.tests;

import com.github.guoyyit.markdownsaferendererjava.ast.Document;
import com.github.guoyyit.markdownsaferendererjava.ast.Heading;
import com.github.guoyyit.markdownsaferendererjava.ast.NodeType;
import com.github.guoyyit.markdownsaferendererjava.limits.ResourceLimits;
import com.github.guoyyit.markdownsaferendererjava.limits.RuntimeBudget;
import com.github.guoyyit.markdownsaferendererjava.parser.MarkdownParser;
import com.github.guoyyit.markdownsaferendererjava.toc.AnchorRegistry;
import com.github.guoyyit.markdownsaferendererjava.toc.HeadingNumbering;
import com.github.guoyyit.markdownsaferendererjava.toc.TocEntry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Heading numbering, duplicate anchors, missing levels, reserved anchors, TOC. */
class HeadingConflictTest {

    private final MarkdownParser parser = new MarkdownParser();
    private final HeadingNumbering numbering = new HeadingNumbering();

    private List<Heading> headingsOf(Document doc) {
        List<Heading> out = new ArrayList<>();
        doc.walk(n -> {
            if (n.type() == NodeType.HEADING) {
                out.add((Heading) n);
            }
        });
        return out;
    }


    private List<Heading> numberedHeadings(String md) {
        Document doc = parser.parse(md, new RuntimeBudget(ResourceLimits.defaults())).getDocument();
        numbering.assign(doc, new AnchorRegistry());
        return headingsOf(doc);
    }

    private TocEntry number(String md, AnchorRegistry anchors) {
        Document doc = parser.parse(md, new RuntimeBudget(ResourceLimits.defaults())).getDocument();
        return numbering.assign(doc, anchors);
    }

    private static void collectAnchors(TocEntry e, Set<String> out) {
        if (!e.getAnchor().isEmpty()) {
            out.add(e.getAnchor());
        }
        e.getChildren().forEach(c -> collectAnchors(c, out));
    }

    @Test
    void sequentialNumbering() {
        List<Heading> hs = numberedHeadings("# A\n## B\n## C\n# D\n");
        assertEquals("1", hs.get(0).getNumber());
        assertEquals("1.1", hs.get(1).getNumber());
        assertEquals("1.2", hs.get(2).getNumber());
        assertEquals("2", hs.get(3).getNumber());
    }

    @Test
    void duplicateTitlesGetUniqueAnchors() {
        TocEntry toc = number("# Intro\n# Intro\n# Intro\n", new AnchorRegistry());
        Set<String> anchors = new HashSet<>();
        collectAnchors(toc, anchors);
        assertEquals(3, anchors.size(), "all anchors unique");
        assertTrue(anchors.contains("intro"));
        assertTrue(anchors.contains("intro-2"));
        assertTrue(anchors.contains("intro-3"));
    }

    @Test
    void missingLevelsProduceDeterministicNumbers() {
        List<Heading> hs = numberedHeadings("# A\n### C\n");
        assertEquals("1", hs.get(0).getNumber());
        assertEquals("1.0.1", hs.get(1).getNumber(), "skipped level keeps its own counter");
    }

    @Test
    void numberingIsStableAcrossRuns() {
        String md = "# A\n## B\n# A\n## B\n";
        List<Heading> run1 = numberedHeadings(md);
        List<Heading> run2 = numberedHeadings(md);
        for (int i = 0; i < run1.size(); i++) {
            assertEquals(run1.get(i).getNumber(), run2.get(i).getNumber());
            assertEquals(run1.get(i).getAnchor(), run2.get(i).getAnchor());
        }
    }

    @Test
    void reservedAnchorsAreAvoided() {
        AnchorRegistry anchors = new AnchorRegistry();
        anchors.reserve("intro");
        anchors.reserve("intro-2");
        TocEntry toc = number("# Intro\n# Intro\n", anchors);
        Set<String> used = new HashSet<>();
        collectAnchors(toc, used);
        assertFalse(used.contains("intro"), "reserved anchor must not be handed out");
        assertFalse(used.contains("intro-2"), "reserved anchor must not be handed out");
        assertEquals(2, used.size());
    }

    @Test
    void tocLinksMatchHeadingAnchors() {
        String md = "# One\n## Two\n# Three\n";
        Document doc = parser.parse(md, new RuntimeBudget(ResourceLimits.defaults())).getDocument();
        TocEntry toc = numbering.assign(doc, new AnchorRegistry());
        Set<String> tocAnchors = new HashSet<>();
        collectAnchors(toc, tocAnchors);
        List<Heading> hs = headingsOf(doc);
        assertEquals(tocAnchors.size(), hs.size());
        for (Heading h : hs) {
            assertTrue(tocAnchors.contains(h.getAnchor()),
                    "TOC contains anchor " + h.getAnchor());
        }
    }

    @Test
    void tocNestingFollowsLevels() {
        TocEntry toc = number("# A\n## B\n### C\n## D\n# E\n", new AnchorRegistry());
        assertEquals(2, toc.getChildren().size(), "two top-level entries");
        TocEntry a = toc.getChildren().get(0);
        assertEquals("A", a.getTitle());
        assertEquals(2, a.getChildren().size(), "B and D nested under A");
        assertEquals(1, a.getChildren().get(0).getChildren().size(), "C nested under B");
    }

    @Test
    void specialCharactersInTitlesSlugified() {
        TocEntry toc = number("# Hello, World! 你好\n", new AnchorRegistry());
        String anchor = toc.getChildren().get(0).getAnchor();
        assertFalse(anchor.isEmpty());
        assertFalse(anchor.contains(" "), anchor);
        assertFalse(anchor.contains(","), anchor);
    }

    @Test
    void emptyTitleGetsFallbackAnchor() {
        TocEntry toc = number("# !!!\n", new AnchorRegistry());
        String anchor = toc.getChildren().get(0).getAnchor();
        assertFalse(anchor.isEmpty());
    }
}
