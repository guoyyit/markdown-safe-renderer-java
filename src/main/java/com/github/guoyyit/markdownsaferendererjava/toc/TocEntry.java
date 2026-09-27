package com.github.guoyyit.markdownsaferendererjava.toc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One table-of-contents entry; children form the nested TOC tree. */
public final class TocEntry {
    private final String number;
    private final String title;
    private final String anchor;
    private final int level;
    private final List<TocEntry> children = new ArrayList<>();

    public TocEntry(String number, String title, String anchor, int level) {
        this.number = number;
        this.title = title;
        this.anchor = anchor;
        this.level = level;
    }

    public String getNumber() { return number; }
    public String getTitle() { return title; }
    public String getAnchor() { return anchor; }
    public int getLevel() { return level; }
    public List<TocEntry> getChildren() { return Collections.unmodifiableList(children); }
    public void addChild(TocEntry child) { children.add(child); }
}
