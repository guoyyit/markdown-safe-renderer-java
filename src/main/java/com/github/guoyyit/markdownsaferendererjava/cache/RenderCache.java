package com.github.guoyyit.markdownsaferendererjava.cache;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Concurrent-safe render cache. Never returns stale or half-initialised values;
 * a changed input maps to a different key, so old entries simply miss.
 *
 * @param <V> cached value type
 */
public interface RenderCache<V> {
    Optional<V> get(String key);

    /**
     * Returns the cached value or computes, stores and returns it.
     * If the supplier throws, nothing is cached and the exception propagates.
     */
    V getOrCompute(String key, Supplier<V> supplier);

    void invalidate(String key);

    void clear();

    int size();
}
