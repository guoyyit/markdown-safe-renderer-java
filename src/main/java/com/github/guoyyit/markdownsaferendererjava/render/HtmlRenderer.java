package com.github.guoyyit.markdownsaferendererjava.render;

import com.github.guoyyit.markdownsaferendererjava.ast.*;
import com.github.guoyyit.markdownsaferendererjava.options.RenderOptions;
import com.github.guoyyit.markdownsaferendererjava.sanitize.HtmlSanitizer;
import com.github.guoyyit.markdownsaferendererjava.toc.TocEntry;

/**
 * Renders a parsed document to restricted HTML. All text is escaped; raw HTML
 * passes through the sanitizer; link and image URLs are protocol-checked.
 * Heading anchors and numbers match the TOC exactly.
 *
 * <p>Output is option-driven; every option defaults to the behaviour that
 * older schema versions produced, so adding options never changes existing
 * output unless explicitly requested.
 */
public final class HtmlRenderer {
    private final HtmlSanitizer sanitizer;

    public HtmlRenderer(HtmlSanitizer sanitizer) {
        this.sanitizer = sanitizer;
    }

    public String render(Document document, TocEntry toc, RenderOptions options) {
        boolean includeToc = options.getBoolean(RenderOptions.KEY_TOC, true);
        boolean showNumbers = options.getBoolean(RenderOptions.KEY_HEADING_NUMBERING, true);
        boolean allowRawHtml = options.getBoolean(RenderOptions.KEY_ALLOW_RAW_HTML, true);
        StringBuilder out = new StringBuilder();
        if (includeToc && toc != null && !toc.getChildren().isEmpty()) {
            renderToc(toc, showNumbers, out);
        }
        if (document != null) {
            renderChildren(document, showNumbers, allowRawHtml, out);
        }
        return out.toString();
    }

    private void renderToc(TocEntry root, boolean showNumbers, StringBuilder out) {
        out.append("<nav class=\"toc\">");
        renderTocList(root, showNumbers, out);
        out.append("</nav>\n");
    }

    private void renderTocList(TocEntry parent, boolean showNumbers, StringBuilder out) {
        out.append("<ul>");
        for (TocEntry entry : parent.getChildren()) {
            out.append("<li><a href=\"#")
                    .append(HtmlSanitizer.escapeAttribute(entry.getAnchor()))
                    .append("\">");
            if (showNumbers && !entry.getNumber().isEmpty()) {
                out.append(HtmlSanitizer.escapeText(entry.getNumber())).append(' ');
            }
            out.append(HtmlSanitizer.escapeText(entry.getTitle()));
            out.append("</a>");
            if (!entry.getChildren().isEmpty()) {
                renderTocList(entry, showNumbers, out);
            }
            out.append("</li>");
        }
        out.append("</ul>");
    }

    private void renderChildren(Node node, boolean showNumbers, boolean allowRawHtml,
                                StringBuilder out) {
        for (Node child : node.children()) {
            renderNode(child, showNumbers, allowRawHtml, out);
        }
    }

    private void renderNode(Node node, boolean showNumbers, boolean allowRawHtml,
                            StringBuilder out) {
        switch (node.type()) {
            case HEADING -> renderHeading((Heading) node, showNumbers, allowRawHtml, out);
            case PARAGRAPH -> {
                out.append("<p>");
                renderChildren(node, showNumbers, allowRawHtml, out);
                out.append("</p>\n");
            }
            case LIST_BLOCK -> renderList((ListBlock) node, showNumbers, allowRawHtml, out);
            case LIST_ITEM -> {
                out.append("<li>");
                renderChildren(node, showNumbers, allowRawHtml, out);
                out.append("</li>\n");
            }
            case TABLE -> renderTable((TableBlock) node, showNumbers, allowRawHtml, out);
            case CODE_BLOCK -> renderCodeBlock((CodeBlock) node, out);
            case QUOTE -> {
                out.append("<blockquote>\n");
                renderChildren(node, showNumbers, allowRawHtml, out);
                out.append("</blockquote>\n");
            }
            case LINK -> renderLink((Link) node, showNumbers, allowRawHtml, out);
            case IMAGE -> renderImage((Image) node, out);
            case TEXT -> out.append(HtmlSanitizer.escapeText(((Text) node).getContent()));
            case RAW_HTML -> {
                if (allowRawHtml) {
                    out.append(sanitizer.sanitize(((RawHtml) node).getHtml()).getHtml());
                } else {
                    out.append(HtmlSanitizer.escapeText(((RawHtml) node).getHtml()));
                }
            }
            case THEMATIC_BREAK -> out.append("<hr/>\n");
            case STRONG -> {
                out.append("<strong>");
                renderChildren(node, showNumbers, allowRawHtml, out);
                out.append("</strong>");
            }
            case EMPHASIS -> {
                out.append("<em>");
                renderChildren(node, showNumbers, allowRawHtml, out);
                out.append("</em>");
            }
            case CODE_INLINE -> {
                out.append("<code>");
                out.append(HtmlSanitizer.escapeText(((CodeInline) node).getCode()));
                out.append("</code>");
            }
            default -> renderChildren(node, showNumbers, allowRawHtml, out);
        }
    }

    private void renderHeading(Heading heading, boolean showNumbers, boolean allowRawHtml,
                               StringBuilder out) {
        int level = Math.max(1, Math.min(6, heading.getLevel()));
        out.append("<h").append(level);
        if (heading.getAnchor() != null) {
            out.append(" id=\"").append(HtmlSanitizer.escapeAttribute(heading.getAnchor())).append('"');
        }
        out.append('>');
        if (showNumbers && heading.getNumber() != null && !heading.getNumber().isEmpty()) {
            out.append("<span class=\"heading-number\">")
                    .append(HtmlSanitizer.escapeText(heading.getNumber()))
                    .append("</span> ");
        }
        renderChildren(heading, showNumbers, allowRawHtml, out);
        out.append("</h").append(level).append(">\n");
    }

    private void renderList(ListBlock list, boolean showNumbers, boolean allowRawHtml,
                            StringBuilder out) {
        if (list.isOrdered()) {
            out.append("<ol");
            if (list.getStartNumber() > 1) {
                out.append(" start=\"").append(list.getStartNumber()).append('"');
            }
            out.append(">\n");
            renderChildren(list, showNumbers, allowRawHtml, out);
            out.append("</ol>\n");
        } else {
            out.append("<ul>\n");
            renderChildren(list, showNumbers, allowRawHtml, out);
            out.append("</ul>\n");
        }
    }

    private void renderTable(TableBlock table, boolean showNumbers, boolean allowRawHtml,
                             StringBuilder out) {
        out.append("<table>\n");
        boolean bodyOpened = false;
        for (Node rowNode : table.children()) {
            TableRow row = (TableRow) rowNode;
            if (row.isHeader()) {
                out.append("<thead><tr>");
                for (Node cell : row.children()) {
                    out.append("<th>");
                    renderChildren(cell, showNumbers, allowRawHtml, out);
                    out.append("</th>");
                }
                out.append("</tr></thead>\n");
            } else {
                if (!bodyOpened) {
                    out.append("<tbody>\n");
                    bodyOpened = true;
                }
                out.append("<tr>");
                for (Node cell : row.children()) {
                    out.append("<td>");
                    renderChildren(cell, showNumbers, allowRawHtml, out);
                    out.append("</td>");
                }
                out.append("</tr>\n");
            }
        }
        if (bodyOpened) {
            out.append("</tbody>\n");
        }
        out.append("</table>\n");
    }

    private void renderCodeBlock(CodeBlock code, StringBuilder out) {
        out.append("<pre><code");
        if (code.getLanguage() != null && !code.getLanguage().isEmpty()) {
            out.append(" class=\"language-")
                    .append(HtmlSanitizer.escapeAttribute(code.getLanguage()))
                    .append('"');
        }
        out.append('>');
        out.append(HtmlSanitizer.escapeText(code.getCode()));
        out.append("</code></pre>\n");
    }

    private void renderLink(Link link, boolean showNumbers, boolean allowRawHtml,
                            StringBuilder out) {
        String dest = link.getDestination();
        if (dest != null && sanitizer.isUrlSafe(dest)) {
            out.append("<a href=\"").append(HtmlSanitizer.escapeAttribute(dest)).append('"');
            if (link.getTitle() != null) {
                out.append(" title=\"").append(HtmlSanitizer.escapeAttribute(link.getTitle())).append('"');
            }
            out.append('>');
            renderChildren(link, showNumbers, allowRawHtml, out);
            out.append("</a>");
        } else {
            // unsafe destination: render the label without the link
            renderChildren(link, showNumbers, allowRawHtml, out);
        }
    }

    private void renderImage(Image image, StringBuilder out) {
        String src = image.getSource();
        if (src == null || !sanitizer.isUrlSafe(src)) {
            // unsafe source: keep only the alt text
            out.append(HtmlSanitizer.escapeText(image.getAltText() == null ? "" : image.getAltText()));
            return;
        }
        out.append("<img src=\"").append(HtmlSanitizer.escapeAttribute(src)).append('"');
        out.append(" alt=\"").append(HtmlSanitizer.escapeAttribute(
                image.getAltText() == null ? "" : image.getAltText())).append('"');
        if (image.getTitle() != null) {
            out.append(" title=\"").append(HtmlSanitizer.escapeAttribute(image.getTitle())).append('"');
        }
        out.append("/>");
    }
}
