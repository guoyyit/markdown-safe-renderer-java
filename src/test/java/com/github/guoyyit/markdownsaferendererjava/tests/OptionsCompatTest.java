package com.github.guoyyit.markdownsaferendererjava.tests;

import com.github.guoyyit.markdownsaferendererjava.cache.CacheConfig;
import com.github.guoyyit.markdownsaferendererjava.cache.LruRenderCache;
import com.github.guoyyit.markdownsaferendererjava.limits.ResourceLimits;
import com.github.guoyyit.markdownsaferendererjava.options.AuditEvent;
import com.github.guoyyit.markdownsaferendererjava.options.InMemoryAuditLog;
import com.github.guoyyit.markdownsaferendererjava.options.OptionsVersion;
import com.github.guoyyit.markdownsaferendererjava.options.OptionsValidator;
import com.github.guoyyit.markdownsaferendererjava.options.RenderOptions;
import com.github.guoyyit.markdownsaferendererjava.options.UnknownOptionPolicy;
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
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Versioned options: defaults preserve output, unknown keys rejected/ignored, audit. */
class OptionsCompatTest {

    private final InMemoryAuditLog auditLog = new InMemoryAuditLog();

    private RenderService newService() {
        return new RenderService(new MarkdownParser(), new HeadingNumbering(),
                new HtmlRenderer(new HtmlSanitizer(SanitizerPolicy.restrictive())),
                new LruRenderCache<CachedRender>(CacheConfig.defaults()),
                ResourceLimits.defaults(), new OptionsValidator(), auditLog);
    }

    private static RenderOptions v1(Map<String, Object> values, UnknownOptionPolicy policy) {
        return new RenderOptions(OptionsVersion.V1, values, policy);
    }

    private boolean auditHas(String kind) {
        return auditLog.recent(100).stream().anyMatch(e -> e.getKind().equals(kind));
    }

    @Test
    void defaultsProduceTocAndNumbering() {
        RenderResponse r = newService().render(
                new RenderRequest("# A\n## B\n", RenderOptions.defaults(), List.of()));
        assertEquals(RenderStatus.OK, r.getStatus());
        assertTrue(r.getHtml().contains("<nav class=\"toc\">"), r.getHtml());
        assertTrue(r.getHtml().contains("heading-number"), r.getHtml());
    }

    @Test
    void tocOptionSwitchesTocOff() {
        RenderResponse r = newService().render(new RenderRequest("# A\n",
                v1(Map.of("toc", false), UnknownOptionPolicy.IGNORE), List.of()));
        assertEquals(RenderStatus.OK, r.getStatus());
        assertFalse(r.getHtml().contains("<nav"), r.getHtml());
        assertTrue(r.getHtml().contains("heading-number"), "numbering unaffected");
    }

    @Test
    void numberingOptionSwitchesNumbersOff() {
        RenderResponse r = newService().render(new RenderRequest("# A\n",
                v1(Map.of("headingNumbering", false), UnknownOptionPolicy.IGNORE), List.of()));
        assertFalse(r.getHtml().contains("heading-number"), r.getHtml());
        assertTrue(r.getHtml().contains("<nav"), "toc still rendered");
    }

    @Test
    void allowRawHtmlOffEscapesInsteadOfSanitizing() {
        RenderResponse r = newService().render(new RenderRequest("<b>bold</b>\n",
                v1(Map.of("allowRawHtml", false), UnknownOptionPolicy.IGNORE), List.of()));
        assertFalse(r.getHtml().contains("<b>"), r.getHtml());
        assertTrue(r.getHtml().contains("&lt;b&gt;"), r.getHtml());
    }

    @Test
    void unknownOptionIgnoredByDefault() {
        RenderResponse r = newService().render(new RenderRequest("# A\n",
                v1(Map.of("futureOption", true), UnknownOptionPolicy.IGNORE), List.of()));
        assertEquals(RenderStatus.OK, r.getStatus());
        assertTrue(auditHas("option-ignored"), "ignored option is audited");
    }

    @Test
    void unknownOptionRejectedWhenPolicySaysSo() {
        RenderResponse r = newService().render(new RenderRequest("# A\n",
                v1(Map.of("futureOption", true), UnknownOptionPolicy.REJECT), List.of()));
        assertEquals(RenderStatus.REJECTED_OPTIONS, r.getStatus());
        assertNull(r.getHtml(), "no output on rejection");
        assertTrue(auditHas("options-rejected"), "rejection is audited");
    }

    @Test
    void wrongTypeForKnownOptionRejected() {
        RenderResponse r = newService().render(new RenderRequest("# A\n",
                v1(Map.of("toc", "not-a-boolean"), UnknownOptionPolicy.IGNORE), List.of()));
        assertEquals(RenderStatus.REJECTED_OPTIONS, r.getStatus(),
                "type errors are rejected even under IGNORE policy");
    }

    @Test
    void defaultsNeverChangeExistingOutput() {
        // rendering with explicit V1 defaults equals rendering with no options at all
        RenderService svc = newService();
        RenderResponse implicit = svc.render(
                new RenderRequest("# A\ntext\n", RenderOptions.defaults(), List.of()));
        RenderResponse explicit = svc.render(new RenderRequest("# A\ntext\n",
                v1(Map.of("toc", true, "headingNumbering", true, "allowRawHtml", true),
                        UnknownOptionPolicy.IGNORE),
                List.of()));
        assertEquals(implicit.getHtml(), explicit.getHtml(),
                "explicit defaults must not change output");
    }

    @Test
    void optionSwitchIsAudited() {
        newService().render(new RenderRequest("# A\n",
                v1(Map.of("toc", false), UnknownOptionPolicy.IGNORE), List.of()));
        assertTrue(auditHas("options-applied"), "applied options are audited");
        List<AuditEvent> events = auditLog.recent(10);
        assertTrue(events.stream().anyMatch(e -> e.getDetail().contains("toc")),
                "audit detail mentions the switched option");
    }

    @Test
    void unsupportedVersionRejected() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> OptionsVersion.fromNumber(99));
    }

    @Test
    void validatorReportsUnknownKeys() {
        OptionsValidator v = new OptionsValidator();
        RenderOptions opts = v1(Map.of("toc", true, "mystery", 1), UnknownOptionPolicy.IGNORE);
        assertEquals(List.of("mystery"), v.unknownKeys(opts));
        assertEquals(1, v.validate(opts).size());
    }
}
