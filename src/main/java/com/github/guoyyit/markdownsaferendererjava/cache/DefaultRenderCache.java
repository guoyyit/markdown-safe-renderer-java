package com.github.guoyyit.markdownsaferendererjava.cache;

import com.github.guoyyit.markdownsaferendererjava.render.RenderResult;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bounded LRU cache with entry TTL.
 * <p>
 * Concurrency and freshness guarantees:
 * <ul>
 *   <li>all map access is serialized by the instance monitor; the returned
 *       {@link RenderResult} is immutable, so reading it outside the lock is safe;</li>
 *   <li>only fully built results are ever inserted (via {@link #put}), so no
 *       half-initialized value can be observed;</li>
 *   <li>expired entries are treated as misses and lazily removed; expired
 *       results are never returned;</li>
 *   <li>when capacity is exceeded the least-recently-used entry is evicted.</li>
 * </ul>
 */
public class DefaultRenderCache implements RenderCache {

    private static final class Entry {
        final RenderResult result;
        final long expireAtNanos;

        Entry(RenderResult result, long ttlMillis) {
            this.result = result;
            this.expireAtNanos = System.nanoTime()
                    + java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(ttlMillis);
        }

        boolean expired() {
            return System.nanoTime() - expireAtNanos >= 0;
        }
    }

    private final int capacity;
    private final long ttlMillis;
    private final LinkedHashMap<String, Entry> store;

    public DefaultRenderCache(int capacity, long ttlMillis) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        if (ttlMillis <= 0) {
            throw new IllegalArgumentException("ttlMillis must be positive");
        }
        this.capacity = capacity;
        this.ttlMillis = ttlMillis;
        this.store = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Entry> eldest) {
                return size() > DefaultRenderCache.this.capacity;
            }
        };
    }

    @Override
    public RenderResult get(String key) {
        synchronized (store) {
            Entry e = store.get(key); // also records access for LRU
            if (e == null) {
                return null;
            }
            if (e.expired()) {
                store.remove(key);
                return null;
            }
            return e.result;
        }
    }

    @Override
    public void put(String key, RenderResult result) {
        if (result == null) {
            throw new IllegalArgumentException("null results are never cached");
        }
        synchronized (store) {
            // opportunistic expiry sweep so the capacity is not wasted on dead entries
            Iterator<Map.Entry<String, Entry>> it = store.entrySet().iterator();
            while (it.hasNext()) {
                if (it.next().getValue().expired()) {
                    it.remove();
                }
            }
            store.put(key, new Entry(result, ttlMillis));
        }
    }

    @Override
    public int size() {
        synchronized (store) {
            return store.size();
        }
    }

    @Override
    public void invalidateAll() {
        synchronized (store) {
            store.clear();
        }
    }
}
