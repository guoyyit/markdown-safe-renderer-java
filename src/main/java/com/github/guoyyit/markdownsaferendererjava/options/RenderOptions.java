package com.github.guoyyit.markdownsaferendererjava.options;

import java.util.Collections;
import java.util.Map;

/**
 * Rendering options attached to a request. Immutable after construction.
 * Option keys are versioned: a key is only meaningful for the schema versions
 * that declare it, and new keys always default to behaviour-preserving values.
 */
public final class RenderOptions {

    /** Well-known V1 option keys. */
    public static final String KEY_TOC = "toc";
    public static final String KEY_HEADING_NUMBERING = "headingNumbering";
    public static final String KEY_ALLOW_RAW_HTML = "allowRawHtml";

    private final OptionsVersion version;
    private final Map<String, Object> values;
    private final UnknownOptionPolicy unknownPolicy;

    public RenderOptions(OptionsVersion version, Map<String, Object> values,
                         UnknownOptionPolicy unknownPolicy) {
        this.version = version;
        this.values = values == null ? Map.of() : Map.copyOf(values);
        this.unknownPolicy = unknownPolicy == null ? UnknownOptionPolicy.IGNORE : unknownPolicy;
    }

    public static RenderOptions defaults() {
        return new RenderOptions(OptionsVersion.latest(), Collections.emptyMap(),
                UnknownOptionPolicy.IGNORE);
    }

    public OptionsVersion getVersion() { return version; }
    public Map<String, Object> getValues() { return values; }
    public UnknownOptionPolicy getUnknownPolicy() { return unknownPolicy; }

    public String getString(String key, String fallback) {
        Object v = values.get(key);
        return v instanceof String s ? s : fallback;
    }

    public boolean getBoolean(String key, boolean fallback) {
        Object v = values.get(key);
        return v instanceof Boolean b ? b : fallback;
    }

    public int getInt(String key, int fallback) {
        Object v = values.get(key);
        return v instanceof Number n ? n.intValue() : fallback;
    }
}
