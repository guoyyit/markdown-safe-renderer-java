package com.github.guoyyit.markdownsaferendererjava.tests;

import com.github.guoyyit.markdownsaferendererjava.audit.AuditLog;
import com.github.guoyyit.markdownsaferendererjava.cache.DefaultRenderCache;
import com.github.guoyyit.markdownsaferendererjava.limits.ResourceLimits;
import com.github.guoyyit.markdownsaferendererjava.options.OptionsValidator;
import com.github.guoyyit.markdownsaferendererjava.options.RenderOptions;
import com.github.guoyyit.markdownsaferendererjava.options.UnknownOptionHandling;
import com.github.guoyyit.markdownsaferendererjava.parser.MarkdownParser;
import com.github.guoyyit.markdownsaferendererjava.render.HtmlRenderer;
import com.github.guoyyit.markdownsaferendererjava.render.RenderResult;
import com.github.guoyyit.markdownsaferendererjava.sanitize.HtmlSanitizer;
import com.github.guoyyit.markdownsaferendererjava.sanitize.SanitizePolicy;
import com.github.guoyyit.markdownsaferendererjava.service.DocumentRenderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RenderServiceIntegrationTest {

    private DocumentRenderService service;

    @BeforeEach
    void setUp() {
        ResourceLimits limits = ResourceLimits.defaults();
        AuditLog audit = new AuditLog();
        service = new DocumentRenderService(
                new MarkdownParser(limits),
                new HtmlRenderer(new HtmlSanitizer(SanitizePolicy.defaultPolicy())),
                new DefaultRenderCache(64, 60_000),
                limits, new OptionsValidator(audit), audit);
    }

    @Test
    void fullDocumentRendersStructuredResult() {
        String md = "# Doc\n\nIntro with [link](https://example.com).\n\n"
                + "## List\n- one\n- two\n\n"
                + "| k | v |\n|---|---|\n| a | 1 |\n\n"
                + "```\ncode\n```\n\n> quote\n\n![img](https://x/y.png)\n";
        RenderResult r = service.render(md, RenderOptions.v1Defaults());
        assertNotNull(r.document());
        assertTrue(r.html().contains("<h1"));
        assertTrue(r.html().contains("<ul>"));
        assertTrue(r.html().contains("<table>"));
        assertTrue(r.html().contains("<pre><code>"));
        assertTrue(r.html().contains("<blockquote>"));
        assertTrue(r.html().contains("<img "));
        assertFalse(r.hasErrors());
        assertFalse(r.toc().isEmpty());
        assertTrue(r.tocHtml().contains("class=\"toc\""));
    }

    @Test
    void dangerousMarkdownLinkIsNeutralizedInOutput() {
        RenderResult r = service.render("[x](javascript:alert(1))\n", RenderOptions.v1Defaults());
        assertFalse(r.html().contains("javascript:"), r.html());
        assertFalse(r.html().contains("<a "), "unsafe link must not render as an anchor");
        assertTrue(r.html().contains("x"));
    }

    @Test
    void dangerousImageIsNeutralized() {
        RenderResult r = service.render("![alt](JaVaScRiPt:alert(1))\n", RenderOptions.v1Defaults());
        assertFalse(r.html().contains("<img"));
        assertTrue(r.html().contains("unsafe-image"));
    }

    @Test
    void rawHtmlIsEscapedByDefault() {
        RenderResult r = service.render("<script>alert(1)</script>\n", RenderOptions.v1Defaults());
        assertFalse(r.html().contains("<script>"));
        assertTrue(r.html().contains("&lt;script&gt;"));
    }

    @Test
    void rawHtmlWhenExplicitlyAllowedIsStillSanitized() {
        RenderOptions opts = new RenderOptions(RenderOptions.SCHEMA_V1, true, true, true, true,
                UnknownOptionHandling.REJECT, Map.of());
        RenderResult r = service.render("safe <b>bold</b> then <script>x</script>\n", opts);
        assertTrue(r.html().contains("<b>bold</b>"));
        assertFalse(r.html().contains("<script>"));
    }

    @Test
    void scriptContentsAreRemovedNotLeakedAsTextWhenRawHtmlAllowed() {
        RenderOptions opts = new RenderOptions(RenderOptions.SCHEMA_V1, true, true, true, true,
                UnknownOptionHandling.REJECT, Map.of());
        RenderResult r = service.render("<ScRiPt>alert(1)</ScRiPt>tail\n", opts);
        assertFalse(r.html().contains("alert(1)"), "script body must be dropped: " + r.html());
        assertFalse(r.html().toLowerCase().contains("script"), r.html());
        assertTrue(r.html().contains("tail"));
    }

    @Test
    void recoverableErrorsAreReturnedButOutputIsComplete() {
        String md = "# H\n\n```java\nunclosed fence\n\nafter\n";
        RenderResult r = service.render(md, RenderOptions.v1Defaults());
        assertFalse(r.errors().isEmpty());
        assertTrue(r.html().contains("<h1"));
        assertTrue(r.html().contains("<pre><code"));
    }

    @Test
    void codeBlockContentIsAlwaysEscaped() {
        RenderResult r = service.render("```html\n<script>alert(1)</script>\n```\n",
                RenderOptions.v1Defaults());
        assertFalse(r.html().contains("<script>alert(1)</script>"));
        assertTrue(r.html().contains("&lt;script&gt;"));
    }

    @Test
    void sameInputIsCachedDifferentInputIsNot() {
        RenderResult a = service.render("# X\n", RenderOptions.v1Defaults());
        RenderResult b = service.render("# X\n", RenderOptions.v1Defaults());
        assertSame(a, b);
        RenderResult c = service.render("# Y\n", RenderOptions.v1Defaults());
        assertNotSame(a, c);
    }
}
