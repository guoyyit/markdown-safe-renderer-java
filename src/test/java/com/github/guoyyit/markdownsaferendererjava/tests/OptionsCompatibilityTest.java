package com.github.guoyyit.markdownsaferendererjava.tests;

import com.github.guoyyit.markdownsaferendererjava.audit.AuditEntry;
import com.github.guoyyit.markdownsaferendererjava.audit.AuditLog;
import com.github.guoyyit.markdownsaferendererjava.options.OptionsValidator;
import com.github.guoyyit.markdownsaferendererjava.options.RenderOptions;
import com.github.guoyyit.markdownsaferendererjava.options.UnknownOptionHandling;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OptionsCompatibilityTest {

    @Test
    void nullOptionsYieldV1Defaults() {
        RenderOptions o = new OptionsValidator(new AuditLog()).validate(null);
        assertEquals(RenderOptions.v1Defaults(), o);
    }

    @Test
    void unsupportedSchemaVersionsAreRejected() {
        OptionsValidator v = new OptionsValidator(new AuditLog());
        RenderOptions future = new RenderOptions(99, true, true, true, false,
                UnknownOptionHandling.REJECT, Map.of());
        assertThrows(IllegalArgumentException.class, () -> v.validate(future));
        RenderOptions ancient = new RenderOptions(0, true, true, true, false,
                UnknownOptionHandling.REJECT, Map.of());
        assertThrows(IllegalArgumentException.class, () -> v.validate(ancient));
    }

    @Test
    void unknownOptionsRejectedByDefaultAndAudited() {
        AuditLog log = new AuditLog();
        OptionsValidator v = new OptionsValidator(log);
        RenderOptions o = new RenderOptions(RenderOptions.SCHEMA_V1, true, true, true, false,
                UnknownOptionHandling.REJECT, Map.of("newFancyFeature", "on"));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> v.validate(o));
        assertTrue(e.getMessage().contains("newFancyFeature"));
        assertTrue(log.snapshot().stream()
                .anyMatch(a -> a.kind().equals(AuditEntry.UNKNOWN_OPTION)));
    }

    @Test
    void unknownOptionsIgnoredWhenConfigured() {
        AuditLog log = new AuditLog();
        OptionsValidator v = new OptionsValidator(log);
        RenderOptions o = new RenderOptions(RenderOptions.SCHEMA_V1, true, true, true, false,
                UnknownOptionHandling.IGNORE, Map.of("newFancyFeature", "on"));
        RenderOptions out = v.validate(o);
        assertTrue(out.extra().isEmpty(), "unknown option must be dropped, not stored");
        assertTrue(log.snapshot().stream()
                .anyMatch(a -> a.kind().equals(AuditEntry.UNKNOWN_OPTION)));
    }

    @Test
    void optionSwitchesAreAudited() {
        AuditLog log = new AuditLog();
        OptionsValidator v = new OptionsValidator(log);
        RenderOptions o = new RenderOptions(RenderOptions.SCHEMA_V1, false, false, true, false,
                UnknownOptionHandling.REJECT, Map.of());
        v.validate(o);
        List<AuditEntry> switches = log.snapshot().stream()
                .filter(a -> a.kind().equals(AuditEntry.OPTION_SWITCHED)).toList();
        assertEquals(2, switches.size());
        assertTrue(switches.stream().anyMatch(a -> a.detail().startsWith("headingNumbers")));
        assertTrue(switches.stream().anyMatch(a -> a.detail().startsWith("generateToc")));
    }

    @Test
    void v1OutputIsStableWhenNoOptionsGiven() {
        RenderOptions a = new OptionsValidator(new AuditLog()).validate(
                new RenderOptions(RenderOptions.SCHEMA_V1, true, true, true, false,
                        UnknownOptionHandling.REJECT, Map.of()));
        assertEquals(RenderOptions.v1Defaults(), a,
                "explicit v1 defaults must equal the implicit defaults");
    }
}
