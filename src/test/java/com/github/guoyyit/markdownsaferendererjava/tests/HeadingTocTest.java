package com.github.guoyyit.markdownsaferendererjava.tests;

import com.github.guoyyit.markdownsaferendererjava.options.RenderOptions;
import com.github.guoyyit.markdownsaferendererjava.parser.MarkdownParser;
import com.github.guoyyit.markdownsaferendererjava.limits.ResourceLimits;
import com.github.guoyyit.markdownsaferendererjava.parser.ParseResult;
import com.github.guoyyit.markdownsaferendererjava.render.HtmlRenderer;
import com.github.guoyyit.markdownsaferendererjava.render.RenderResult;
import com.github.guoyyit.markdownsaferendererjava.render.TocEntry;
import com.github.guoyyit.markdownsaferendererjava.sanitize.HtmlSanitizer;
import com.github.guoyyit.markdownsaferendererjava.sanitize.SanitizePolicy;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class HeadingTocTest {

    private final MarkdownParser parser = new MarkdownParser(ResourceLimits.defaults());
    private final HtmlRenderer renderer =
            new HtmlRenderer(new HtmlSanitizer(SanitizePolicy.defaultPolicy()));

    private RenderResult render(String md) {
        ParseResult parsed = parser.parse(md);
        return renderer.render(parsed.document(), RenderOptions.v1Defaults(), md);
    }

    @Test
    void duplicateHeadingsGetUniqueAnchors() {
        RenderResult r = render("# Intro\n\n# Intro\n\n# Intro\n");
        String html = r.html();
        assertTrue(html.contains("id=\""), html);
        Set<String> anchors = new HashSet<>();
        int idx = 0;
        while ((idx = html.indexOf("id=\"", idx)) >= 0) {
            int end = html.indexOf('"', idx + 4);
            anchors.add(html.substring(idx + 4, end));
            idx = end;
        }
        assertEquals(3, anchors.size(), "three headings need three unique anchors: " + anchors);
        assertTrue(anchors.stream().anyMatch(a -> a.endsWith("-intro-2")));
        assertTrue(anchors.stream().anyMatch(a -> a.endsWith("-intro-3")));
    }

    @Test
    void headingNumbersAreSequential() {
        RenderResult r = render("# A\n\n## B\n\n## C\n\n### D\n");
        String html = r.html();
        assertTrue(html.contains(">1.</span>"), html);
        assertTrue(html.contains(">1.1.</span>"), html);
        assertTrue(html.contains(">1.2.</span>"), html);
        assertTrue(html.contains(">1.2.1.</span>"), html);
    }

    @Test
    void tocAnchorsMatchRenderedHeadingIds() {
        RenderResult r = render("# First\n\ntext\n\n## Second\n\n## Second\n");
        Set<String> tocLinks = new HashSet<>();
        collectAnchors(r.toc(), tocLinks);
        assertEquals(3, tocLinks.size());
        for (String anchor : tocLinks) {
            assertTrue(r.html().contains("id=\"" + anchor + "\""),
                    "TOC anchor must exist in HTML: " + anchor);
            assertTrue(r.tocHtml().contains("href=\"#" + anchor + "\""),
                    "TOC html must link anchor: " + anchor);
        }
    }

    @Test
    void sameTitlesInDifferentDocumentsDoNotCollide() {
        RenderResult r1 = render("# Same\n");
        RenderResult r2 = render("# Same\nbut different body");
        String a1 = r1.toc().get(0).anchor();
        String a2 = r2.toc().get(0).anchor();
        assertNotEquals(a1, a2, "anchors must be unique across documents");
    }

    @Test
    void identicalInputProducesIdenticalNumberingAndAnchors() {
        RenderResult a = render("# X\n\n## Y\n");
        RenderResult b = render("# X\n\n## Y\n");
        assertEquals(a.toc().get(0).anchor(), b.toc().get(0).anchor());
        assertEquals(a.html(), b.html());
    }

    @Test
    void tocIsNestedByLevel() {
        RenderResult r = render("# A\n\n## A1\n\n### A1a\n\n## A2\n");
        assertEquals(1, r.toc().size());
        TocEntry top = r.toc().get(0);
        assertEquals(2, top.children().size());
        assertEquals(1, top.children().get(0).children().size());
    }

    @Test
    void disablingNumbersKeepsTocTitlesClean() {
        RenderOptions opts = new RenderOptions(RenderOptions.SCHEMA_V1,
                false, true, true, false,
                RenderOptions.v1Defaults().unknownOptionHandling(), java.util.Map.of());
        ParseResult parsed = parser.parse("# Hello\n");
        RenderResult r = renderer.render(parsed.document(), opts, "# Hello\n");
        assertEquals("Hello", r.toc().get(0).title());
        assertFalse(r.html().contains("heading-number"));
    }

    private void collectAnchors(java.util.List<TocEntry> entries, Set<String> out) {
        for (TocEntry e : entries) {
            out.add(e.anchor());
            collectAnchors(e.children(), out);
        }
    }
}
