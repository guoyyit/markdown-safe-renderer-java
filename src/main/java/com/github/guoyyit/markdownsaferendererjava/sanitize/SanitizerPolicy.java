package com.github.guoyyit.markdownsaferendererjava.sanitize;

import java.util.Set;

/**
 * Whitelist policy for raw HTML: allowed tags, allowed attributes per tag,
 * and allowed URL protocols. Everything else is dropped or escaped.
 */
public final class SanitizerPolicy {
    private final Set<String> allowedTags;
    private final java.util.Map<String, Set<String>> allowedAttributes;
    private final Set<String> allowedProtocols;

    public SanitizerPolicy(Set<String> allowedTags,
                           java.util.Map<String, Set<String>> allowedAttributes,
                           Set<String> allowedProtocols) {
        this.allowedTags = Set.copyOf(allowedTags);
        this.allowedAttributes = java.util.Map.copyOf(allowedAttributes);
        this.allowedProtocols = Set.copyOf(allowedProtocols);
    }

    public static SanitizerPolicy restrictive() {
        return new SanitizerPolicy(
                Set.of("p", "br", "b", "strong", "i", "em", "u", "code", "pre",
                        "ul", "ol", "li", "blockquote", "a", "img", "table",
                        "thead", "tbody", "tr", "th", "td", "h1", "h2", "h3",
                        "h4", "h5", "h6", "hr", "span", "div"),
                java.util.Map.of(
                        "a", Set.of("href", "title"),
                        "img", Set.of("src", "alt", "title"),
                        "code", Set.of("class"),
                        "span", Set.of("class"),
                        "div", Set.of("class"),
                        "th", Set.of("align"),
                        "td", Set.of("align")),
                Set.of("http", "https", "mailto"));
    }

    public boolean isTagAllowed(String tag) {
        return tag != null && allowedTags.contains(tag.toLowerCase(java.util.Locale.ROOT));
    }

    public boolean isAttributeAllowed(String tag, String attribute) {
        if (tag == null || attribute == null) {
            return false;
        }
        Set<String> attrs = allowedAttributes.get(tag.toLowerCase(java.util.Locale.ROOT));
        return attrs != null && attrs.contains(attribute.toLowerCase(java.util.Locale.ROOT));
    }

    public boolean isProtocolAllowed(String protocol) {
        return protocol != null
                && allowedProtocols.contains(protocol.toLowerCase(java.util.Locale.ROOT));
    }
    public Set<String> getAllowedTags() { return allowedTags; }
    public Set<String> getAllowedProtocols() { return allowedProtocols; }
}
