package com.github.guoyyit.markdownsaferendererjava.tests;

import com.github.guoyyit.markdownsaferendererjava.audit.AuditLog;
import com.github.guoyyit.markdownsaferendererjava.cache.DefaultRenderCache;
import com.github.guoyyit.markdownsaferendererjava.limits.LimitExceededException;
import com.github.guoyyit.markdownsaferendererjava.limits.LimitReason;
import com.github.guoyyit.markdownsaferendererjava.limits.ResourceLimits;
import com.github.guoyyit.markdownsaferendererjava.options.OptionsValidator;
import com.github.guoyyit.markdownsaferendererjava.options.RenderOptions;
import com.github.guoyyit.markdownsaferendererjava.parser.MarkdownParser;
import com.github.guoyyit.markdownsaferendererjava.parser.ParseResult;
import com.github.guoyyit.markdownsaferendererjava.render.HtmlRenderer;
import com.github.guoyyit.markdownsaferendererjava.sanitize.HtmlSanitizer;
import com.github.guoyyit.markdownsaferendererjava.sanitize.SanitizePolicy;
import com.github.guoyyit.markdownsaferendererjava.service.DocumentRenderService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ResourceLimitsTest {

    private DocumentRenderService service(ResourceLimits limits, MarkdownParser parser) {
        return new DocumentRenderService(parser,
                new HtmlRenderer(new HtmlSanitizer(SanitizePolicy.defaultPolicy())),
                new DefaultRenderCache(10, 60_000),
                limits, new OptionsValidator(new AuditLog()), new AuditLog());
    }

    private DocumentRenderService service(ResourceLimits limits) {
        return service(limits, new MarkdownParser(limits));
    }

    @Test
    void inputTooLongIsRejectedWithDistinctReason() {
        DocumentRenderService svc = service(new ResourceLimits(10, 64, 100, 20, 100, 5000));
        LimitExceededException e = assertThrows(LimitExceededException.class,
                () -> svc.render("this is more than ten chars", RenderOptions.v1Defaults()));
        assertEquals(LimitReason.INPUT_TOO_LONG, e.getReason());
        assertEquals(0, svc.getCache().size(), "rejected request must not be cached");
    }

    @Test
    void nestingTooDeepIsRejected() {
        DocumentRenderService svc = service(new ResourceLimits(1_000_000, 3, 100, 20, 100, 5000));
        StringBuilder md = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            md.append("> ".repeat(i)).append("deep\n");
        }
        LimitExceededException e = assertThrows(LimitExceededException.class,
                () -> svc.render(md.toString(), RenderOptions.v1Defaults()));
        assertEquals(LimitReason.NESTING_TOO_DEEP, e.getReason());
    }

    @Test
    void tableTooManyColumnsIsRejected() {
        DocumentRenderService svc = service(new ResourceLimits(1_000_000, 64, 100, 3, 100, 5000));
        StringBuilder md = new StringBuilder("| a | b | c | d |\n|---|---|---|---|\n| 1 | 2 | 3 | 4 |\n");
        LimitExceededException e = assertThrows(LimitExceededException.class,
                () -> svc.render(md.toString(), RenderOptions.v1Defaults()));
        assertEquals(LimitReason.TABLE_TOO_LARGE, e.getReason());
    }

    @Test
    void tableTooManyRowsIsRejected() {
        DocumentRenderService svc = service(new ResourceLimits(1_000_000, 64, 2, 20, 100, 5000));
        StringBuilder md = new StringBuilder("| h |\n|---|\n| 1 |\n| 2 |\n| 3 |\n");
        LimitExceededException e = assertThrows(LimitExceededException.class,
                () -> svc.render(md.toString(), RenderOptions.v1Defaults()));
        assertEquals(LimitReason.TABLE_TOO_LARGE, e.getReason());
    }

    @Test
    void tooManyImagesIsRejected() {
        DocumentRenderService svc = service(new ResourceLimits(1_000_000, 64, 100, 20, 2, 5000));
        String md = "![a](x.png) ![b](y.png) ![c](z.png)\n";
        LimitExceededException e = assertThrows(LimitExceededException.class,
                () -> svc.render(md, RenderOptions.v1Defaults()));
        assertEquals(LimitReason.TOO_MANY_IMAGES, e.getReason());
    }

    @Test
    void timeoutIsRejectedAndCacheStaysClean() {
        ResourceLimits limits = new ResourceLimits(1_000_000, 64, 100, 20, 100, 80);
        MarkdownParser slowParser = new MarkdownParser(limits) {
            @Override
            public ParseResult parse(String input) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new LimitExceededException(LimitReason.PROCESSING_TIMEOUT, "interrupted");
                }
                return super.parse(input);
            }
        };
        DocumentRenderService svc = service(limits, slowParser);
        LimitExceededException e = assertThrows(LimitExceededException.class,
                () -> svc.render("# hi\n", RenderOptions.v1Defaults()));
        assertEquals(LimitReason.PROCESSING_TIMEOUT, e.getReason());
        assertEquals(0, svc.getCache().size());
    }

    @Test
    void fixingInputAfterRejectionSucceedsAndIsCached() {
        DocumentRenderService svc = service(new ResourceLimits(10, 64, 100, 20, 100, 5000));
        assertThrows(LimitExceededException.class,
                () -> svc.render("this is far too long!!!", RenderOptions.v1Defaults()));
        // repaired input succeeds
        assertEquals(0, svc.getCache().size());
        assertNotNull(svc.render("# ok\n", RenderOptions.v1Defaults()));
        assertEquals(1, svc.getCache().size());
        // second identical call is served from cache
        assertNotNull(svc.render("# ok\n", RenderOptions.v1Defaults()));
        assertEquals(1, svc.getCache().size());
    }
}
