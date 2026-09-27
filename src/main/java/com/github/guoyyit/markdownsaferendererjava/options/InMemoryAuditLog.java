package com.github.guoyyit.markdownsaferendererjava.options;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Simple bounded in-memory audit log; oldest events are dropped first. */
public final class InMemoryAuditLog implements OptionsAuditLog {
    private static final int DEFAULT_CAPACITY = 1000;

    private final int capacity;
    private final Deque<AuditEvent> events;

    public InMemoryAuditLog() {
        this(DEFAULT_CAPACITY);
    }

    public InMemoryAuditLog(int capacity) {
        this.capacity = Math.max(1, capacity);
        this.events = new ArrayDeque<>(this.capacity);
    }

    @Override
    public synchronized void record(AuditEvent event) {
        if (event == null) {
            return;
        }
        while (events.size() >= capacity) {
            events.pollFirst();
        }
        events.addLast(event);
    }

    @Override
    public synchronized List<AuditEvent> recent(int limit) {
        int n = Math.min(limit, events.size());
        List<AuditEvent> result = new ArrayList<>(n);
        var it = events.descendingIterator();
        while (it.hasNext() && result.size() < n) {
            result.add(it.next());
        }
        return result;
    }
}
