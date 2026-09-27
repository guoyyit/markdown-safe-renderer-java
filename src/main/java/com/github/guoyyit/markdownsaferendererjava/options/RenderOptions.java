package com.github.guoyyit.markdownsaferendererjava.options;

import java.util.Map;

/**
 * Versioned render options. {@code schemaVersion} enables forward/backward
 * compatibility: newly added option fields default to values that reproduce
 * historical output.
 */
public record RenderOptions(
        int schemaVersion,
        boolean headingNumbers,
        boolean generateToc,
        boolean sanitizeHtml,
        boolean rawHtmlAllowed,
        UnknownOptionHandling unknownOptionHandling,
        Map<String, String> extra) {

    /** Earliest schema; all newer defaults must preserve this output. */
    public static final int SCHEMA_V1 = 1;
    public static final int CURRENT_SCHEMA = SCHEMA_V1;

    public static RenderOptions v1Defaults() {
        return new RenderOptions(SCHEMA_V1, true, true, true, false,
                UnknownOptionHandling.REJECT, Map.of());
    }
}
