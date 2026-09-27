package com.github.guoyyit.markdownsaferendererjava.cache;

/** Configurable cache capacity and expiry. */
public final class CacheConfig {
    private final int maxEntries;
    private final long ttlMillis;

    public CacheConfig(int maxEntries, long ttlMillis) {
        this.maxEntries = maxEntries;
        this.ttlMillis = ttlMillis;
    }

    public static CacheConfig defaults() { return new CacheConfig(256, 60_000); }

    public int getMaxEntries() { return maxEntries; }
    public long getTtlMillis() { return ttlMillis; }
}
