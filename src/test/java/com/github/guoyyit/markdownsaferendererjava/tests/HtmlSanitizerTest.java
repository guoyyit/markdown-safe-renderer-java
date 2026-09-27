package com.github.guoyyit.markdownsaferendererjava.tests;

import com.github.guoyyit.markdownsaferendererjava.sanitize.HtmlSanitizer;
import com.github.guoyyit.markdownsaferendererjava.sanitize.SanitizePolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HtmlSanitizerTest {

    private final HtmlSanitizer sanitizer = new HtmlSanitizer(SanitizePolicy.defaultPolicy());

    @Test
    void scriptTagAndContentAreRemoved() {
        String out = sanitizer.sanitize("<script>alert(1)</script>ok");
        assertEquals("ok", out);
    }

    @Test
    void scriptWithCaseVariationIsRemoved() {
        String out = sanitizer.sanitize("<ScRiPt>alert(1)</sCrIpT>ok");
        assertEquals("ok", out);
    }

    @Test
    void nestedScriptCannotEscape() {
        String out = sanitizer.sanitize("<script><script>alert(1)</script>still</script>ok");
        assertEquals("ok", out);
    }

    @Test
    void mixedCaseOpenAndCloseScriptAreRemoved() {
        assertEquals("ok", sanitizer.sanitize("<ScRiPt>alert(1)</ScRiPt>ok"));
        assertEquals("ok", sanitizer.sanitize("<ScRiPt>alert(1)</sCrIpT>ok"));
        assertEquals("ok", sanitizer.sanitize("<SCRIPT>alert(1)</script>ok"));
    }

    @Test
    void eventHandlersAreDropped() {
        String out = sanitizer.sanitize("<b onclick=\"alert(1)\" onmouseover='x'>hi</b>");
        assertEquals("<b>hi</b>", out);
    }

    @Test
    void javascriptProtocolIsRejected() {
        assertFalse(sanitizer.isSafeUrl("javascript:alert(1)"));
        assertFalse(sanitizer.isSafeUrl("JaVaScRiPt:alert(1)"));
        assertFalse(sanitizer.isSafeUrl("  javascript:alert(1)"));
        assertFalse(sanitizer.isSafeUrl("java\tscript:alert(1)"));
        assertFalse(sanitizer.isSafeUrl("java\nscript:alert(1)"));
        assertFalse(sanitizer.isSafeUrl("&#106;avascript:alert(1)"));
        assertFalse(sanitizer.isSafeUrl("&#x6a;avascript:alert(1)"));
        assertFalse(sanitizer.isSafeUrl("vbscript:msgbox(1)"));
        assertFalse(sanitizer.isSafeUrl("data:text/html,<script>alert(1)</script>"));
    }

    @Test
    void safeProtocolsAndRelativeUrlsPass() {
        assertTrue(sanitizer.isSafeUrl("https://example.com/x"));
        assertTrue(sanitizer.isSafeUrl("HTTP://example.com"));
        assertTrue(sanitizer.isSafeUrl("mailto:a@b.c"));
        assertTrue(sanitizer.isSafeUrl("/relative/path"));
        assertTrue(sanitizer.isSafeUrl("#anchor"));
        assertTrue(sanitizer.isSafeUrl("./sibling"));
        assertTrue(sanitizer.isSafeUrl("page.html"));
    }

    @Test
    void dangerousHrefAttributeIsDroppedButTagKept() {
        String out = sanitizer.sanitize("<a href=\"javascript:alert(1)\">x</a>");
        assertEquals("<a>x</a>", out);
    }

    @Test
    void attributeBreakoutIsEscaped() {
        String out = sanitizer.sanitize("<a title='\" onmouseover=\"alert(1)' href=\"https://e.com\">x</a>");
        // the injected quotes are neutralized inside the escaped attribute value,
        // so no live onmouseover attribute can appear
        assertEquals("<a title=\"&quot; onmouseover=&quot;alert(1)\" href=\"https://e.com\">x</a>", out);
    }

    @Test
    void unterminatedQuoteCannotSmuggleAttributes() {
        String out = sanitizer.sanitize("<a href=\"https://e.com onmouseover=alert(1)>x");
        // truly unterminated quote → malformed tag → escaped as text, no live <a>
        assertFalse(out.contains("<a "), "malformed tag must not be emitted: " + out);
        assertTrue(out.contains("&lt;a"));
    }

    @Test
    void disallowedTagsAreEscapedNotExecuted() {
        String out = sanitizer.sanitize("<iframe src=\"https://evil\"></iframe><form action=\"/x\">f</form>");
        assertFalse(out.contains("<iframe"));
        assertFalse(out.contains("<form"));
        // raw-text containers are dropped wholesale; other disallowed tags are escaped
        assertTrue(out.contains("&lt;form"));
    }

    @Test
    void commentsAndDoctypeAreDropped() {
        String out = sanitizer.sanitize("<!DOCTYPE html><!-- <script>alert(1)</script> --><b>ok</b>");
        assertEquals("<b>ok</b>", out);
    }

    @Test
    void unclosedCommentDoesNotLeak() {
        String out = sanitizer.sanitize("ok<!-- never closed <script>x</script>");
        assertEquals("ok", out);
    }

    @Test
    void allowedTagsAndAttributesSurvive() {
        String out = sanitizer.sanitize(
                "<a href=\"https://e.com\" title=\"t\">l</a><img src=\"https://i/x.png\" alt=\"a\"/>");
        assertEquals("<a href=\"https://e.com\" title=\"t\">l</a>"
                + "<img src=\"https://i/x.png\" alt=\"a\"/>", out);
    }

    @Test
    void entityObfuscatedProtocolInAttributeIsCaught() {
        String out = sanitizer.sanitize("<a href=\"&#106;&#97;vascript:alert(1)\">x</a>");
        assertEquals("<a>x</a>", out);
    }

    @Test
    void plainTextIsEscaped() {
        String out = sanitizer.sanitize("1 < 2 & 3 > 2");
        assertEquals("1 &lt; 2 &amp; 3 &gt; 2", out);
    }
}
