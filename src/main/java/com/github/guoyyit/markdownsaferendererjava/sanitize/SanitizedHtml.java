package com.github.guoyyit.markdownsaferendererjava.sanitize;

import java.util.List;

/** Result of sanitizing raw HTML: safe output plus a record of what was removed. */
public final class SanitizedHtml {
    private final String html;
    private final List<String> removals;

    public SanitizedHtml(String html, List<String> removals) {
        this.html = html;
        this.removals = List.copyOf(removals);
    }

    public String getHtml() { return html; }
    public List<String> getRemovals() { return removals; }
}
