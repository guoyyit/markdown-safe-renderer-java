package com.github.guoyyit.markdownsaferendererjava.config;

import com.github.guoyyit.markdownsaferendererjava.audit.AuditLog;
import com.github.guoyyit.markdownsaferendererjava.cache.DefaultRenderCache;
import com.github.guoyyit.markdownsaferendererjava.cache.RenderCache;
import com.github.guoyyit.markdownsaferendererjava.limits.ResourceLimits;
import com.github.guoyyit.markdownsaferendererjava.options.OptionsValidator;
import com.github.guoyyit.markdownsaferendererjava.parser.MarkdownParser;
import com.github.guoyyit.markdownsaferendererjava.render.HtmlRenderer;
import com.github.guoyyit.markdownsaferendererjava.sanitize.HtmlSanitizer;
import com.github.guoyyit.markdownsaferendererjava.sanitize.SanitizePolicy;
import com.github.guoyyit.markdownsaferendererjava.service.DocumentRenderService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires renderer beans from externalized properties.
 */
@Configuration
@EnableConfigurationProperties(RendererProperties.class)
public class RendererConfiguration {

    @Bean
    public ResourceLimits resourceLimits(RendererProperties props) {
        return new ResourceLimits(
                props.getMaxInputChars(),
                props.getMaxDepth(),
                props.getMaxTableRows(),
                props.getMaxTableCols(),
                props.getMaxImages(),
                props.getTimeoutMillis());
    }

    @Bean
    public MarkdownParser markdownParser(ResourceLimits limits) {
        return new MarkdownParser(limits);
    }

    @Bean
    public HtmlSanitizer htmlSanitizer() {
        return new HtmlSanitizer(SanitizePolicy.defaultPolicy());
    }

    @Bean
    public HtmlRenderer htmlRenderer(HtmlSanitizer sanitizer) {
        return new HtmlRenderer(sanitizer);
    }

    @Bean
    public RenderCache renderCache(RendererProperties props) {
        return new DefaultRenderCache(props.getCacheCapacity(), props.getCacheTtlMillis());
    }

    @Bean
    public AuditLog auditLog() {
        return new AuditLog();
    }

    @Bean
    public OptionsValidator optionsValidator(AuditLog auditLog) {
        return new OptionsValidator(auditLog);
    }

    @Bean
    public DocumentRenderService documentRenderService(MarkdownParser parser,
                                                       HtmlRenderer renderer,
                                                       RenderCache cache,
                                                       ResourceLimits limits,
                                                       OptionsValidator validator,
                                                       AuditLog auditLog) {
        return new DocumentRenderService(parser, renderer, cache, limits, validator, auditLog);
    }
}
