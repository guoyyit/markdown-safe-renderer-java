package com.github.guoyyit.markdownsaferendererjava.options;

import java.util.List;

/** Append-only audit log for option switches and option errors. */
public interface OptionsAuditLog {
    void record(AuditEvent event);

    List<AuditEvent> recent(int limit);
}
