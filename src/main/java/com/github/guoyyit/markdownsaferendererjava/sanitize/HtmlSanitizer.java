package com.github.guoyyit.markdownsaferendererjava.sanitize;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Sanitizes raw HTML fragments against a whitelist policy. Script content,
 * event handler attributes, dangerous URL protocols and attribute-escape
 * attempts are rejected or escaped. Nesting and case variations do not
 * bypass sanitizing. Malformed markup is recovered locally.
 */
public final class HtmlSanitizer {

    /** Tags that never have content and never go on the open-tag stack. */
    private static final Set<String> VOID_TAGS = Set.of(
            "area", "base", "br", "col", "embed", "hr", "img", "input",
            "link", "meta", "source", "track", "wbr");

    /** Tags whose entire content is discarded, never emitted as text. */
    public static final Set<String> DISCARD_CONTENT_TAGS = Set.of(
            "script", "style", "iframe", "object", "embed", "form",
            "textarea", "title", "noscript", "noframes", "xmp", "plaintext",
            "template", "svg", "math", "link", "meta", "base", "frame",
            "frameset", "applet", "audio", "video");

    /** Attributes whose value is a URL and must pass the protocol check. */
    private static final Set<String> URL_ATTRIBUTES = Set.of(
            "href", "src", "action", "formaction", "xlink:href", "cite",
            "background", "poster", "data");

    private final SanitizerPolicy policy;

    public HtmlSanitizer(SanitizerPolicy policy) {
        this.policy = policy;
    }

    public SanitizedHtml sanitize(String rawHtml) {
        List<String> removals = new ArrayList<>();
        if (rawHtml == null || rawHtml.isEmpty()) {
            return new SanitizedHtml("", removals);
        }
        StringBuilder out = new StringBuilder(rawHtml.length());
        Deque<String> openTags = new ArrayDeque<>();
        int n = rawHtml.length();
        int i = 0;
        String suppressedTag = null; // tag whose content is being discarded
        int suppressedDepth = 0;

        while (i < n) {
            char c = rawHtml.charAt(i);
            if (c == '<' && i + 1 < n) {
                char next = rawHtml.charAt(i + 1);
                if (next == '!') {
                    i = skipCommentOrDeclaration(rawHtml, i, removals);
                    continue;
                }
                if (next == '/' || Character.isLetter(next)) {
                    TagParse tp = parseTag(rawHtml, i);
                    i = tp.nextIndex;
                    if (tp.incomplete) {
                        removals.add("dropped incomplete tag <" + tp.name + ">");
                        break; // unterminated tag consumed the rest of the input
                    }
                    String name = tp.name;
                    if (suppressedTag != null) {
                        if (name.equals(suppressedTag)) {
                            if (tp.closing) {
                                suppressedDepth--;
                                if (suppressedDepth == 0) {
                                    suppressedTag = null;
                                }
                            } else if (!tp.selfClosing && !VOID_TAGS.contains(name)) {
                                suppressedDepth++;
                            }
                        }
                        continue; // everything inside a discarded tag is dropped
                    }
                    if (tp.closing) {
                        handleEndTag(out, removals, openTags, name);
                    } else if (DISCARD_CONTENT_TAGS.contains(name)) {
                        removals.add("removed <" + name + "> and its content");
                        if (!tp.selfClosing && !VOID_TAGS.contains(name)) {
                            suppressedTag = name;
                            suppressedDepth = 1;
                        }
                    } else if (!policy.isTagAllowed(name)) {
                        removals.add("removed tag <" + name + ">");
                        // inner text content is kept
                    } else {
                        String attrs = sanitizeAttributes(name, tp, removals);
                        out.append('<').append(name).append(attrs);
                        if (tp.selfClosing) {
                            out.append("/>");
                        } else {
                            out.append('>');
                            if (!VOID_TAGS.contains(name)) {
                                openTags.push(name);
                            }
                        }
                    }
                    continue;
                }
                // '<' followed by something that cannot start a tag: literal text
                if (suppressedTag == null) {
                    out.append("&lt;");
                }
                i++;
            } else {
                int start = i;
                while (i < n && rawHtml.charAt(i) != '<') {
                    i++;
                }
                if (suppressedTag == null) {
                    escapeText(rawHtml, start, i, out);
                }
            }
        }
        while (!openTags.isEmpty()) {
            out.append("</").append(openTags.pop()).append('>');
        }
        return new SanitizedHtml(out.toString(), removals);
    }

    private int skipCommentOrDeclaration(String s, int start, List<String> removals) {
        int n = s.length();
        if (start + 3 < n && s.charAt(start + 2) == '-' && s.charAt(start + 3) == '-') {
            int end = s.indexOf("-->", start + 4);
            removals.add("removed comment");
            return end < 0 ? n : end + 3;
        }
        int gt = s.indexOf('>', start + 2);
        removals.add("removed declaration");
        return gt < 0 ? n : gt + 1;
    }

    private void handleEndTag(StringBuilder out, List<String> removals,
                              Deque<String> openTags, String name) {
        if (!policy.isTagAllowed(name) || VOID_TAGS.contains(name)
                || DISCARD_CONTENT_TAGS.contains(name)) {
            return; // end tag of a dropped or void element: nothing to close
        }
        if (openTags.contains(name)) {
            while (!openTags.isEmpty()) {
                String open = openTags.pop();
                out.append("</").append(open).append('>');
                if (open.equals(name)) {
                    break;
                }
            }
        } else {
            removals.add("ignored unmatched end tag </" + name + ">");
        }
    }

    private String sanitizeAttributes(String tag, TagParse tp, List<String> removals) {
        StringBuilder sb = new StringBuilder();
        Set<String> seen = new HashSet<>();
        for (String[] attr : tp.attributes) {
            String name = attr[0];
            String value = attr[1] == null ? "" : attr[1];
            if (!seen.add(name)) {
                removals.add("removed duplicate attribute " + name + " on <" + tag + ">");
                continue;
            }
            if (name.startsWith("on")) {
                removals.add("removed event handler attribute " + name + " on <" + tag + ">");
                continue;
            }
            if (!policy.isAttributeAllowed(tag, name)) {
                removals.add("removed attribute " + name + " on <" + tag + ">");
                continue;
            }
            if (URL_ATTRIBUTES.contains(name)) {
                if (!isUrlSafe(value)) {
                    removals.add("removed unsafe URL in " + name + " on <" + tag + ">");
                    continue;
                }
                value = decodeEntities(value); // normalise, then re-escape below
            }
            sb.append(' ').append(name).append("=\"");
            escapeAttribute(value, sb);
            sb.append('"');
        }
        return sb.toString();
    }

    /**
     * A URL is safe when it has no scheme (relative) or its scheme is allowed.
     * Entities are decoded and whitespace/control characters ignored before
     * the scheme is compared case-insensitively.
     */
    public boolean isUrlSafe(String url) {
        String decoded = decodeEntities(url);
        int i = 0;
        int len = decoded.length();
        while (i < len && decoded.charAt(i) <= 0x20) {
            i++;
        }
        StringBuilder scheme = new StringBuilder();
        for (int j = i; j < len; j++) {
            char ch = decoded.charAt(j);
            if (ch == ':') {
                if (scheme.length() == 0) {
                    return false; // ":foo" is not a safe relative URL
                }
                return policy.isProtocolAllowed(scheme.toString());
            }
            if (ch == '/' || ch == '?' || ch == '#') {
                return true; // relative URL before any scheme
            }
            if (ch <= 0x20) {
                continue; // "java\tscript:" -> "javascript:"
            }
            scheme.append(ch);
        }
        return true; // no scheme at all
    }

    static String decodeEntities(String s) {
        if (s.indexOf('&') < 0) {
            return s;
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '&') {
                int semi = s.indexOf(';', i + 1);
                if (semi > i + 1 && semi - i <= 10) {
                    Character decoded = decodeEntity(s.substring(i + 1, semi));
                    if (decoded != null) {
                        sb.append(decoded.charValue());
                        i = semi;
                        continue;
                    }
                }
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private static Character decodeEntity(String ent) {
        try {
            if (ent.startsWith("#x") || ent.startsWith("#X")) {
                return (char) Integer.parseInt(ent.substring(2), 16);
            }
            if (ent.startsWith("#")) {
                return (char) Integer.parseInt(ent.substring(1));
            }
        } catch (NumberFormatException e) {
            return null;
        }
        return switch (ent) {
            case "amp" -> '&';
            case "lt" -> '<';
            case "gt" -> '>';
            case "quot" -> '"';
            case "apos" -> '\'';
            case "nbsp" -> ' ';
            case "tab" -> '\t';
            case "newline" -> '\n';
            default -> null;
        };
    }

    static void escapeText(String s, int start, int end, StringBuilder out) {
        for (int i = start; i < end; i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                default -> out.append(c);
            }
        }
    }

    /** Escapes plain text for HTML content. */
    public static String escapeText(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        escapeText(s, 0, s.length(), sb);
        return sb.toString();
    }

    /** Escapes text for a double-quoted attribute value, preventing breakout. */
    public static String escapeAttribute(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        escapeAttribute(s, sb);
        return sb.toString();
    }

    /** Escapes text for a double-quoted attribute value, preventing breakout. */
    static void escapeAttribute(String s, StringBuilder out) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> out.append(c);
            }
        }
    }

    private static boolean isNameChar(char c) {
        return Character.isLetterOrDigit(c) || c == '-' || c == ':';
    }

    private static boolean isAttrNameChar(char c) {
        return !Character.isWhitespace(c) && c != '=' && c != '>' && c != '/'
                && c != '"' && c != '\'';
    }

    private static final class TagParse {
        String name;
        boolean closing;
        boolean selfClosing;
        boolean incomplete;
        final List<String[]> attributes = new ArrayList<>();
        int nextIndex;
    }

    private static TagParse parseTag(String s, int start) {
        TagParse tp = new TagParse();
        int n = s.length();
        int i = start + 1;
        if (s.charAt(i) == '/') {
            tp.closing = true;
            i++;
        }
        int nameStart = i;
        while (i < n && isNameChar(s.charAt(i))) {
            i++;
        }
        tp.name = s.substring(nameStart, i).toLowerCase(Locale.ROOT);
        while (true) {
            while (i < n && Character.isWhitespace(s.charAt(i))) {
                i++;
            }
            if (i >= n) {
                tp.incomplete = true;
                tp.nextIndex = i;
                return tp;
            }
            char c = s.charAt(i);
            if (c == '>') {
                tp.nextIndex = i + 1;
                return tp;
            }
            if (c == '/') {
                if (i + 1 < n && s.charAt(i + 1) == '>') {
                    tp.selfClosing = true;
                    tp.nextIndex = i + 2;
                    return tp;
                }
                i++; // stray slash inside a tag
                continue;
            }
            if (tp.closing) {
                i++; // junk inside an end tag
                continue;
            }
            int aStart = i;
            while (i < n && isAttrNameChar(s.charAt(i))) {
                i++;
            }
            if (i == aStart) {
                i++; // stray character
                continue;
            }
            String attrName = s.substring(aStart, i).toLowerCase(Locale.ROOT);
            while (i < n && Character.isWhitespace(s.charAt(i))) {
                i++;
            }
            String value = null;
            if (i < n && s.charAt(i) == '=') {
                i++;
                while (i < n && Character.isWhitespace(s.charAt(i))) {
                    i++;
                }
                if (i < n && (s.charAt(i) == '"' || s.charAt(i) == '\'')) {
                    char q = s.charAt(i);
                    i++;
                    int vStart = i;
                    while (i < n && s.charAt(i) != q) {
                        i++;
                    }
                    if (i >= n) {
                        tp.incomplete = true;
                        tp.nextIndex = i;
                        return tp;
                    }
                    value = s.substring(vStart, i);
                    i++;
                } else {
                    int vStart = i;
                    while (i < n && !Character.isWhitespace(s.charAt(i))
                            && s.charAt(i) != '>') {
                        i++;
                    }
                    value = s.substring(vStart, i);
                }
            }
            tp.attributes.add(new String[]{attrName, value});
        }
    }
}
