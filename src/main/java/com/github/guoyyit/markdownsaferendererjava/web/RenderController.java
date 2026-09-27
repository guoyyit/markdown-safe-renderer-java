package com.github.guoyyit.markdownsaferendererjava.web;

import com.github.guoyyit.markdownsaferendererjava.audit.AuditEntry;
import com.github.guoyyit.markdownsaferendererjava.options.RenderOptions;
import com.github.guoyyit.markdownsaferendererjava.options.UnknownOptionHandling;
import com.github.guoyyit.markdownsaferendererjava.render.RenderResult;
import com.github.guoyyit.markdownsaferendererjava.service.DocumentRenderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HTTP entry point for document rendering.
 */
@RestController
@RequestMapping("/api/render")
public class RenderController {

    private final DocumentRenderService service;

    public RenderController(DocumentRenderService service) {
        this.service = service;
    }

    @PostMapping
    public RenderResult render(@RequestBody RenderRequest request) {
        RenderOptions options = toOptions(request == null ? null : request.options());
        String content = request == null ? "" : request.content();
        return service.render(content, options);
    }

    @GetMapping("/audit")
    public List<AuditEntry> audit() {
        return service.getAuditLog().snapshot();
    }

    @DeleteMapping("/cache")
    public ResponseEntity<Void> invalidateCache() {
        service.getCache().invalidateAll();
        return ResponseEntity.noContent().build();
    }

    /** Maps the open options map onto the versioned {@link RenderOptions}. */
    static RenderOptions toOptions(Map<String, Object> raw) {
        if (raw == null || raw.isEmpty()) {
            return RenderOptions.v1Defaults();
        }
        RenderOptions base = RenderOptions.v1Defaults();
        int schema = base.schemaVersion();
        boolean headingNumbers = base.headingNumbers();
        boolean generateToc = base.generateToc();
        boolean sanitizeHtml = base.sanitizeHtml();
        boolean rawHtmlAllowed = base.rawHtmlAllowed();
        UnknownOptionHandling handling = base.unknownOptionHandling();
        Map<String, String> extra = new HashMap<>();
        for (Map.Entry<String, Object> e : raw.entrySet()) {
            String key = e.getKey();
            Object value = e.getValue();
            switch (key) {
                case "schemaVersion" -> schema = toInt(key, value);
                case "headingNumbers" -> headingNumbers = toBool(key, value);
                case "generateToc" -> generateToc = toBool(key, value);
                case "sanitizeHtml" -> sanitizeHtml = toBool(key, value);
                case "rawHtmlAllowed" -> rawHtmlAllowed = toBool(key, value);
                case "unknownOptionHandling" -> handling = UnknownOptionHandling
                        .valueOf(String.valueOf(value).toUpperCase(java.util.Locale.ROOT));
                default -> extra.put(key, value == null ? null : String.valueOf(value));
            }
        }
        return new RenderOptions(schema, headingNumbers, generateToc, sanitizeHtml,
                rawHtmlAllowed, handling, extra);
    }

    private static int toInt(String key, Object value) {
        if (value instanceof Number num) {
            return num.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("option '" + key + "' must be an integer");
        }
    }

    private static boolean toBool(String key, Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        String s = String.valueOf(value);
        if ("true".equalsIgnoreCase(s) || "false".equalsIgnoreCase(s)) {
            return Boolean.parseBoolean(s);
        }
        throw new IllegalArgumentException("option '" + key + "' must be a boolean");
    }
}
