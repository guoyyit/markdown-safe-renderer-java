package com.github.guoyyit.markdownsaferendererjava.render;

import java.util.List;

/**
 * One table-of-contents entry. Children form the nested outline.
 */
public record TocEntry(String number, String title, String anchor, int level, List<TocEntry> children) {
}
