package com.github.guoyyit.markdownsaferendererjava.sanitize;

import java.util.Map;
import java.util.Set;

/**
 * Allowlist of tags, per-tag attributes and URL protocols.
 * Anything not listed is rejected (dropped) or escaped.
 */
public record SanitizePolicy(
        Set<String> allowedTags,
        Map<String, Set<String>> allowedAttributes,
        Set<String> allowedProtocols) {

    public SanitizePolicy {
        allowedTags = Set.copyOf(allowedTags);
        allowedAttributes = copyOfAttrs(allowedAttributes);
        allowedProtocols = Set.copyOf(allowedProtocols);
    }

    private static Map<String, Set<String>> copyOfAttrs(Map<String, Set<String>> in) {
        java.util.Map<String, Set<String>> out = new java.util.LinkedHashMap<>();
        in.forEach((k, v) -> out.put(k, Set.copyOf(v)));
        return java.util.Collections.unmodifiableMap(out);
    }

    /**
     * Conservative default: formatting tags only, no scripts/forms/frames,
     * no event handlers, no style attribute, http(s)/mailto URLs only.
     */
    public static SanitizePolicy defaultPolicy() {
        Set<String> tags = Set.of(
                "p", "br", "hr", "span", "div",
                "b", "strong", "i", "em", "u", "s", "del", "ins", "mark", "small", "sub", "sup",
                "code", "pre", "kbd", "samp", "var",
                "blockquote", "q", "cite",
                "ul", "ol", "li", "dl", "dt", "dd",
                "table", "thead", "tbody", "tfoot", "tr", "td", "th", "caption",
                "a", "img", "figure", "figcaption",
                "h1", "h2", "h3", "h4", "h5", "h6",
                "abbr", "time", "details", "summary");
        Map<String, Set<String>> attrs = Map.ofEntries(
                Map.entry("a", Set.of("href", "title", "id", "class")),
                Map.entry("img", Set.of("src", "alt", "title", "width", "height")),
                Map.entry("td", Set.of("align", "colspan", "rowspan")),
                Map.entry("th", Set.of("align", "colspan", "rowspan")),
                Map.entry("code", Set.of("class")),
                Map.entry("pre", Set.of("class")),
                Map.entry("span", Set.of("class", "id")),
                Map.entry("div", Set.of("class", "id")),
                Map.entry("h1", Set.of("id", "class")),
                Map.entry("h2", Set.of("id", "class")),
                Map.entry("h3", Set.of("id", "class")),
                Map.entry("h4", Set.of("id", "class")),
                Map.entry("h5", Set.of("id", "class")),
                Map.entry("h6", Set.of("id", "class")),
                Map.entry("abbr", Set.of("title")),
                Map.entry("time", Set.of("datetime")),
                Map.entry("ol", Set.of("start", "type")),
                Map.entry("blockquote", Set.of("cite")),
                Map.entry("q", Set.of("cite")));
        Set<String> protocols = Set.of("http", "https", "mailto");
        return new SanitizePolicy(tags, attrs, protocols);
    }
}
