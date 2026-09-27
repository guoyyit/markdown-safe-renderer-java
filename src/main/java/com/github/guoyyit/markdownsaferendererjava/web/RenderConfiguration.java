package com.github.guoyyit.markdownsaferendererjava.web;

import com.github.guoyyit.markdownsaferendererjava.cache.CacheConfig;
import com.github.guoyyit.markdownsaferendererjava.cache.LruRenderCache;
import com.github.guoyyit.markdownsaferendererjava.cache.RenderCache;
import com.github.guoyyit.markdownsaferendererjava.limits.ResourceLimits;
import com.github.guoyyit.markdownsaferendererjava.options.InMemoryAuditLog;
import com.github.guoyyit.markdownsaferendererjava.options.OptionsAuditLog;
import com.github.guoyyit.markdownsaferendererjava.options.OptionsValidator;
import com.github.guoyyit.markdownsaferendererjava.parser.MarkdownParser;
import com.github.guoyyit.markdownsaferendererjava.render.HtmlRenderer;
import com.github.guoyyit.markdownsaferendererjava.sanitize.HtmlSanitizer;
import com.github.guoyyit.markdownsaferendererjava.sanitize.SanitizerPolicy;
import com.github.guoyyit.markdownsaferendererjava.service.CachedRender;
import com.github.guoyyit.markdownsaferendererjava.service.RenderService;
import com.github.guoyyit.markdownsaferendererjava.toc.HeadingNumbering;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the render pipeline with externally configurable limits and cache. */
@Configuration
public class RenderConfiguration {

    @Bean
    public MarkdownParser markdownParser() { return new MarkdownParser(); }

    @Bean
    public HeadingNumbering headingNumbering() { return new HeadingNumbering(); }

    @Bean
    public HtmlSanitizer htmlSanitizer() {
        return new HtmlSanitizer(SanitizerPolicy.restrictive());
    }

    @Bean
    public HtmlRenderer htmlRenderer(HtmlSanitizer sanitizer) {
        return new HtmlRenderer(sanitizer);
    }

    @Bean
    public RenderCache<CachedRender> renderCache(
            @Value("${renderer.cache.max-entries:256}") int maxEntries,
            @Value("${renderer.cache.ttl-millis:60000}") long ttlMillis) {
        return new LruRenderCache<>(new CacheConfig(maxEntries, ttlMillis));
    }

    @Bean
    public ResourceLimits resourceLimits(
            @Value("${renderer.limits.max-input-length:1000000}") int maxInputLength,
            @Value("${renderer.limits.max-nesting-depth:64}") int maxNestingDepth,
            @Value("${renderer.limits.max-table-rows:200}") int maxTableRows,
            @Value("${renderer.limits.max-table-columns:50}") int maxTableColumns,
            @Value("${renderer.limits.max-images:100}") int maxImages,
            @Value("${renderer.limits.max-processing-millis:3000}") long maxProcessingMillis) {
        return new ResourceLimits(maxInputLength, maxNestingDepth, maxTableRows,
                maxTableColumns, maxImages, maxProcessingMillis);
    }

    @Bean
    public OptionsValidator optionsValidator() { return new OptionsValidator(); }

    @Bean
    public OptionsAuditLog optionsAuditLog() { return new InMemoryAuditLog(); }

    @Bean
    public RenderService renderService(MarkdownParser parser, HeadingNumbering numbering,
                                       HtmlRenderer renderer, RenderCache<CachedRender> cache,
                                       ResourceLimits limits, OptionsValidator validator,
                                       OptionsAuditLog auditLog) {
        return new RenderService(parser, numbering, renderer, cache, limits,
                validator, auditLog);
    }
}
