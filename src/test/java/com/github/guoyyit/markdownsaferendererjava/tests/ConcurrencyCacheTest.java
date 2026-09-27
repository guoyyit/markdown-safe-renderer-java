package com.github.guoyyit.markdownsaferendererjava.tests;

import com.github.guoyyit.markdownsaferendererjava.audit.AuditLog;
import com.github.guoyyit.markdownsaferendererjava.cache.DefaultRenderCache;
import com.github.guoyyit.markdownsaferendererjava.limits.ResourceLimits;
import com.github.guoyyit.markdownsaferendererjava.options.OptionsValidator;
import com.github.guoyyit.markdownsaferendererjava.options.RenderOptions;
import com.github.guoyyit.markdownsaferendererjava.parser.MarkdownParser;
import com.github.guoyyit.markdownsaferendererjava.parser.ParseResult;
import com.github.guoyyit.markdownsaferendererjava.render.HtmlRenderer;
import com.github.guoyyit.markdownsaferendererjava.render.RenderResult;
import com.github.guoyyit.markdownsaferendererjava.sanitize.HtmlSanitizer;
import com.github.guoyyit.markdownsaferendererjava.sanitize.SanitizePolicy;
import com.github.guoyyit.markdownsaferendererjava.service.DocumentRenderService;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ConcurrencyCacheTest {

    private static final class CountingParser extends MarkdownParser {
        final AtomicInteger parses = new AtomicInteger();

        CountingParser(ResourceLimits limits) {
            super(limits);
        }

        @Override
        public ParseResult parse(String input) {
            parses.incrementAndGet();
            try {
                Thread.sleep(30); // widen the race window
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return super.parse(input);
        }
    }

    private DocumentRenderService service(DefaultRenderCache cache, MarkdownParser parser,
                                          ResourceLimits limits) {
        return new DocumentRenderService(parser,
                new HtmlRenderer(new HtmlSanitizer(SanitizePolicy.defaultPolicy())),
                cache, limits, new OptionsValidator(new AuditLog()), new AuditLog());
    }

    @Test
    void concurrentIdenticalRequestsShareOneComputation() throws Exception {
        ResourceLimits limits = ResourceLimits.defaults();
        CountingParser parser = new CountingParser(limits);
        DocumentRenderService svc = service(new DefaultRenderCache(64, 60_000), parser, limits);

        int threads = 24;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CyclicBarrier barrier = new CyclicBarrier(threads);
        CountDownLatch done = new CountDownLatch(threads);
        Set<String> htmls = Collections.newSetFromMap(new ConcurrentHashMap<>());
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    barrier.await();
                    RenderResult r = svc.render("# Same Title\n\nbody\n", RenderOptions.v1Defaults());
                    htmls.add(r.html());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    done.countDown();
                }
            });
        }
        assertTrue(done.await(10, TimeUnit.SECONDS));
        pool.shutdown();
        assertEquals(1, parser.parses.get(), "identical concurrent requests must coalesce");
        assertEquals(1, htmls.size(), "all callers must receive the same result");
    }

    @Test
    void changedInputBypassesAndReplacesOldCache() {
        ResourceLimits limits = ResourceLimits.defaults();
        DefaultRenderCache cache = new DefaultRenderCache(64, 60_000);
        DocumentRenderService svc = service(cache, new MarkdownParser(limits), limits);

        RenderResult r1 = svc.render("# Version One\n", RenderOptions.v1Defaults());
        assertEquals(1, cache.size());
        RenderResult r1Again = svc.render("# Version One\n", RenderOptions.v1Defaults());
        assertSame(r1, r1Again, "unchanged input is served from cache");

        RenderResult r2 = svc.render("# Version Two\n", RenderOptions.v1Defaults());
        assertEquals(2, cache.size());
        assertNotEquals(r1.html(), r2.html());
        // old entry still valid, new input did not corrupt it
        assertSame(r1, svc.render("# Version One\n", RenderOptions.v1Defaults()));
    }

    @Test
    void changedOptionsBypassCache() {
        ResourceLimits limits = ResourceLimits.defaults();
        DefaultRenderCache cache = new DefaultRenderCache(64, 60_000);
        DocumentRenderService svc = service(cache, new MarkdownParser(limits), limits);

        svc.render("# Hi\n", RenderOptions.v1Defaults());
        RenderOptions noNumbers = new RenderOptions(RenderOptions.SCHEMA_V1,
                false, false, true, false,
                RenderOptions.v1Defaults().unknownOptionHandling(), java.util.Map.of());
        RenderResult r2 = svc.render("# Hi\n", noNumbers);
        assertEquals(2, cache.size());
        assertFalse(r2.html().contains("heading-number"));
    }

    @Test
    void capacityEvictsLeastRecentlyUsed() {
        DefaultRenderCache cache = new DefaultRenderCache(2, 60_000);
        RenderResult a = svcResult("a");
        RenderResult b = svcResult("b");
        RenderResult c = svcResult("c");
        cache.put("a", a);
        cache.put("b", b);
        assertNotNull(cache.get("a")); // touch a so b becomes LRU
        cache.put("c", c);
        assertEquals(2, cache.size());
        assertNull(cache.get("b"), "LRU entry must be evicted");
        assertNotNull(cache.get("a"));
        assertNotNull(cache.get("c"));
    }

    @Test
    void expiredEntriesAreNeverReturned() throws InterruptedException {
        DefaultRenderCache cache = new DefaultRenderCache(16, 30);
        cache.put("k", svcResult("x"));
        assertNotNull(cache.get("k"));
        Thread.sleep(60);
        assertNull(cache.get("k"), "expired entry must behave as a miss");
        assertEquals(0, cache.size());
    }

    @Test
    void cacheOnlyStoresCompletedResults() {
        DefaultRenderCache cache = new DefaultRenderCache(16, 60_000);
        assertThrows(IllegalArgumentException.class, () -> cache.put("k", null));
        assertEquals(0, cache.size());
    }

    @Test
    void cacheIsThreadSafeUnderMixedLoad() throws Exception {
        DefaultRenderCache cache = new DefaultRenderCache(128, 60_000);
        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch done = new CountDownLatch(threads);
        for (int t = 0; t < threads; t++) {
            final int base = t * 1000;
            pool.submit(() -> {
                try {
                    for (int i = 0; i < 500; i++) {
                        String key = "k" + (base + i);
                        cache.put(key, svcResult(key));
                        cache.get(key);
                    }
                } finally {
                    done.countDown();
                }
            });
        }
        assertTrue(done.await(15, TimeUnit.SECONDS));
        pool.shutdown();
        assertTrue(cache.size() <= 128);
    }

    private static RenderResult svcResult(String marker) {
        return new RenderResult(null, "<p>" + marker + "</p>",
                java.util.List.of(), "", java.util.List.of());
    }
}
