package com.github.guoyyit.markdownsaferendererjava.audit;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Bounded, thread-safe in-memory audit trail. Oldest entries are dropped once
 * the capacity is reached. {@link #snapshot()} returns a safe copy.
 */
public class AuditLog {

    private final java.util.ArrayDeque<AuditEntry> entries = new java.util.ArrayDeque<>();
    private final ReentrantLock lock = new ReentrantLock();
    private final int capacity;

    public AuditLog(int capacity) {
        this.capacity = capacity;
    }

    public AuditLog() {
        this(1000);
    }

    public void record(String kind, String detail) {
        lock.lock();
        try {
            entries.addLast(new AuditEntry(System.currentTimeMillis(), kind, detail));
            while (entries.size() > capacity) {
                entries.pollFirst();
            }
        } finally {
            lock.unlock();
        }
    }

    public List<AuditEntry> snapshot() {
        lock.lock();
        try {
            return new ArrayList<>(entries);
        } finally {
            lock.unlock();
        }
    }
}
