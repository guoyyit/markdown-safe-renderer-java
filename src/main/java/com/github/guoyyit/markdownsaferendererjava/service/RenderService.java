package com.github.guoyyit.markdownsaferendererjava.service;

import com.github.guoyyit.markdownsaferendererjava.cache.RenderCache;
import com.github.guoyyit.markdownsaferendererjava.limits.LimitExceededException;
import com.github.guoyyit.markdownsaferendererjava.limits.RejectionReason;
import com.github.guoyyit.markdownsaferendererjava.limits.ResourceLimits;
import com.github.guoyyit.markdownsaferendererjava.limits.RuntimeBudget;
import com.github.guoyyit.markdownsaferendererjava.options.AuditEvent;
import com.github.guoyyit.markdownsaferendererjava.options.OptionsAuditLog;
import com.github.guoyyit.markdownsaferendererjava.options.OptionsValidator;
import com.github.guoyyit.markdownsaferendererjava.options.OptionsViolation;
import com.github.guoyyit.markdownsaferendererjava.options.RenderOptions;
import com.github.guoyyit.markdownsaferendererjava.options.UnknownOptionPolicy;
import com.github.guoyyit.markdownsaferendererjava.parser.MarkdownParser;
import com.github.guoyyit.markdownsaferendererjava.parser.ParseResult;
import com.github.guoyyit.markdownsaferendererjava.render.HtmlRenderer;
import com.github.guoyyit.markdownsaferendererjava.toc.AnchorRegistry;
import com.github.guoyyit.markdownsaferendererjava.toc.HeadingNumbering;
import com.github.guoyyit.markdownsaferendererjava.toc.TocEntry;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Orchestrates parsing, sanitizing, numbering and rendering with caching,
 * resource limits and option auditing. Safe for concurrent use.
 *
 * <p>Cache keys cover the content, the effective options and the reserved
 * anchors, so any input change misses the cache and stale entries are never
 * served. Limit violations and failures are never cached.
 */
public final class RenderService {
    private final MarkdownParser parser;
    private final HeadingNumbering numbering;
    private final HtmlRenderer renderer;
    private final RenderCache<CachedRender> cache;
    private final ResourceLimits limits;
    private final OptionsValidator optionsValidator;
    private final OptionsAuditLog auditLog;

    public RenderService(MarkdownParser parser, HeadingNumbering numbering,
                         HtmlRenderer renderer, RenderCache<CachedRender> cache,
                         ResourceLimits limits, OptionsValidator optionsValidator,
                         OptionsAuditLog auditLog) {
        this.parser = parser;
        this.numbering = numbering;
        this.renderer = renderer;
        this.cache = cache;
        this.limits = limits;
        this.optionsValidator = optionsValidator;
        this.auditLog = auditLog;
    }

    public RenderResponse render(RenderRequest request) {
        RenderOptions options = request.getOptions();

        // ---- options validation & audit
        List<OptionsViolation> violations = optionsValidator.validate(options);
        List<String> unknownKeys = optionsValidator.unknownKeys(options);
        List<OptionsViolation> fatal = new ArrayList<>();
        for (OptionsViolation v : violations) {
            if (unknownKeys.contains(v.getKey())
                    && options.getUnknownPolicy() == UnknownOptionPolicy.IGNORE) {
                audit("option-ignored", v.toString());
            } else {
                fatal.add(v);
            }
        }
        if (!fatal.isEmpty()) {
            audit("options-rejected", fatal.toString());
            return new RenderResponse(RenderStatus.REJECTED_OPTIONS, null, null, null,
                    List.of(), null, "invalid options: " + fatal);
        }
        if (!options.getValues().isEmpty()) {
            audit("options-applied",
                    "version=" + options.getVersion() + " values=" + new TreeMap<>(options.getValues()));
        }

        // ---- input size limit (before caching: cheap and deterministic)
        String content = request.getContent() == null ? "" : request.getContent();
        if (content.length() > limits.getMaxInputLength()) {
            return new RenderResponse(RenderStatus.REJECTED_LIMIT, null, null, null,
                    List.of(), RejectionReason.INPUT_TOO_LONG,
                    "input length " + content.length() + " exceeds limit "
                            + limits.getMaxInputLength());
        }

        // ---- cache lookup / compute
        String key = cacheKey(content, options, request.getReservedAnchors());
        try {
            CachedRender cached = cache.getOrCompute(key,
                    () -> doRender(content, options, request.getReservedAnchors()));
            return new RenderResponse(RenderStatus.OK, cached.document(), cached.toc(),
                    cached.html(), cached.errors(), null, null);
        } catch (LimitExceededException e) {
            // never cached: no partial output, no polluted cache
            return new RenderResponse(RenderStatus.REJECTED_LIMIT, null, null, null,
                    List.of(), e.getReason(), e.getMessage());
        } catch (Exception e) {
            audit("render-exception", e.getClass().getSimpleName() + ": " + e.getMessage());
            return new RenderResponse(RenderStatus.FAILED, null, null, null,
                    List.of(), null, "render failed: " + e.getMessage());
        }
    }

    private CachedRender doRender(String content, RenderOptions options,
                                  List<String> reservedAnchors) {
        RuntimeBudget budget = new RuntimeBudget(limits);
        ParseResult parsed = parser.parse(content, budget);
        AnchorRegistry anchors = new AnchorRegistry();
        for (String reserved : reservedAnchors) {
            anchors.reserve(reserved);
        }
        TocEntry toc = numbering.assign(parsed.getDocument(), anchors);
        String html = renderer.render(parsed.getDocument(), toc, options);
        return new CachedRender(parsed.getDocument(), toc, html, parsed.getErrors());
    }

    /** Deterministic key: any change to content, options or anchors changes it. */
    static String cacheKey(String content, RenderOptions options, List<String> reservedAnchors) {
        StringBuilder sb = new StringBuilder();
        sb.append("v=").append(options.getVersion().getNumber()).append('\n');
        Map<String, Object> sorted = new TreeMap<>(options.getValues());
        for (Map.Entry<String, Object> e : sorted.entrySet()) {
            sb.append("o.").append(e.getKey()).append('=').append(e.getValue()).append('\n');
        }
        List<String> anchors = new ArrayList<>(reservedAnchors);
        java.util.Collections.sort(anchors);
        for (String a : anchors) {
            sb.append("r=").append(a).append('\n');
        }
        sb.append("---\n").append(content);
        return sha256(sb.toString());
    }

    private static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private void audit(String kind, String detail) {
        try {
            auditLog.record(new AuditEvent(Instant.now(), kind, detail));
        } catch (Exception ignored) {
            // auditing must never break rendering
        }
    }
}
