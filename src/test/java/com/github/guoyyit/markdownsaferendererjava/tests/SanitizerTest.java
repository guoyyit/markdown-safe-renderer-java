package com.github.guoyyit.markdownsaferendererjava.tests;

import com.github.guoyyit.markdownsaferendererjava.sanitize.HtmlSanitizer;
import com.github.guoyyit.markdownsaferendererjava.sanitize.SanitizedHtml;
import com.github.guoyyit.markdownsaferendererjava.sanitize.SanitizerPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Sanitizer bypass tests: scripts, event handlers, dangerous protocols, escapes. */
class SanitizerTest {

    private final HtmlSanitizer sanitizer = new HtmlSanitizer(SanitizerPolicy.restrictive());

    private String sanitize(String html) {
        return sanitizer.sanitize(html).getHtml();
    }

    @Test
    void scriptTagAndContentRemoved() {
        String out = sanitize("<p>safe</p><script>alert(1)</script>");
        assertFalse(out.contains("script"), out);
        assertFalse(out.contains("alert"), out);
        assertTrue(out.contains("<p>safe</p>"), out);
    }

    @Test
    void scriptCaseVariationRemoved() {
        String out = sanitize("<ScRiPt>alert(1)</sCrIpT>");
        assertFalse(out.toLowerCase().contains("alert"), out);
    }

    @Test
    void nestedScriptInsideAllowedTagRemoved() {
        String out = sanitize("<div><span><script>alert(1)</script></span></div>");
        assertFalse(out.contains("alert"), out);
        assertTrue(out.contains("<div><span></span></div>"), out);
    }

    @Test
    void eventHandlerAttributesRemoved() {
        assertEquals("<img src=\"x\">", sanitize("<img src=x onerror=alert(1)>"));
        assertEquals("<p>hi</p>", sanitize("<p onclick=\"steal()\">hi</p>"));
        assertEquals("<a href=\"https://ok\">x</a>",
                sanitize("<a href=\"https://ok\" onmouseover=\"x()\">x</a>"));
    }

    @Test
    void javascriptProtocolRejected() {
        assertEquals("<a>x</a>", sanitize("<a href=\"javascript:alert(1)\">x</a>"));
    }

    @Test
    void javascriptProtocolCaseVariationRejected() {
        assertEquals("<a>x</a>", sanitize("<a href=\"JaVaScRiPt:alert(1)\">x</a>"));
    }

    @Test
    void javascriptProtocolWithEntitiesRejected() {
        assertEquals("<a>x</a>", sanitize("<a href=\"&#106;avascript:alert(1)\">x</a>"));
        assertEquals("<a>x</a>", sanitize("<a href=\"&#x6a;avascript:alert(1)\">x</a>"));
    }

    @Test
    void javascriptProtocolWithControlCharsRejected() {
        assertEquals("<a>x</a>", sanitize("<a href=\"java\tscript:alert(1)\">x</a>"));
        assertEquals("<a>x</a>", sanitize("<a href=\"  javascript:alert(1)\">x</a>"));
    }

    @Test
    void dataProtocolRejected() {
        String out = sanitize("<a href=\"data:text/html,<script>alert(1)</script>\">x</a>");
        assertFalse(out.contains("href"), out);
    }

    @Test
    void allowedProtocolsKept() {
        assertEquals("<a href=\"https://ok.com\">x</a>",
                sanitize("<a href=\"https://ok.com\">x</a>"));
        assertEquals("<a href=\"http://ok.com\">x</a>",
                sanitize("<a href=\"http://ok.com\">x</a>"));
        assertEquals("<a href=\"mailto:a@b.c\">x</a>",
                sanitize("<a href=\"mailto:a@b.c\">x</a>"));
        assertEquals("<a href=\"/relative/path\">x</a>",
                sanitize("<a href=\"/relative/path\">x</a>"));
        assertEquals("<a href=\"#anchor\">x</a>",
                sanitize("<a href=\"#anchor\">x</a>"));
    }

    @Test
    void attributeEscapeAttemptNeutralised() {
        String out = sanitize("<a title=\"&quot;><script>alert(1)</script>\">x</a>");
        assertFalse(out.contains("<script>"), out);
        // the quote must be escaped so the attribute cannot be broken out of
        assertTrue(out.contains("title=\"&amp;quot;&gt;"), out);
    }

    @Test
    void rawQuoteInAttributeEscaped() {
        String out = sanitize("<img src=\"x\" alt=\"a&quot;b\">");
        assertFalse(out.contains("alt=\"a\"b\""), out);
    }

    @Test
    void disallowedTagsDroppedButTextKept() {
        assertEquals("text", sanitize("<marquee>text</marquee>"));
        assertEquals("<p>a</p>", sanitize("<section><p>a</p></section>"));
    }

    @Test
    void disallowedAttributesDropped() {
        assertEquals("<p>hi</p>", sanitize("<p style=\"color:red\" id=\"x\">hi</p>"));
    }

    @Test
    void commentsAndDeclarationsRemoved() {
        assertEquals("a", sanitize("<!-- hidden -->a"));
        assertEquals("a", sanitize("<!DOCTYPE html>a"));
    }

    @Test
    void unclosedTagAutoClosed() {
        assertEquals("<b>bold</b>", sanitize("<b>bold"));
    }

    @Test
    void unclosedQuoteTagDropped() {
        SanitizedHtml r = sanitizer.sanitize("<a href=\"unterminated");
        assertFalse(r.getHtml().contains("href"), r.getHtml());
        assertFalse(r.getRemovals().isEmpty());
    }

    @Test
    void textIsEscaped() {
        assertEquals("1 &lt; 2 &amp; 3 &gt; 2", sanitize("1 < 2 & 3 > 2"));
    }

    @Test
    void iframeAndEmbedContentRemoved() {
        assertEquals("", sanitize("<iframe src=\"https://evil\"></iframe>"));
        assertEquals("", sanitize("<embed src=\"evil.swf\">"));
    }

    @Test
    void removalsAreReported() {
        SanitizedHtml r = sanitizer.sanitize("<script>x</script><p onclick=\"y\">ok</p>");
        assertEquals(2, r.getRemovals().size());
    }
}
