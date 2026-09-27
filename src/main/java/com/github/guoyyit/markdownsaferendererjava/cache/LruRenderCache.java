package com.github.guoyyit.markdownsaferendererjava.cache;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Bounded LRU cache with TTL. Fully constructed values only: computation
 * happens outside the lock and failures are never stored, so readers can
 * never observe a half-initialised or failed result.
 */
public final class LruRenderCache<V> implements RenderCache<V> {

    private static final class Entry<V> {
        final V value;
        final long createdAtMillis;

        Entry(V value, long createdAtMillis) {
            this.value = value;
            this.createdAtMillis = createdAtMillis;
        }
    }

    private final CacheConfig config;
    private final Map<String, Entry<V>> map;

    public LruRenderCache(CacheConfig config) {
        this.config = config;
        this.map = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Entry<V>> eldest) {
                return size() > config.getMaxEntries();
            }
        };
    }

    @Override
    public synchronized Optional<V> get(String key) {
        Entry<V> e = map.get(key);
        if (e == null) {
            return Optional.empty();
        }
        if (isExpired(e)) {
            map.remove(key);
            return Optional.empty();
        }
        return Optional.of(e.value);
    }

    @Override
    public V getOrCompute(String key, Supplier<V> supplier) {
        synchronized (this) {
            Entry<V> e = map.get(key);
            if (e != null) {
                if (!isExpired(e)) {
                    return e.value;
                }
                map.remove(key); // expired: force recompute
            }
        }
        // Compute outside the lock. A failure propagates and stores nothing,
        // so a timeout or resource exhaustion can never pollute the cache.
        V computed = supplier.get();
        if (computed == null) {
            throw new IllegalStateException("cache supplier returned null");
        }
        synchronized (this) {
            map.put(key, new Entry<>(computed, System.currentTimeMillis()));
        }
        return computed;
    }

    @Override
    public synchronized void invalidate(String key) {
        map.remove(key);
    }

    @Override
    public synchronized void clear() {
        map.clear();
    }

    @Override
    public synchronized int size() {
        return map.size();
    }

    private boolean isExpired(Entry<V> e) {
        long ttl = config.getTtlMillis();
        return ttl > 0 && System.currentTimeMillis() - e.createdAtMillis > ttl;
    }
}
