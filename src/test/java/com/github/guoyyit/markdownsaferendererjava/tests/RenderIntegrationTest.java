package com.github.guoyyit.markdownsaferendererjava.tests;

import com.github.guoyyit.markdownsaferendererjava.cache.CacheConfig;
import com.github.guoyyit.markdownsaferendererjava.cache.LruRenderCache;
import com.github.guoyyit.markdownsaferendererjava.limits.ResourceLimits;
import com.github.guoyyit.markdownsaferendererjava.options.InMemoryAuditLog;
import com.github.guoyyit.markdownsaferendererjava.options.OptionsValidator;
import com.github.guoyyit.markdownsaferendererjava.options.RenderOptions;
import com.github.guoyyit.markdownsaferendererjava.parser.MarkdownParser;
import com.github.guoyyit.markdownsaferendererjava.render.HtmlRenderer;
import com.github.guoyyit.markdownsaferendererjava.sanitize.HtmlSanitizer;
import com.github.guoyyit.markdownsaferendererjava.sanitize.SanitizerPolicy;
import com.github.guoyyit.markdownsaferendererjava.service.CachedRender;
import com.github.guoyyit.markdownsaferendererjava.service.RenderRequest;
import com.github.guoyyit.markdownsaferendererjava.service.RenderResponse;
import com.github.guoyyit.markdownsaferendererjava.service.RenderService;
import com.github.guoyyit.markdownsaferendererjava.service.RenderStatus;
import com.github.guoyyit.markdownsaferendererjava.toc.HeadingNumbering;
import com.github.guoyyit.markdownsaferendererjava.toc.TocEntry;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** End-to-end rendering: HTML structure, safe links, TOC/output consistency. */
class RenderIntegrationTest {

    private RenderService newService() {
        return new RenderService(new MarkdownParser(), new HeadingNumbering(),
                new HtmlRenderer(new HtmlSanitizer(SanitizerPolicy.restrictive())),
                new LruRenderCache<CachedRender>(CacheConfig.defaults()),
                ResourceLimits.defaults(), new OptionsValidator(), new InMemoryAuditLog());
    }

    private RenderResponse render(String md) {
        return newService().render(new RenderRequest(md, RenderOptions.defaults(), List.of()));
    }

    @Test
    void rendersAllBlockStructures() {
        String md = """
                # Title

                para **bold**

                - item

                1. one

                > quote

                ```java
                code
                ```

                | A |
                |---|
                | 1 |

                ---
                """;
        String html = render(md).getHtml();
        assertTrue(html.contains("<h1"), html);
        assertTrue(html.contains("<p>"), html);
        assertTrue(html.contains("<strong>bold</strong>"), html);
        assertTrue(html.contains("<ul>"), html);
        assertTrue(html.contains("<ol>"), html);
        assertTrue(html.contains("<blockquote>"), html);
        assertTrue(html.contains("<pre><code class=\"language-java\">"), html);
        assertTrue(html.contains("<table>"), html);
        assertTrue(html.contains("<hr/>"), html);
    }

    @Test
    void markdownLinkWithJavascriptProtocolNotLinked() {
        String html = render("[click](javascript:alert(1))").getHtml();
        assertFalse(html.contains("javascript:"), html);
        assertFalse(html.contains("<a href"), html);
        assertTrue(html.contains("click"), "label text preserved");
    }

    @Test
    void imageWithDangerousSrcDropped() {
        String html = render("![alt text](javascript:alert(1))").getHtml();
        assertFalse(html.contains("<img"), html);
        assertTrue(html.contains("alt text"), "alt text preserved");
    }

    @Test
    void rawHtmlBlockSanitizedInOutput() {
        String html = render("<div onclick=\"evil()\">content</div>\n").getHtml();
        assertFalse(html.contains("onclick"), html);
        assertTrue(html.contains("content"), html);
    }

    @Test
    void tocAnchorsMatchHeadingIds() {
        RenderResponse r = render("# Alpha\n## Beta\n# Gamma\n");
        Set<String> tocHrefs = new HashSet<>();
        Matcher m = Pattern.compile("href=\"#([^\"]+)\"").matcher(r.getHtml());
        while (m.find()) {
            tocHrefs.add(m.group(1));
        }
        Set<String> headingIds = new HashSet<>();
        Matcher hm = Pattern.compile("<h\\d id=\"([^\"]+)\"").matcher(r.getHtml());
        while (hm.find()) {
            headingIds.add(hm.group(1));
        }
        assertFalse(tocHrefs.isEmpty());
        assertEquals(headingIds, tocHrefs, "every TOC link targets an existing heading id");
    }

    @Test
    void tocNumbersMatchHeadingNumbers() {
        RenderResponse r = render("# A\n## B\n");
        String html = r.getHtml();
        assertTrue(html.contains("<span class=\"heading-number\">1</span>"), html);
        assertTrue(html.contains("<span class=\"heading-number\">1.1</span>"), html);
        TocEntry toc = r.getToc();
        assertEquals("1", toc.getChildren().get(0).getNumber());
        assertEquals("1.1", toc.getChildren().get(0).getChildren().get(0).getNumber());
    }

    @Test
    void crossDocumentAnchorConflictResolved() {
        RenderService svc = newService();
        RenderResponse r1 = svc.render(new RenderRequest("# Intro\n",
                RenderOptions.defaults(), List.of()));
        RenderResponse r2 = svc.render(new RenderRequest("# Intro\n",
                RenderOptions.defaults(), List.of("intro")));
        assertTrue(r1.getHtml().contains("id=\"intro\""), r1.getHtml());
        assertFalse(r2.getHtml().contains("id=\"intro\""),
                "reserved anchor avoided in second document");
        assertTrue(r2.getHtml().contains("id=\"intro-2\""), r2.getHtml());
    }

    @Test
    void htmlIsEscapedInText() {
        String html = render("1 < 2 & 3 > 2").getHtml();
        assertTrue(html.contains("1 &lt; 2 &amp; 3 &gt; 2"), html);
    }

    @Test
    void codeBlockContentNotInterpreted() {
        String html = render("```\n<script>alert(1)</script>\n```\n").getHtml();
        assertFalse(html.contains("<script>"), html);
        assertTrue(html.contains("&lt;script&gt;"), "code shown escaped");
    }

    @Test
    void structuredResultTraversable() {
        RenderResponse r = render("# T\n\npara\n");
        assertEquals(RenderStatus.OK, r.getStatus());
        assertNotNull(r.getDocument());
        StringBuilder text = new StringBuilder();
        r.getDocument().walk(n -> {
            if (n instanceof com.github.guoyyit.markdownsaferendererjava.ast.Text t) {
                text.append(t.getContent());
            }
        });
        assertTrue(text.toString().contains("para"));
        assertTrue(text.toString().contains("T"));
    }
}
