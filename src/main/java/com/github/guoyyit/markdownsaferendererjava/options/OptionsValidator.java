package com.github.guoyyit.markdownsaferendererjava.options;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validates options against a schema version. Unknown keys are reported;
 * whether they are fatal depends on the request's UnknownOptionPolicy.
 */
public final class OptionsValidator {

    private enum Type { BOOLEAN, STRING, INTEGER }

    /** Keys declared by each schema version. New versions append, never remove. */
    private static final Map<OptionsVersion, Map<String, Type>> SCHEMAS = Map.of(
            OptionsVersion.V1, Map.of(
                    RenderOptions.KEY_TOC, Type.BOOLEAN,
                    RenderOptions.KEY_HEADING_NUMBERING, Type.BOOLEAN,
                    RenderOptions.KEY_ALLOW_RAW_HTML, Type.BOOLEAN));

    /** Validates the given options. Unknown keys are reported as violations. */
    public List<OptionsViolation> validate(RenderOptions options) {
        List<OptionsViolation> violations = new ArrayList<>();
        Map<String, Type> schema = SCHEMAS.get(options.getVersion());
        if (schema == null) {
            violations.add(new OptionsViolation("version",
                    "no schema for version " + options.getVersion()));
            return violations;
        }
        for (Map.Entry<String, Object> e : options.getValues().entrySet()) {
            String key = e.getKey();
            Type type = schema.get(key);
            if (type == null) {
                violations.add(new OptionsViolation(key,
                        "unknown option for version " + options.getVersion()));
                continue;
            }
            if (!typeMatches(type, e.getValue())) {
                violations.add(new OptionsViolation(key,
                        "expected " + type + " but got "
                                + (e.getValue() == null ? "null" : e.getValue().getClass().getSimpleName())));
            }
        }
        return violations;
    }

    /** Splits violations into unknown-key violations and other violations. */
    public List<String> unknownKeys(RenderOptions options) {
        Map<String, Type> schema = SCHEMAS.get(options.getVersion());
        List<String> unknown = new ArrayList<>();
        if (schema == null) {
            return unknown;
        }
        for (String key : options.getValues().keySet()) {
            if (!schema.containsKey(key)) {
                unknown.add(key);
            }
        }
        return unknown;
    }

    public Set<String> knownKeys(OptionsVersion version) {
        Map<String, Type> schema = SCHEMAS.get(version);
        return schema == null ? Set.of() : schema.keySet();
    }

    private static boolean typeMatches(Type type, Object value) {
        return switch (type) {
            case BOOLEAN -> value instanceof Boolean;
            case STRING -> value instanceof String;
            case INTEGER -> value instanceof Number;
        };
    }
}
