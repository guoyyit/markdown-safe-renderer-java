package com.github.guoyyit.markdownsaferendererjava.sanitize;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Sanitizes raw HTML fragments against a {@link SanitizePolicy}.
 * <p>
 * Security model:
 * <ul>
 *   <li>Only allowlisted tags are emitted as markup; everything else is
 *       entity-escaped so it renders as inert text.</li>
 *   <li>Only allowlisted attributes survive; event handlers (on*) and
 *       unknown attributes are dropped by construction.</li>
 *   <li>URL attributes are entity-decoded, control-character-stripped and
 *       checked against an allowlist of protocols (case-insensitive), so
 *       {@code javascript:}, {@code data:}, {@code vbscript:} etc. are removed
 *       even with case/nesting/whitespace obfuscation.</li>
 *   <li>Attribute values are always re-escaped on output, so a value can
 *       never break out of its quotes.</li>
 *   <li>Comments, doctypes, processing instructions and malformed tags are
 *       dropped or escaped; nesting tricks cannot smuggle markup through
 *       because every token is validated independently.</li>
 * </ul>
 */
public class HtmlSanitizer {

    private final SanitizePolicy policy;

    public HtmlSanitizer(SanitizePolicy policy) {
        this.policy = policy;
    }

    /** Tags whose raw text content must never be emitted. */
    private static final Set<String> RAW_TEXT_DROP = Set.of("script", "style", "iframe", "object", "embed");

    /** Attributes that carry URLs and need protocol validation. */
    private static final Set<String> URL_ATTRS = Set.of("href", "src", "cite", "action", "formaction");

    public String sanitize(String rawHtml) {
        if (rawHtml == null || rawHtml.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(rawHtml.length());
        int i = 0;
        int n = rawHtml.length();
        // When a raw-text element like <script> is rejected, its text content
        // must not leak into the output: suppress everything up to the
        // matching close tag (nested same-name opens increase the depth).
        String suppressedTag = null;
        int suppressedDepth = 0;
        while (i < n) {
            char c = rawHtml.charAt(i);
            if (c != '<') {
                int next = rawHtml.indexOf('<', i);
                int end = next < 0 ? n : next;
                if (suppressedTag == null) {
                    escapeText(rawHtml, i, end, out);
                }
                i = end;
                continue;
            }
            // c == '<'
            if (rawHtml.startsWith("<!--", i)) {
                int close = rawHtml.indexOf("-->", i + 4);
                // comments are dropped entirely (incl. unclosed ones)
                i = close < 0 ? n : close + 3;
                continue;
            }
            if (i + 1 < n && (rawHtml.charAt(i + 1) == '!' || rawHtml.charAt(i + 1) == '?')) {
                int close = rawHtml.indexOf('>', i + 1);
                i = close < 0 ? n : close + 1;
                continue;
            }
            int[] tagEnd = new int[1];
            Tag tag = parseTag(rawHtml, i, tagEnd);
            if (tag == null) {
                // not a tag at all: while suppressing, consume the '<' silently;
                // otherwise escape it and move on
                if (suppressedTag == null) {
                    out.append("&lt;");
                }
                i++;
                continue;
            }
            if (tag.name == null) {
                // malformed (e.g. unterminated quote): escape through the next
                // '>' (or end of input) so nothing can execute. Inside a
                // suppressed raw-text element the text is simply dropped.
                int gt = rawHtml.indexOf('>', i + 1);
                int stop = gt < 0 ? n : gt + 1;
                if (suppressedTag == null) {
                    escapeText(rawHtml, i, stop, out);
                }
                i = stop;
                continue;
            }
            if (suppressedTag != null) {
                if (suppressedTag.equals(tag.name)) {
                    suppressedDepth += tag.closing ? -1 : (tag.selfClosing ? 0 : 1);
                    if (suppressedDepth <= 0) {
                        suppressedTag = null;
                        suppressedDepth = 0;
                    }
                }
                i = tagEnd[0];
                continue;
            }
            if (tag.name != null && !tag.closing && !tag.selfClosing
                    && RAW_TEXT_DROP.contains(tag.name)
                    && !policy.allowedTags().contains(tag.name)) {
                suppressedTag = tag.name;
                suppressedDepth = 1;
                i = tagEnd[0];
                continue;
            }
            emit(tag, rawHtml, i, tagEnd[0], out);
            i = tagEnd[0];
        }
        return out.toString();
    }

    // ------------------------------------------------------------ tokenizing

    private static final class Tag {
        boolean closing;
        boolean selfClosing;
        String name;          // lower-cased
        String rawName;
        java.util.List<String[]> attrs = new java.util.ArrayList<>(); // [name, value|null]
    }

    /**
     * Parses a tag starting at {@code start} (which points at '<').
     * Returns null if this is not a syntactically valid tag; on malformed
     * (unterminated) tags returns a Tag with name == null so the caller can
     * escape the raw text.
     */
    private Tag parseTag(String s, int start, int[] tagEnd) {
        int i = start + 1;
        int n = s.length();
        Tag tag = new Tag();
        if (i < n && s.charAt(i) == '/') {
            tag.closing = true;
            i++;
        }
        int nameStart = i;
        while (i < n && isNameChar(s.charAt(i))) {
            i++;
        }
        if (i == nameStart) {
            return null; // '<' not followed by a name char
        }
        tag.rawName = s.substring(nameStart, i);
        tag.name = tag.rawName.toLowerCase(Locale.ROOT);
        // attributes
        while (true) {
            while (i < n && Character.isWhitespace(s.charAt(i))) {
                i++;
            }
            if (i >= n) {
                return malformed(tag); // unterminated tag
            }
            char c = s.charAt(i);
            if (c == '>') {
                tagEnd[0] = i + 1;
                return tag;
            }
            if (c == '/' && i + 1 < n && s.charAt(i + 1) == '>') {
                tag.selfClosing = true;
                tagEnd[0] = i + 2;
                return tag;
            }
            // attribute name
            int attrStart = i;
            while (i < n && isAttrNameChar(s.charAt(i))) {
                i++;
            }
            if (i == attrStart) {
                return malformed(tag); // e.g. stray '/'
            }
            String attrName = s.substring(attrStart, i).toLowerCase(Locale.ROOT);
            while (i < n && Character.isWhitespace(s.charAt(i))) {
                i++;
            }
            String value = null;
            if (i < n && s.charAt(i) == '=') {
                i++;
                while (i < n && Character.isWhitespace(s.charAt(i))) {
                    i++;
                }
                if (i >= n) {
                    return malformed(tag);
                }
                char q = s.charAt(i);
                if (q == '"' || q == '\'') {
                    int close = s.indexOf(q, i + 1);
                    if (close < 0) {
                        return malformed(tag); // unterminated quote
                    }
                    value = s.substring(i + 1, close);
                    i = close + 1;
                } else {
                    int vStart = i;
                    while (i < n && !Character.isWhitespace(s.charAt(i)) && s.charAt(i) != '>') {
                        i++;
                    }
                    value = s.substring(vStart, i);
                }
            }
            tag.attrs.add(new String[]{attrName, value});
        }
    }

    private Tag malformed(Tag tag) {
        tag.name = null;
        return tag;
    }

    private static boolean isNameChar(char c) {
        return Character.isLetterOrDigit(c) || c == '-';
    }

    private static boolean isAttrNameChar(char c) {
        return !Character.isWhitespace(c) && c != '/' && c != '>' && c != '=' && c != '<'
                && c != '"' && c != '\'';
    }

    // -------------------------------------------------------------- emission

    private void emit(Tag tag, String src, int start, int end, StringBuilder out) {
        if (tag.name == null) {
            // malformed tag: escape the raw text so it cannot execute
            escapeText(src, start, end, out);
            return;
        }
        if (!policy.allowedTags().contains(tag.name)) {
            // disallowed tag: escape so the user sees it as inert text
            escapeText(src, start, end, out);
            return;
        }
        if (tag.closing) {
            out.append("</").append(tag.name).append('>');
            return;
        }
        out.append('<').append(tag.name);
        Set<String> allowedAttrs = policy.allowedAttributes()
                .getOrDefault(tag.name, Set.of());
        for (String[] attr : tag.attrs) {
            String name = attr[0];
            String value = attr[1];
            if (!allowedAttrs.contains(name)) {
                continue; // drops on*, style, and anything not allowlisted
            }
            if (URL_ATTRS.contains(name) && value != null && !isSafeUrl(value)) {
                continue; // dangerous protocol: drop the attribute
            }
            if (value == null) {
                out.append(' ').append(name);
            } else {
                out.append(' ').append(name).append("=\"");
                escapeAttribute(value, out);
                out.append('"');
            }
        }
        out.append(tag.selfClosing ? "/>" : ">");
    }

    /**
     * Validates a URL against the protocol allowlist. Entity-decodes and
     * strips control characters first so obfuscation cannot bypass the check.
     */
    public boolean isSafeUrl(String raw) {
        String decoded = decodeEntities(raw);
        StringBuilder cleaned = new StringBuilder(decoded.length());
        for (int k = 0; k < decoded.length(); k++) {
            char c = decoded.charAt(k);
            if (c > 0x20 && c != 0x7F) {
                cleaned.append(c);
            }
        }
        String url = cleaned.toString();
        if (url.isEmpty()) {
            return true;
        }
        if (url.startsWith("#") || url.startsWith("/") || url.startsWith("./")
                || url.startsWith("../") || url.startsWith("//")) {
            return true;
        }
        int colon = url.indexOf(':');
        if (colon < 0) {
            return true; // relative reference
        }
        String scheme = url.substring(0, colon).toLowerCase(Locale.ROOT);
        if (!scheme.matches("[a-z][a-z0-9+.-]*")) {
            return false;
        }
        return policy.allowedProtocols().contains(scheme);
    }

    static String decodeEntities(String s) {
        if (s.indexOf('&') < 0) {
            return s;
        }
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != '&') {
                out.append(c);
                continue;
            }
            int semi = s.indexOf(';', i + 1);
            if (semi < 0 || semi - i > 10) {
                out.append(c);
                continue;
            }
            String ent = s.substring(i + 1, semi);
            if (ent.startsWith("#x") || ent.startsWith("#X")) {
                try {
                    out.append((char) Integer.parseInt(ent.substring(2), 16));
                    i = semi;
                    continue;
                } catch (NumberFormatException e) {
                    out.append(c);
                    continue;
                }
            }
            if (ent.startsWith("#")) {
                try {
                    out.append((char) Integer.parseInt(ent.substring(1)));
                    i = semi;
                    continue;
                } catch (NumberFormatException e) {
                    out.append(c);
                    continue;
                }
            }
            switch (ent.toLowerCase(Locale.ROOT)) {
                case "amp" -> { out.append('&'); i = semi; }
                case "lt" -> { out.append('<'); i = semi; }
                case "gt" -> { out.append('>'); i = semi; }
                case "quot" -> { out.append('"'); i = semi; }
                case "apos" -> { out.append('\''); i = semi; }
                case "colon" -> { out.append(':'); i = semi; }
                case "tab" -> { out.append('\t'); i = semi; }
                case "newline" -> { out.append('\n'); i = semi; }
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    public static void escapeText(String s, int from, int to, StringBuilder out) {
        for (int k = from; k < to; k++) {
            char c = s.charAt(k);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                default -> out.append(c);
            }
        }
    }

    public static void escapeAttribute(String s, StringBuilder out) {
        for (int k = 0; k < s.length(); k++) {
            char c = s.charAt(k);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#x27;");
                default -> out.append(c);
            }
        }
    }
}
