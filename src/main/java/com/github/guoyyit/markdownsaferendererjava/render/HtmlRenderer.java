package com.github.guoyyit.markdownsaferendererjava.render;

import com.github.guoyyit.markdownsaferendererjava.ast.Node;
import com.github.guoyyit.markdownsaferendererjava.ast.NodeType;
import com.github.guoyyit.markdownsaferendererjava.options.RenderOptions;
import com.github.guoyyit.markdownsaferendererjava.sanitize.HtmlSanitizer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Renders a parsed document tree to restricted HTML.
 * <p>
 * Heading numbering and anchors are deterministic and globally unique:
 * <ul>
 *   <li>numbers follow the real heading levels (missing levels are kept),
 *       so two identical inputs render identical numbers;</li>
 *   <li>anchor = short input-hash prefix + slug; duplicate titles get a
 *       deterministic "-2", "-3" suffix within the document, and the input
 *       hash prevents collisions across documents even for identical titles;</li>
 *   <li>the generated TOC links exactly the anchors in the rendered HTML.</li>
 * </ul>
 */
public class HtmlRenderer {

    private final HtmlSanitizer sanitizer;

    public HtmlRenderer(HtmlSanitizer sanitizer) {
        this.sanitizer = sanitizer;
    }

    public RenderResult render(Node document, RenderOptions options, String input) {
        String namespace = inputHashPrefix(input);
        Numbering numbering = new Numbering(namespace);
        StringBuilder body = new StringBuilder(4096);
        renderChildren(document, body, options, numbering);

        List<TocEntry> toc = buildToc(numbering.headings, options.headingNumbers());
        String tocHtml = options.generateToc() ? renderToc(toc) : "";
        return new RenderResult(document, body.toString(), toc, tocHtml, List.of());
    }

    /** Kept for tests that already have a document and do not care about anchors. */
    public RenderResult render(Node document, RenderOptions options) {
        return render(document, options, "");
    }

    private static String inputHashPrefix(String input) {
        if (input == null || input.isEmpty()) {
            return "doc";
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(8);
            for (int i = 0; i < 4; i++) {
                sb.append(String.format("%02x", digest[i]));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // ----------------------------------------------------------- numbering

    private static final class HeadingInfo {
        final int level;
        final String title;
        final String number;
        final String anchor;

        HeadingInfo(int level, String title, String number, String anchor) {
            this.level = level;
            this.title = title;
            this.number = number;
            this.anchor = anchor;
        }
    }

    private static final class Numbering {
        final int[] counters = new int[7]; // indexes 1..6
        final Map<String, Integer> usedSlugs = new HashMap<>();
        final List<HeadingInfo> headings = new ArrayList<>();
        final String namespace;

        Numbering(String namespace) {
            this.namespace = namespace;
        }

        HeadingInfo register(int level, String title) {
            counters[level]++;
            for (int k = level + 1; k <= 6; k++) {
                counters[k] = 0;
            }
            StringBuilder num = new StringBuilder();
            for (int k = 1; k <= level; k++) {
                if (counters[k] == 0) {
                    break; // missing level
                }
                if (num.length() > 0) {
                    num.append('.');
                }
                num.append(counters[k]);
            }
            String base = slug(title);
            if (base.isEmpty()) {
                base = "heading";
            }
            String key = namespace + "-" + base;
            int count = usedSlugs.merge(base, 1, Integer::sum);
            String anchor = count == 1 ? key : key + "-" + count;
            HeadingInfo info = new HeadingInfo(level, title, num.toString(), anchor);
            headings.add(info);
            return info;
        }
    }

    static String slug(String title) {
        String s = title.toLowerCase(Locale.ROOT).trim();
        StringBuilder sb = new StringBuilder(s.length());
        boolean lastDash = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                sb.append(c);
                lastDash = false;
            } else if (!lastDash && sb.length() > 0) {
                sb.append('-');
                lastDash = true;
            }
        }
        String out = sb.toString();
        while (out.endsWith("-")) {
            out = out.substring(0, out.length() - 1);
        }
        return out;
    }

    private List<TocEntry> buildToc(List<HeadingInfo> headings, boolean withNumbers) {
        List<TocEntry> roots = new ArrayList<>();
        List<TocEntry> stack = new ArrayList<>();
        List<Integer> levels = new ArrayList<>();
        for (HeadingInfo h : headings) {
            while (!levels.isEmpty() && levels.get(levels.size() - 1) >= h.level) {
                levels.remove(levels.size() - 1);
                stack.remove(stack.size() - 1);
            }
            List<TocEntry> children = new ArrayList<>();
            String title = withNumbers ? h.number + " " + h.title : h.title;
            TocEntry entry = new TocEntry(h.number, title, h.anchor, h.level, children);
            if (stack.isEmpty()) {
                roots.add(entry);
            } else {
                stack.get(stack.size() - 1).children().add(entry);
            }
            stack.add(entry);
            levels.add(h.level);
        }
        return roots;
    }

    private String renderToc(List<TocEntry> entries) {
        StringBuilder sb = new StringBuilder();
        renderTocList(entries, sb);
        return sb.toString();
    }

    private void renderTocList(List<TocEntry> entries, StringBuilder sb) {
        if (entries.isEmpty()) {
            return;
        }
        sb.append("<ul class=\"toc\">");
        for (TocEntry e : entries) {
            sb.append("<li><a href=\"#").append(e.anchor()).append("\">");
            HtmlSanitizer.escapeText(e.title(), 0, e.title().length(), sb);
            sb.append("</a>");
            renderTocList(e.children(), sb);
            sb.append("</li>");
        }
        sb.append("</ul>");
    }

    // -------------------------------------------------------------- render

    private void renderChildren(Node parent, StringBuilder out, RenderOptions options,
                                Numbering numbering) {
        List<Node> children = parent.getChildren();
        for (int idx = 0; idx < children.size(); idx++) {
            Node child = children.get(idx);
            if (child.getType() == NodeType.RAW_HTML
                    && options.sanitizeHtml() && options.rawHtmlAllowed()) {
                // Sanitize a whole run of raw html + interleaved text at once,
                // so constructs like <script>...</script> are handled as a unit
                // and script content cannot leak out as plain text.
                StringBuilder run = new StringBuilder();
                run.append(child.getLiteral() == null ? "" : child.getLiteral());
                while (idx + 1 < children.size()) {
                    Node next = children.get(idx + 1);
                    if (next.getType() == NodeType.RAW_HTML || next.getType() == NodeType.TEXT) {
                        run.append(next.getLiteral() == null ? "" : next.getLiteral());
                        idx++;
                    } else {
                        break;
                    }
                }
                out.append(sanitizer.sanitize(run.toString()));
                continue;
            }
            renderNode(child, out, options, numbering);
        }
    }

    private void renderNode(Node node, StringBuilder out, RenderOptions options,
                            Numbering numbering) {
        if (Thread.currentThread().isInterrupted()) {
            throw new com.github.guoyyit.markdownsaferendererjava.limits
                    .LimitExceededException(
                    com.github.guoyyit.markdownsaferendererjava.limits.LimitReason.PROCESSING_TIMEOUT,
                    "render aborted (timeout)");
        }
        switch (node.getType()) {
            case DOCUMENT -> renderChildren(node, out, options, numbering);
            case HEADING -> renderHeading(node, out, options, numbering);
            case PARAGRAPH -> {
                out.append("<p>");
                renderChildren(node, out, options, numbering);
                out.append("</p>");
            }
            case TEXT -> HtmlSanitizer.escapeText(node.getLiteral(), 0,
                    node.getLiteral().length(), out);
            case CODE_SPAN -> {
                out.append("<code>");
                String lit = node.getLiteral() == null ? "" : node.getLiteral();
                HtmlSanitizer.escapeText(lit, 0, lit.length(), out);
                out.append("</code>");
            }
            case CODE_BLOCK -> renderCodeBlock(node, out);
            case BLOCK_QUOTE -> {
                out.append("<blockquote>");
                renderChildren(node, out, options, numbering);
                out.append("</blockquote>");
            }
            case ORDERED_LIST -> {
                String start = node.getAttributes().get("start");
                out.append("<ol");
                if (start != null) {
                    out.append(" start=\"").append(start).append('"');
                }
                out.append('>');
                renderChildren(node, out, options, numbering);
                out.append("</ol>");
            }
            case UNORDERED_LIST -> {
                out.append("<ul>");
                renderChildren(node, out, options, numbering);
                out.append("</ul>");
            }
            case LIST_ITEM -> {
                out.append("<li>");
                renderChildren(node, out, options, numbering);
                out.append("</li>");
            }
            case TABLE -> renderTable(node, out, options, numbering);
            case TABLE_ROW -> renderRow(node, out, options, numbering);
            case TABLE_CELL -> renderCell(node, out, options, numbering, false);
            case LINK -> renderLink(node, out, options, numbering);
            case IMAGE -> renderImage(node, out);
            case RAW_HTML -> renderRawHtml(node, out, options);
            case THEMATIC_BREAK -> out.append("<hr/>");
        }
    }

    private void renderHeading(Node node, StringBuilder out, RenderOptions options,
                               Numbering numbering) {
        int level = Integer.parseInt(node.getAttributes().getOrDefault("level", "1"));
        String title = plainText(node);
        HeadingInfo info = numbering.register(level, title);
        out.append("<h").append(level).append(" id=\"").append(info.anchor).append("\">");
        if (options.headingNumbers()) {
            out.append("<span class=\"heading-number\">").append(info.number)
                    .append(".</span> ");
        }
        renderChildren(node, out, options, numbering);
        out.append("</h").append(level).append('>');
    }

    private void renderCodeBlock(Node node, StringBuilder out) {
        String info = node.getAttributes().get("info");
        out.append("<pre><code");
        if (info != null && info.matches("[a-zA-Z0-9_.+-]*")) {
            out.append(" class=\"language-").append(info).append('"');
        }
        out.append('>');
        String lit = node.getLiteral() == null ? "" : node.getLiteral();
        HtmlSanitizer.escapeText(lit, 0, lit.length(), out);
        out.append("</code></pre>");
    }

    private void renderTable(Node node, StringBuilder out, RenderOptions options,
                             Numbering numbering) {
        out.append("<table>");
        boolean firstRow = true;
        for (Node child : node.getChildren()) {
            if (child.getType() == NodeType.TABLE_ROW
                    && "true".equals(child.getAttributes().get("header"))) {
                out.append("<thead>");
                renderNode(child, out, options, numbering);
                out.append("</thead><tbody>");
                firstRow = false;
            }
        }
        boolean bodyOpened = !firstRow;
        for (Node child : node.getChildren()) {
            if (child.getType() != NodeType.TABLE_ROW) {
                continue;
            }
            if ("true".equals(child.getAttributes().get("header"))) {
                continue;
            }
            renderNode(child, out, options, numbering);
        }
        if (bodyOpened) {
            out.append("</tbody>");
        }
        out.append("</table>");
    }

    private void renderRow(Node node, StringBuilder out, RenderOptions options,
                           Numbering numbering) {
        boolean header = "true".equals(node.getAttributes().get("header"));
        out.append("<tr>");
        for (Node cell : node.getChildren()) {
            renderCell(cell, out, options, numbering, header);
        }
        out.append("</tr>");
    }

    private void renderCell(Node node, StringBuilder out, RenderOptions options,
                            Numbering numbering, boolean header) {
        String align = node.getAttributes().get("align");
        out.append(header ? "<th" : "<td");
        if (align != null) {
            out.append(" align=\"").append(align).append('"');
        }
        out.append('>');
        renderChildren(node, out, options, numbering);
        out.append(header ? "</th>" : "</td>");
    }

    private void renderLink(Node node, StringBuilder out, RenderOptions options,
                            Numbering numbering) {
        String href = node.getAttributes().getOrDefault("href", "");
        String title = node.getAttributes().get("title");
        if (!sanitizer.isSafeUrl(href)) {
            // dangerous protocol: render as inert text instead
            renderChildren(node, out, options, numbering);
            return;
        }
        out.append("<a href=\"");
        HtmlSanitizer.escapeAttribute(href, out);
        out.append('"');
        if (title != null && !title.isBlank()) {
            out.append(" title=\"");
            HtmlSanitizer.escapeAttribute(title, out);
            out.append('"');
        }
        out.append('>');
        renderChildren(node, out, options, numbering);
        out.append("</a>");
    }

    private void renderImage(Node node, StringBuilder out) {
        String src = node.getAttributes().getOrDefault("src", "");
        String alt = node.getAttributes().getOrDefault("alt", "");
        String title = node.getAttributes().get("title");
        if (!sanitizer.isSafeUrl(src)) {
            out.append("<span class=\"unsafe-image\">");
            HtmlSanitizer.escapeText(alt, 0, alt.length(), out);
            out.append("</span>");
            return;
        }
        out.append("<img src=\"");
        HtmlSanitizer.escapeAttribute(src, out);
        out.append("\" alt=\"");
        HtmlSanitizer.escapeAttribute(alt, out);
        out.append('"');
        if (title != null && !title.isBlank()) {
            out.append(" title=\"");
            HtmlSanitizer.escapeAttribute(title, out);
            out.append('"');
        }
        out.append("/>");
    }

    private void renderRawHtml(Node node, StringBuilder out, RenderOptions options) {
        String raw = node.getLiteral() == null ? "" : node.getLiteral();
        if (options.sanitizeHtml() && options.rawHtmlAllowed()) {
            out.append(sanitizer.sanitize(raw));
        } else {
            // default: raw html is never trusted; show it as literal text
            HtmlSanitizer.escapeText(raw, 0, raw.length(), out);
        }
    }

    static String plainText(Node node) {
        StringBuilder sb = new StringBuilder();
        collectText(node, sb);
        return sb.toString().trim();
    }

    private static void collectText(Node node, StringBuilder sb) {
        if (node.getType() == NodeType.TEXT || node.getType() == NodeType.CODE_SPAN) {
            sb.append(node.getLiteral());
            return;
        }
        for (Node c : node.getChildren()) {
            collectText(c, sb);
        }
    }
}
