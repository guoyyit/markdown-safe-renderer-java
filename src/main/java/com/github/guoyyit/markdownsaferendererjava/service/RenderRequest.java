package com.github.guoyyit.markdownsaferendererjava.service;

import com.github.guoyyit.markdownsaferendererjava.options.RenderOptions;

import java.util.List;

/** A single render request: untrusted content plus options and reserved anchors. */
public final class RenderRequest {
    private final String content;
    private final RenderOptions options;
    private final List<String> reservedAnchors;

    public RenderRequest(String content, RenderOptions options, List<String> reservedAnchors) {
        this.content = content;
        this.options = options == null ? RenderOptions.defaults() : options;
        this.reservedAnchors = reservedAnchors == null ? List.of() : List.copyOf(reservedAnchors);
    }

    public String getContent() { return content; }
    public RenderOptions getOptions() { return options; }
    public List<String> getReservedAnchors() { return reservedAnchors; }
}
