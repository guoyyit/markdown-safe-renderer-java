package com.github.guoyyit.markdownsaferendererjava.web;

/**
 * Error body; {@code reason} distinguishes rejection causes.
 */
public record ErrorResponse(String reason, String message) {
}
