package com.github.guoyyit.markdownsaferendererjava.tests;

import com.github.guoyyit.markdownsaferendererjava.cache.CacheConfig;
import com.github.guoyyit.markdownsaferendererjava.cache.LruRenderCache;
import com.github.guoyyit.markdownsaferendererjava.cache.RenderCache;
import com.github.guoyyit.markdownsaferendererjava.limits.RejectionReason;
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
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Resource limits: distinguishable rejections, no partial output, clean cache. */
class ResourceLimitTest {

    private RenderService newService(ResourceLimits limits, RenderCache<CachedRender> cache) {
        return new RenderService(new MarkdownParser(), new HeadingNumbering(),
                new HtmlRenderer(new HtmlSanitizer(SanitizerPolicy.restrictive())),
                cache, limits, new OptionsValidator(), new InMemoryAuditLog());
    }

    private RenderResponse render(RenderService svc, String content) {
        return svc.render(new RenderRequest(content, RenderOptions.defaults(), List.of()));
    }

    @Test
    void inputTooLongRejected() {
        RenderCache<CachedRender> cache = new LruRenderCache<>(CacheConfig.defaults());
        RenderService svc = newService(
                new ResourceLimits(100, 64, 200, 50, 100, 3_000), cache);
        RenderResponse r = render(svc, "x".repeat(101));
        assertEquals(RenderStatus.REJECTED_LIMIT, r.getStatus());
        assertEquals(RejectionReason.INPUT_TOO_LONG, r.getRejectionReason());
        assertNull(r.getHtml(), "no partial output");
        assertNull(r.getDocument(), "no partial output");
        assertEquals(0, cache.size(), "rejection must not be cached");
    }

    @Test
    void nestingTooDeepRejected() {
        RenderCache<CachedRender> cache = new LruRenderCache<>(CacheConfig.defaults());
        RenderService svc = newService(
                new ResourceLimits(1_000_000, 5, 200, 50, 100, 3_000), cache);
        StringBuilder deep = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            deep.append("> ".repeat(1)).append("quote ").append(i).append("\n");
            // build nested quotes: each line one level deeper
        }
        StringBuilder nested = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            nested.append("> ".repeat(i + 1)).append("level ").append(i).append("\n");
        }
        RenderResponse r = render(svc, nested.toString());
        assertEquals(RenderStatus.REJECTED_LIMIT, r.getStatus());
        assertEquals(RejectionReason.NESTING_TOO_DEEP, r.getRejectionReason());
        assertNull(r.getHtml());
        assertEquals(0, cache.size());
    }

    @Test
    void tableTooManyRowsRejected() {
        RenderCache<CachedRender> cache = new LruRenderCache<>(CacheConfig.defaults());
        RenderService svc = newService(
                new ResourceLimits(1_000_000, 64, 5, 50, 100, 3_000), cache);
        StringBuilder md = new StringBuilder("| A |\n|---|\n");
        for (int i = 0; i < 10; i++) {
            md.append("| ").append(i).append(" |\n");
        }
        RenderResponse r = render(svc, md.toString());
        assertEquals(RenderStatus.REJECTED_LIMIT, r.getStatus());
        assertEquals(RejectionReason.TABLE_TOO_LARGE, r.getRejectionReason());
        assertNull(r.getHtml());
        assertEquals(0, cache.size());
    }

    @Test
    void tableTooManyColumnsRejected() {
        RenderCache<CachedRender> cache = new LruRenderCache<>(CacheConfig.defaults());
        RenderService svc = newService(
                new ResourceLimits(1_000_000, 64, 200, 3, 100, 3_000), cache);
        RenderResponse r = render(svc, "| a | b | c | d |\n|---|---|---|---|\n");
        assertEquals(RenderStatus.REJECTED_LIMIT, r.getStatus());
        assertEquals(RejectionReason.TABLE_TOO_LARGE, r.getRejectionReason());
        assertEquals(0, cache.size());
    }

    @Test
    void tooManyImagesRejected() {
        RenderCache<CachedRender> cache = new LruRenderCache<>(CacheConfig.defaults());
        RenderService svc = newService(
                new ResourceLimits(1_000_000, 64, 200, 50, 3, 3_000), cache);
        StringBuilder md = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            md.append("![img").append(i).append("](https://i.png/").append(i).append(")\n\n");
        }
        RenderResponse r = render(svc, md.toString());
        assertEquals(RenderStatus.REJECTED_LIMIT, r.getStatus());
        assertEquals(RejectionReason.TOO_MANY_IMAGES, r.getRejectionReason());
        assertNull(r.getHtml());
        assertEquals(0, cache.size());
    }

    @Test
    void timeoutRejected() {
        RenderCache<CachedRender> cache = new LruRenderCache<>(CacheConfig.defaults());
        // zero-ish processing budget: any non-trivial work trips the deadline
        RenderService svc = newService(
                new ResourceLimits(10_000_000, 10_000, 100_000, 1000, 100_000, 1), cache);
        StringBuilder md = new StringBuilder();
        for (int i = 0; i < 20_000; i++) {
            md.append("paragraph ").append(i).append("\n\n");
        }
        RenderResponse r = render(svc, md.toString());
        assertEquals(RenderStatus.REJECTED_LIMIT, r.getStatus());
        assertEquals(RejectionReason.PROCESSING_TIMEOUT, r.getRejectionReason());
        assertNull(r.getHtml(), "timeout must not produce partial output");
        assertEquals(0, cache.size(), "timeout must not pollute cache");
    }

    @Test
    void rejectionReasonsAreDistinguishable() {
        RenderCache<CachedRender> cache = new LruRenderCache<>(CacheConfig.defaults());
        RenderService svc = newService(
                new ResourceLimits(10, 64, 200, 50, 100, 3_000), cache);
        RenderResponse tooLong = render(svc, "this is definitely longer than ten chars");
        assertEquals(RejectionReason.INPUT_TOO_LONG, tooLong.getRejectionReason());
        assertNotEquals(RejectionReason.PROCESSING_TIMEOUT, tooLong.getRejectionReason());
    }

    @Test
    void withinLimitsSucceeds() {
        RenderCache<CachedRender> cache = new LruRenderCache<>(CacheConfig.defaults());
        RenderService svc = newService(ResourceLimits.defaults(), cache);
        RenderResponse r = render(svc, "# Title\n\nSome **text** and a [link](https://x.com).\n");
        assertEquals(RenderStatus.OK, r.getStatus());
        assertNotNull(r.getHtml());
        assertEquals(1, cache.size());
    }

    @Test
    void retryAfterRejectionSucceeds() {
        RenderCache<CachedRender> cache = new LruRenderCache<>(CacheConfig.defaults());
        RenderService svc = newService(
                new ResourceLimits(50, 64, 200, 50, 100, 3_000), cache);
        RenderResponse rejected = render(svc, "x".repeat(100));
        assertEquals(RenderStatus.REJECTED_LIMIT, rejected.getStatus());
        RenderResponse ok = render(svc, "# short");
        assertEquals(RenderStatus.OK, ok.getStatus(), "service recovers after rejection");
    }
}
