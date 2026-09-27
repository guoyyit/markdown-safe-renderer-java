package com.github.guoyyit.markdownsaferendererjava.cache;

import com.github.guoyyit.markdownsaferendererjava.render.RenderResult;

/**
 * Thread-safe render result cache with configurable capacity and TTL.
 * Only fully completed results are ever visible; a changed input always
 * misses (key includes content hash + options).
 */
public interface RenderCache {

    /** Returns null on miss or expiry; never returns a half-initialized value. */
    RenderResult get(String key);

    /** Publishes only a fully built result; failed renders never enter the cache. */
    void put(String key, RenderResult result);

    /** Current number of cached entries (mainly for tests/metrics). */
    int size();

    /** Drops all entries. */
    void invalidateAll();
}
