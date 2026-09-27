package com.github.guoyyit.markdownsaferendererjava.toc;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Allocates globally unique, stable anchors for headings. Collisions (duplicate
 * titles, anchors reserved by other documents) are resolved deterministically
 * with a numeric suffix, so the same document always gets the same anchors.
 */
public final class AnchorRegistry {
    private final Set<String> reserved = new HashSet<>();
    private final Set<String> taken = new HashSet<>();
    private final Map<String, Integer> nextSuffix = new HashMap<>();

    /** Reserves an anchor that must not be handed out (e.g. from another document). */
    public void reserve(String anchor) {
        if (anchor != null && !anchor.isEmpty()) {
            reserved.add(anchor);
        }
    }

    /** Returns a unique anchor derived from the title; stable across identical runs. */
    public String allocate(String title) {
        String base = slugify(title);
        String candidate = base;
        if (isFree(candidate)) {
            claim(candidate);
            return candidate;
        }
        int suffix = nextSuffix.getOrDefault(base, 2);
        do {
            candidate = base + "-" + suffix;
            suffix++;
        } while (!isFree(candidate));
        nextSuffix.put(base, suffix);
        claim(candidate);
        return candidate;
    }

    private boolean isFree(String anchor) {
        return !taken.contains(anchor) && !reserved.contains(anchor);
    }

    private void claim(String anchor) {
        taken.add(anchor);
    }

    /** Lowercases, keeps letters/digits, maps every other run to a single '-'. */
    static String slugify(String title) {
        if (title == null) {
            return "section";
        }
        StringBuilder sb = new StringBuilder(title.length());
        boolean lastWasDash = false;
        for (int i = 0; i < title.length(); i++) {
            char c = title.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                sb.append(Character.toLowerCase(c));
                lastWasDash = false;
            } else if (!lastWasDash && sb.length() > 0) {
                sb.append('-');
                lastWasDash = true;
            }
        }
        // trim trailing dash
        while (sb.length() > 0 && sb.charAt(sb.length() - 1) == '-') {
            sb.setLength(sb.length() - 1);
        }
        String slug = sb.toString();
        return slug.isEmpty() ? "section" : slug;
    }
}
