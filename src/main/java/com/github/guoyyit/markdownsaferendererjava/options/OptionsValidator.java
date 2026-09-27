package com.github.guoyyit.markdownsaferendererjava.options;

import com.github.guoyyit.markdownsaferendererjava.audit.AuditEntry;
import com.github.guoyyit.markdownsaferendererjava.audit.AuditLog;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Validates and normalizes incoming options.
 * <p>
 * Compatibility rules:
 * <ul>
 *   <li>schema versions below 1 or above the supported version are rejected;</li>
 *   <li>unknown option keys are rejected or ignored according to
 *       {@link UnknownOptionHandling};</li>
 *   <li>when an option is switched relative to the V1 defaults, the change is
 *       recorded in the audit log.</li>
 * </ul>
 */
public class OptionsValidator {

    /** Extra (forward-compat) keys understood by V1; everything else is unknown. */
    private static final Set<String> KNOWN_EXTRA = Set.of();

    private final AuditLog auditLog;

    public OptionsValidator(AuditLog auditLog) {
        this.auditLog = auditLog;
    }

    public RenderOptions validate(RenderOptions incoming) {
        RenderOptions base = RenderOptions.v1Defaults();
        if (incoming == null) {
            return base;
        }
        if (incoming.schemaVersion() < RenderOptions.SCHEMA_V1) {
            throw new IllegalArgumentException(
                    "unsupported schema version: " + incoming.schemaVersion());
        }
        if (incoming.schemaVersion() > RenderOptions.CURRENT_SCHEMA) {
            throw new IllegalArgumentException(
                    "schema version " + incoming.schemaVersion()
                            + " is newer than supported " + RenderOptions.CURRENT_SCHEMA);
        }

        UnknownOptionHandling handling = incoming.unknownOptionHandling() == null
                ? UnknownOptionHandling.REJECT : incoming.unknownOptionHandling();

        Map<String, String> extras = new HashMap<>();
        if (incoming.extra() != null) {
            for (Map.Entry<String, String> e : incoming.extra().entrySet()) {
                if (KNOWN_EXTRA.contains(e.getKey())) {
                    extras.put(e.getKey(), e.getValue());
                } else {
                    auditLog.record(AuditEntry.UNKNOWN_OPTION,
                            "key=" + e.getKey() + " handling=" + handling);
                    if (handling == UnknownOptionHandling.REJECT) {
                        throw new IllegalArgumentException("unknown render option: " + e.getKey());
                    }
                    // IGNORE: dropped silently
                }
            }
        }

        RenderOptions normalized = new RenderOptions(
                incoming.schemaVersion(),
                incoming.headingNumbers(),
                incoming.generateToc(),
                incoming.sanitizeHtml(),
                incoming.rawHtmlAllowed(),
                handling,
                Map.copyOf(extras));

        if (normalized.headingNumbers() != base.headingNumbers()) {
            auditSwitch("headingNumbers", String.valueOf(normalized.headingNumbers()));
        }
        if (normalized.generateToc() != base.generateToc()) {
            auditSwitch("generateToc", String.valueOf(normalized.generateToc()));
        }
        if (normalized.sanitizeHtml() != base.sanitizeHtml()) {
            auditSwitch("sanitizeHtml", String.valueOf(normalized.sanitizeHtml()));
        }
        if (normalized.rawHtmlAllowed() != base.rawHtmlAllowed()) {
            auditSwitch("rawHtmlAllowed", String.valueOf(normalized.rawHtmlAllowed()));
        }
        if (normalized.unknownOptionHandling() != base.unknownOptionHandling()) {
            auditSwitch("unknownOptionHandling", String.valueOf(normalized.unknownOptionHandling()));
        }
        return normalized;
    }

    private void auditSwitch(String name, String value) {
        auditLog.record(AuditEntry.OPTION_SWITCHED, name + "=" + value);
    }
}
