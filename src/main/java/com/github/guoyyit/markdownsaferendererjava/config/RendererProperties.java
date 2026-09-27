package com.github.guoyyit.markdownsaferendererjava.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalized configuration: resource limits, cache policy, sanitize policy.
 */
@ConfigurationProperties(prefix = "renderer")
public class RendererProperties {

    private int maxInputChars = 1_000_000;
    private int maxDepth = 64;
    private int maxTableRows = 1_000;
    private int maxTableCols = 100;
    private int maxImages = 100;
    private long timeoutMillis = 5_000;

    private int cacheCapacity = 256;
    private long cacheTtlMillis = 60_000;

    public int getMaxInputChars() { return maxInputChars; }
    public void setMaxInputChars(int maxInputChars) { this.maxInputChars = maxInputChars; }
    public int getMaxDepth() { return maxDepth; }
    public void setMaxDepth(int maxDepth) { this.maxDepth = maxDepth; }
    public int getMaxTableRows() { return maxTableRows; }
    public void setMaxTableRows(int maxTableRows) { this.maxTableRows = maxTableRows; }
    public int getMaxTableCols() { return maxTableCols; }
    public void setMaxTableCols(int maxTableCols) { this.maxTableCols = maxTableCols; }
    public int getMaxImages() { return maxImages; }
    public void setMaxImages(int maxImages) { this.maxImages = maxImages; }
    public long getTimeoutMillis() { return timeoutMillis; }
    public void setTimeoutMillis(long timeoutMillis) { this.timeoutMillis = timeoutMillis; }
    public int getCacheCapacity() { return cacheCapacity; }
    public void setCacheCapacity(int cacheCapacity) { this.cacheCapacity = cacheCapacity; }
    public long getCacheTtlMillis() { return cacheTtlMillis; }
    public void setCacheTtlMillis(long cacheTtlMillis) { this.cacheTtlMillis = cacheTtlMillis; }
}
