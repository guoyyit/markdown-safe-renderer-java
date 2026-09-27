package com.github.guoyyit.markdownsaferendererjava.tests;

import com.github.guoyyit.markdownsaferendererjava.cache.CacheConfig;
import com.github.guoyyit.markdownsaferendererjava.cache.LruRenderCache;
import com.github.guoyyit.markdownsaferendererjava.cache.RenderCache;
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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Cache semantics: hit, invalidation on change, TTL, capacity, concurrency. */
class CacheConcurrencyTest {

    private RenderService newService(RenderCache<CachedRender> cache) {
        return new RenderService(new MarkdownParser(), new HeadingNumbering(),
                new HtmlRenderer(new HtmlSanitizer(SanitizerPolicy.restrictive())),
                cache, ResourceLimits.defaults(), new OptionsValidator(),
                new InMemoryAuditLog());
    }

    @Test
    void repeatedRenderHitsCache() {
        RenderCache<CachedRender> cache = new LruRenderCache<>(CacheConfig.defaults());
        RenderService svc = newService(cache);
        RenderRequest req = new RenderRequest("# A\ntext", RenderOptions.defaults(), List.of());
        RenderResponse r1 = svc.render(req);
        RenderResponse r2 = svc.render(req);
        assertEquals(RenderStatus.OK, r1.getStatus());
        assertEquals(r1.getHtml(), r2.getHtml());
        assertEquals(1, cache.size(), "one entry cached");
    }

    @Test
    void changedInputMissesCache() {
        RenderCache<CachedRender> cache = new LruRenderCache<>(CacheConfig.defaults());
        RenderService svc = newService(cache);
        svc.render(new RenderRequest("# A", RenderOptions.defaults(), List.of()));
        svc.render(new RenderRequest("# B", RenderOptions.defaults(), List.of()));
        assertEquals(2, cache.size(), "different input -> different key");
    }

    @Test
    void changedOptionsMissCache() {
        RenderCache<CachedRender> cache = new LruRenderCache<>(CacheConfig.defaults());
        RenderService svc = newService(cache);
        svc.render(new RenderRequest("# A", RenderOptions.defaults(), List.of()));
        svc.render(new RenderRequest("# A",
                new RenderOptions(com.github.guoyyit.markdownsaferendererjava.options.OptionsVersion.V1,
                        java.util.Map.of("toc", false),
                        com.github.guoyyit.markdownsaferendererjava.options.UnknownOptionPolicy.IGNORE),
                List.of()));
        assertEquals(2, cache.size(), "different options -> different key");
    }

    @Test
    void ttlExpiryForcesRecompute() throws Exception {
        RenderCache<String> cache = new LruRenderCache<>(new CacheConfig(10, 50));
        AtomicInteger computations = new AtomicInteger();
        cache.getOrCompute("k", () -> { computations.incrementAndGet(); return "v1"; });
        cache.getOrCompute("k", () -> { computations.incrementAndGet(); return "v2"; });
        assertEquals(1, computations.get(), "second call served from cache");
        Thread.sleep(80); // beyond TTL
        String v = cache.getOrCompute("k", () -> { computations.incrementAndGet(); return "v3"; });
        assertEquals("v3", v, "expired entry recomputed");
        assertEquals(2, computations.get());
    }

    @Test
    void capacityEvictsEldest() {
        RenderCache<String> cache = new LruRenderCache<>(new CacheConfig(2, 0));
        cache.getOrCompute("a", () -> "1");
        cache.getOrCompute("b", () -> "2");
        cache.getOrCompute("c", () -> "3");
        assertEquals(2, cache.size());
        assertTrue(cache.get("a").isEmpty(), "eldest evicted");
        assertTrue(cache.get("c").isPresent());
    }

    @Test
    void failedComputationIsNotCached() {
        RenderCache<String> cache = new LruRenderCache<>(CacheConfig.defaults());
        AtomicInteger attempts = new AtomicInteger();
        assertThrows(IllegalStateException.class, () ->
                cache.getOrCompute("k", () -> {
                    attempts.incrementAndGet();
                    throw new IllegalStateException("boom");
                }));
        assertEquals(0, cache.size(), "failure must not be cached");
        String v = cache.getOrCompute("k", () -> { attempts.incrementAndGet(); return "ok"; });
        assertEquals("ok", v);
        assertEquals(2, attempts.get(), "recomputed after failure");
    }

    @Test
    void concurrentSameKeyReturnsConsistentValue() throws Exception {
        RenderCache<String> cache = new LruRenderCache<>(CacheConfig.defaults());
        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        ConcurrentLinkedQueue<String> results = new ConcurrentLinkedQueue<>();
        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                start.await();
                results.add(cache.getOrCompute("shared", () -> "value"));
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        assertEquals(threads, results.size());
        for (String r : results) {
            assertEquals("value", r, "no half-initialised or divergent values");
        }
    }

    @Test
    void concurrentServiceRendersAreConsistent() throws Exception {
        RenderService svc = newService(new LruRenderCache<>(new CacheConfig(64, 60_000)));
        int threads = 12;
        int perThread = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<String>> futures = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            final int id = t;
            Callable<String> task = () -> {
                StringBuilder last = new StringBuilder();
                for (int i = 0; i < perThread; i++) {
                    String doc = "# Doc" + (id % 3) + "\n\ncontent " + (i % 2);
                    RenderResponse r = svc.render(new RenderRequest(doc,
                            RenderOptions.defaults(), List.of()));
                    assertEquals(RenderStatus.OK, r.getStatus());
                    last.setLength(0);
                    last.append(r.getHtml());
                }
                return last.toString();
            };
            futures.add(pool.submit(task));
        }
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
        // same doc id -> same html across threads
        List<String> outputs = new ArrayList<>();
        for (Future<String> f : futures) {
            outputs.add(f.get());
        }
        for (int a = 0; a < threads; a++) {
            for (int b = a + 1; b < threads; b++) {
                if (a % 3 == b % 3) {
                    assertEquals(outputs.get(a), outputs.get(b),
                            "same input must render identically under concurrency");
                }
            }
        }
    }

    @Test
    void invalidationWorks() {
        RenderCache<String> cache = new LruRenderCache<>(CacheConfig.defaults());
        cache.getOrCompute("k", () -> "v");
        assertTrue(cache.get("k").isPresent());
        cache.invalidate("k");
        assertTrue(cache.get("k").isEmpty());
    }
}
