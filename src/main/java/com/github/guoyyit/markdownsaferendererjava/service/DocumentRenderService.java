package com.github.guoyyit.markdownsaferendererjava.service;

import com.github.guoyyit.markdownsaferendererjava.ast.Node;
import com.github.guoyyit.markdownsaferendererjava.ast.NodeType;
import com.github.guoyyit.markdownsaferendererjava.audit.AuditEntry;
import com.github.guoyyit.markdownsaferendererjava.audit.AuditLog;
import com.github.guoyyit.markdownsaferendererjava.cache.RenderCache;
import com.github.guoyyit.markdownsaferendererjava.limits.LimitExceededException;
import com.github.guoyyit.markdownsaferendererjava.limits.LimitReason;
import com.github.guoyyit.markdownsaferendererjava.limits.ResourceLimits;
import com.github.guoyyit.markdownsaferendererjava.options.OptionsValidator;
import com.github.guoyyit.markdownsaferendererjava.options.RenderOptions;
import com.github.guoyyit.markdownsaferendererjava.parser.MarkdownParser;
import com.github.guoyyit.markdownsaferendererjava.parser.ParseResult;
import com.github.guoyyit.markdownsaferendererjava.render.HtmlRenderer;
import com.github.guoyyit.markdownsaferendererjava.render.RenderResult;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Orchestrates option validation, caching, in-flight request coalescing,
 * timeout enforcement and post-parse resource counting.
 * <p>
 * Invariants:
 * <ul>
 *   <li>only fully completed renders enter the cache — exceptions, timeouts
 *       and resource exhaustion never publish partial results;</li>
 *   <li>cache key covers input content and normalized options, so a changed
 *       input (or changed options) always misses;</li>
 *   <li>concurrent identical requests share one computation but each keeps
 *       its own timeout;</li>
 *   <li>all rejections and unexpected errors are audited.</li>
 * </ul>
 */
public class DocumentRenderService {

    private final MarkdownParser parser;
    private final HtmlRenderer renderer;
    private final RenderCache cache;
    private final ResourceLimits limits;
    private final OptionsValidator optionsValidator;
    private final AuditLog auditLog;

    /** In-flight computations, keyed like the cache, to coalesce duplicates. */
    private final ConcurrentHashMap<String, CompletableFuture<RenderResult>> inFlight =
            new ConcurrentHashMap<>();
    private final AtomicLong threadSeq = new AtomicLong();

    public DocumentRenderService(MarkdownParser parser, HtmlRenderer renderer,
                                 RenderCache cache, ResourceLimits limits,
                                 OptionsValidator optionsValidator, AuditLog auditLog) {
        this.parser = parser;
        this.renderer = renderer;
        this.cache = cache;
        this.limits = limits;
        this.optionsValidator = optionsValidator;
        this.auditLog = auditLog;
    }

    public RenderResult render(String input, RenderOptions options) {
        RenderOptions normalized = optionsValidator.validate(options);
        String key = cacheKey(input, normalized);

        RenderResult cached = cache.get(key);
        if (cached != null) {
            return cached;
        }

        CompletableFuture<RenderResult> mine = new CompletableFuture<>();
        CompletableFuture<RenderResult> existing = inFlight.putIfAbsent(key, mine);
        if (existing != null) {
            return await(existing);
        }
        try {
            RenderResult result = executeWithTimeout(input, normalized);
            cache.put(key, result);
            mine.complete(result);
            return result;
        } catch (RuntimeException | Error e) {
            mine.completeExceptionally(e);
            throw e;
        } finally {
            inFlight.remove(key, mine);
        }
    }

    private RenderResult await(CompletableFuture<RenderResult> future) {
        try {
            return future.get(limits.timeoutMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw rejected(LimitReason.PROCESSING_TIMEOUT, "timeout while waiting for render");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw rejected(LimitReason.PROCESSING_TIMEOUT, "interrupted while waiting for render");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            if (cause instanceof Error err) {
                throw err;
            }
            throw new IllegalStateException(cause);
        }
    }

    private RenderResult executeWithTimeout(String input, RenderOptions options) {
        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "render-worker-" + threadSeq.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
        Future<RenderResult> future = executor.submit(() -> doRender(input, options));
        try {
            return future.get(limits.timeoutMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw rejected(LimitReason.PROCESSING_TIMEOUT,
                    "render exceeded " + limits.timeoutMillis() + "ms");
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw rejected(LimitReason.PROCESSING_TIMEOUT, "render interrupted");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof LimitExceededException le) {
                auditRejection(le);
                throw le;
            }
            if (cause instanceof VirtualMachineError vme) {
                LimitExceededException le = new LimitExceededException(
                        LimitReason.INTERNAL_RESOURCE_LIMIT,
                        "resource exhausted: " + vme.getClass().getSimpleName());
                auditRejection(le);
                throw le;
            }
            auditLog.record(AuditEntry.RENDER_ERROR, String.valueOf(cause));
            throw new IllegalStateException(cause);
        } finally {
            executor.shutdownNow();
        }
    }

    private RenderResult doRender(String input, RenderOptions options) {
        String text = input == null ? "" : input;
        if (text.length() > limits.maxInputChars()) {
            throw rejected(LimitReason.INPUT_TOO_LONG,
                    "input length " + text.length() + " exceeds " + limits.maxInputChars());
        }
        ParseResult parsed;
        try {
            parsed = parser.parse(text);
        } catch (LimitExceededException le) {
            auditRejection(le);
            throw le;
        }
        int images = countByType(parsed.document(), NodeType.IMAGE);
        if (images > limits.maxImages()) {
            throw rejected(LimitReason.TOO_MANY_IMAGES,
                    "image count " + images + " exceeds " + limits.maxImages());
        }
        RenderResult rendered = renderer.render(parsed.document(), options, text);
        return new RenderResult(parsed.document(), rendered.html(), rendered.toc(),
                rendered.tocHtml(), parsed.errors());
    }

    /** Iterative walk so pathological nesting cannot overflow the stack. */
    static int countByType(Node root, NodeType type) {
        int count = 0;
        Deque<Node> stack = new ArrayDeque<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            Node n = stack.pop();
            if (n.getType() == type) {
                count++;
            }
            for (Node child : n.getChildren()) {
                stack.push(child);
            }
        }
        return count;
    }

    private LimitExceededException rejected(LimitReason reason, String message) {
        LimitExceededException le = new LimitExceededException(reason, message);
        auditRejection(le);
        return le;
    }

    private void auditRejection(LimitExceededException le) {
        auditLog.record(AuditEntry.RENDER_REJECTED,
                le.getReason() + ": " + le.getMessage());
    }

    private static String cacheKey(String input, RenderOptions options) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(optionsString(options).getBytes(StandardCharsets.UTF_8));
            byte[] len = new byte[8];
            int n = input == null ? 0 : input.length();
            for (int i = 7; i >= 0; i--) {
                len[i] = (byte) (n & 0xFF);
                n >>>= 8;
            }
            md.update(len);
            md.update((input == null ? "" : input).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest()) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String optionsString(RenderOptions o) {
        return o.schemaVersion() + "|" + o.headingNumbers() + "|" + o.generateToc()
                + "|" + o.sanitizeHtml() + "|" + o.rawHtmlAllowed()
                + "|" + o.unknownOptionHandling() + "|" + o.extra();
    }

    public RenderCache getCache() {
        return cache;
    }

    public AuditLog getAuditLog() {
        return auditLog;
    }
}
