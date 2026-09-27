package com.github.guoyyit.markdownsaferendererjava.web;

import java.util.Map;

/**
 * Render request body. {@code options} is an open map so that unknown keys
 * can be detected and handled per the unknownOptionHandling policy.
 */
public record RenderRequest(String content, Map<String, Object> options) {
}
