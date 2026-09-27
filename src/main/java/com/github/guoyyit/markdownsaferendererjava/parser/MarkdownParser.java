package com.github.guoyyit.markdownsaferendererjava.parser;

import com.github.guoyyit.markdownsaferendererjava.ast.*;
import com.github.guoyyit.markdownsaferendererjava.limits.LimitExceededException;
import com.github.guoyyit.markdownsaferendererjava.limits.RejectionReason;
import com.github.guoyyit.markdownsaferendererjava.limits.RuntimeBudget;
import com.github.guoyyit.markdownsaferendererjava.sanitize.HtmlSanitizer;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses untrusted Markdown into a structured AST.
 *
 * <p>Recovery model: malformed constructs (unclosed fences, broken tables,
 * unbalanced inline markup) never abort the whole parse. The offending span
 * is skipped or degraded to plain text and a {@link ParseError} with the
 * source location and the skip reason is reported. Fixing the input and
 * retrying yields a clean parse.
 */
public final class MarkdownParser {

    public MarkdownParser() {
    }

    public ParseResult parse(String source, RuntimeBudget budget) {
        List<ParseError> errors = new ArrayList<>();
        Document doc = new Document(new SourceLocation(1, 1, 0));
        if (source == null || source.isEmpty()) {
            return new ParseResult(doc, errors);
        }
        List<Line> lines = splitLines(source);
        parseBlocks(lines, 0, lines.size(), 1, doc, budget, errors);
        return new ParseResult(doc, errors);
    }

    // ---------------------------------------------------------------- lines

    private record Line(int number, int offset, String text) {
    }

    private static List<Line> splitLines(String source) {
        List<Line> lines = new ArrayList<>();
        int start = 0;
        int number = 1;
        int n = source.length();
        for (int i = 0; i < n; i++) {
            char c = source.charAt(i);
            if (c == '\n') {
                lines.add(new Line(number, start, stripCarriage(source.substring(start, i))));
                number++;
                start = i + 1;
            }
        }
        if (start < n) {
            lines.add(new Line(number, start, stripCarriage(source.substring(start))));
        }
        return lines;
    }

    private static String stripCarriage(String s) {
        return s.endsWith("\r") ? s.substring(0, s.length() - 1) : s;
    }

    private static SourceLocation loc(Line line, int column) {
        return new SourceLocation(line.number, column, line.offset);
    }

    // -------------------------------------------------------------- blocks

    private void parseBlocks(List<Line> lines, int from, int to, int depth,
                             Node parent, RuntimeBudget budget, List<ParseError> errors) {
        budget.recordDepth(depth);
        int i = from;
        while (i < to) {
            budget.checkTimeout();
            Line line = lines.get(i);
            String text = line.text;
            if (text.isBlank()) {
                i++;
                continue;
            }
            FenceMatch fence = matchFence(text);
            if (fence != null) {
                i = parseFencedCode(lines, i, to, fence, parent, budget, errors);
                continue;
            }
            int atxLevel = matchAtxHeading(text);
            if (atxLevel > 0) {
                Heading h = new Heading(loc(line, 1), atxLevel);
                String content = text.strip().substring(atxLevel).strip();
                // strip optional closing ### sequence
                content = content.replaceAll("\\s+#+\\s*$", "");
                parseInlines(content, line.number, atxLevel + 2, h, budget, errors);
                parent.addChild(h);
                i++;
                continue;
            }
            if (isThematicBreak(text)) {
                parent.addChild(new ThematicBreak(loc(line, 1)));
                i++;
                continue;
            }
            if (i + 1 < to && looksLikeTableRow(text) && isTableDelimiter(lines.get(i + 1).text)) {
                i = parseTable(lines, i, to, parent, budget, errors);
                continue;
            }
            if (isQuoteLine(text)) {
                i = parseQuote(lines, i, to, depth, parent, budget, errors);
                continue;
            }
            ListMarker marker = matchListMarker(text);
            if (marker != null) {
                i = parseList(lines, i, to, depth, parent, budget, errors, marker);
                continue;
            }
            if (startsHtmlBlock(text)) {
                i = parseHtmlBlock(lines, i, to, parent, errors);
                continue;
            }
            if (indentOf(text) >= 4) {
                i = parseIndentedCode(lines, i, to, parent, budget);
                continue;
            }
            if (i + 1 < to && isSetextUnderline(lines.get(i + 1).text)) {
                int level = lines.get(i + 1).text.strip().startsWith("=") ? 1 : 2;
                Heading h = new Heading(loc(line, 1), level);
                parseInlines(text.strip(), line.number, 1, h, budget, errors);
                parent.addChild(h);
                i += 2;
                continue;
            }
            i = parseParagraph(lines, i, to, parent, budget, errors);
        }
    }

    private int parseFencedCode(List<Line> lines, int i, int to, FenceMatch fence,
                                Node parent, RuntimeBudget budget, List<ParseError> errors) {
        StringBuilder code = new StringBuilder();
        int j = i + 1;
        boolean closed = false;
        while (j < to) {
            budget.checkTimeout();
            String t = lines.get(j).text;
            if (isClosingFence(t, fence)) {
                closed = true;
                j++;
                break;
            }
            code.append(t).append('\n');
            j++;
        }
        if (!closed) {
            errors.add(new ParseError(ParseError.Severity.ERROR, loc(lines.get(i), 1),
                    "unclosed code fence",
                    "content up to end of input treated as code"));
        }
        parent.addChild(new CodeBlock(loc(lines.get(i), 1), fence.language, code.toString()));
        return j;
    }

    private int parseQuote(List<Line> lines, int i, int to, int depth,
                           Node parent, RuntimeBudget budget, List<ParseError> errors) {
        List<Line> inner = new ArrayList<>();
        int start = i;
        while (i < to && isQuoteLine(lines.get(i).text)) {
            budget.checkTimeout();
            Line l = lines.get(i);
            inner.add(new Line(l.number, l.offset, stripQuotePrefix(l.text)));
            i++;
        }
        QuoteBlock quote = new QuoteBlock(loc(lines.get(start), 1));
        parseBlocks(inner, 0, inner.size(), depth + 1, quote, budget, errors);
        parent.addChild(quote);
        return i;
    }

    private int parseList(List<Line> lines, int i, int to, int depth,
                          Node parent, RuntimeBudget budget, List<ParseError> errors,
                          ListMarker first) {
        ListBlock list = new ListBlock(loc(lines.get(i), 1), first.ordered, first.number);
        int baseIndent = first.indent;
        while (i < to) {
            budget.checkTimeout();
            Line line = lines.get(i);
            ListMarker m = matchListMarker(line.text);
            if (m == null || m.indent != baseIndent || m.ordered != first.ordered) {
                break;
            }
            List<Line> itemLines = new ArrayList<>();
            itemLines.add(new Line(line.number, line.offset, line.text.substring(m.contentStart)));
            i++;
            while (i < to) {
                Line cont = lines.get(i);
                if (cont.text.isBlank()) {
                    if (i + 1 < to && !lines.get(i + 1).text.isBlank()
                            && indentOf(lines.get(i + 1).text) > baseIndent) {
                        itemLines.add(new Line(cont.number, cont.offset, ""));
                        i++;
                        continue;
                    }
                    break;
                }
                ListMarker sibling = matchListMarker(cont.text);
                if (sibling != null && sibling.indent == baseIndent) {
                    break; // next item of this list
                }
                int ind = indentOf(cont.text);
                if (ind > baseIndent) {
                    int strip = Math.min(ind, baseIndent + 2);
                    itemLines.add(new Line(cont.number, cont.offset, cont.text.substring(strip)));
                    i++;
                } else {
                    break;
                }
            }
            ListItem item = new ListItem(loc(line, 1));
            parseBlocks(itemLines, 0, itemLines.size(), depth + 1, item, budget, errors);
            list.addChild(item);
        }
        parent.addChild(list);
        return i;
    }

    private int parseTable(List<Line> lines, int i, int to,
                           Node parent, RuntimeBudget budget, List<ParseError> errors) {
        Line headerLine = lines.get(i);
        List<String> headers = splitTableRow(headerLine.text);
        int cols = headers.size();
        if (cols > budget.limits().getMaxTableColumns()) {
            throw new LimitExceededException(RejectionReason.TABLE_TOO_LARGE,
                    "table has " + cols + " columns, limit is "
                            + budget.limits().getMaxTableColumns());
        }
        TableBlock table = new TableBlock(loc(headerLine, 1));
        TableRow header = new TableRow(loc(headerLine, 1), true);
        for (String h : headers) {
            TableCell cell = new TableCell(loc(headerLine, 1));
            parseInlines(h.strip(), headerLine.number, 1, cell, budget, errors);
            header.addChild(cell);
        }
        table.addChild(header);
        int j = i + 2;
        int rows = 0;
        while (j < to && !lines.get(j).text.isBlank() && looksLikeTableRow(lines.get(j).text)) {
            budget.checkTimeout();
            rows++;
            if (rows > budget.limits().getMaxTableRows()) {
                throw new LimitExceededException(RejectionReason.TABLE_TOO_LARGE,
                        "table has more than " + budget.limits().getMaxTableRows() + " rows");
            }
            Line rowLine = lines.get(j);
            List<String> cells = splitTableRow(rowLine.text);
            if (cells.size() != cols) {
                errors.add(new ParseError(ParseError.Severity.WARNING, loc(rowLine, 1),
                        "table row has " + cells.size() + " cells, expected " + cols,
                        "row padded with empty cells or truncated to fit"));
            }
            TableRow row = new TableRow(loc(rowLine, 1), false);
            for (int c = 0; c < cols; c++) {
                String content = c < cells.size() ? cells.get(c).strip() : "";
                TableCell cell = new TableCell(loc(rowLine, 1));
                parseInlines(content, rowLine.number, 1, cell, budget, errors);
                row.addChild(cell);
            }
            table.addChild(row);
            j++;
        }
        parent.addChild(table);
        return j;
    }

    private int parseHtmlBlock(List<Line> lines, int i, int to,
                               Node parent, List<ParseError> errors) {
        StringBuilder html = new StringBuilder();
        int start = i;
        while (i < to && !lines.get(i).text.isBlank()) {
            html.append(lines.get(i).text).append('\n');
            i++;
        }
        parent.addChild(new RawHtml(loc(lines.get(start), 1), html.toString()));
        return i;
    }

    private int parseIndentedCode(List<Line> lines, int i, int to,
                                  Node parent, RuntimeBudget budget) {
        StringBuilder code = new StringBuilder();
        int start = i;
        while (i < to) {
            budget.checkTimeout();
            String t = lines.get(i).text;
            if (t.isBlank()) {
                code.append('\n');
                i++;
                continue;
            }
            if (indentOf(t) < 4) {
                break;
            }
            code.append(t.substring(4)).append('\n');
            i++;
        }
        parent.addChild(new CodeBlock(loc(lines.get(start), 1), "", code.toString()));
        return i;
    }

    private int parseParagraph(List<Line> lines, int i, int to,
                               Node parent, RuntimeBudget budget, List<ParseError> errors) {
        StringBuilder sb = new StringBuilder();
        int start = i;
        sb.append(lines.get(i).text.strip());
        i++;
        while (i < to) {
            budget.checkTimeout();
            String t = lines.get(i).text;
            if (t.isBlank() || isBlockStart(lines, i, to)) {
                break;
            }
            sb.append('\n').append(t.strip());
            i++;
        }
        Paragraph p = new Paragraph(loc(lines.get(start), 1));
        parseInlines(sb.toString(), lines.get(start).number, 1, p, budget, errors);
        parent.addChild(p);
        return i;
    }

    private boolean isBlockStart(List<Line> lines, int i, int to) {
        String t = lines.get(i).text;
        if (t.isBlank()) {
            return true;
        }
        return matchFence(t) != null
                || matchAtxHeading(t) > 0
                || isThematicBreak(t)
                || isQuoteLine(t)
                || matchListMarker(t) != null
                || startsHtmlBlock(t)
                || (i + 1 < to && looksLikeTableRow(t) && isTableDelimiter(lines.get(i + 1).text));
    }

    // ------------------------------------------------------- block matchers

    private record FenceMatch(String marker, String language) {
    }

    private static FenceMatch matchFence(String text) {
        String t = text.strip();
        if (t.startsWith("```")) {
            return new FenceMatch("```", t.substring(3).strip());
        }
        if (t.startsWith("~~~")) {
            return new FenceMatch("~~~", t.substring(3).strip());
        }
        return null;
    }

    private static boolean isClosingFence(String text, FenceMatch fence) {
        String t = text.strip();
        return t.startsWith(fence.marker)
                && t.substring(fence.marker.length()).strip().isEmpty();
    }

    private static int matchAtxHeading(String text) {
        String t = text.strip();
        int level = 0;
        while (level < t.length() && t.charAt(level) == '#' && level < 6) {
            level++;
        }
        if (level == 0 || level >= t.length()) {
            return level > 0 && level == t.length() ? 0 : 0; // "#" alone is a paragraph
        }
        return t.charAt(level) == ' ' || t.charAt(level) == '\t' ? level : 0;
    }

    private static boolean isThematicBreak(String text) {
        String t = text.strip();
        if (t.length() < 3) {
            return false;
        }
        char c = t.charAt(0);
        if (c != '-' && c != '*' && c != '_') {
            return false;
        }
        int count = 0;
        for (int k = 0; k < t.length(); k++) {
            char ch = t.charAt(k);
            if (ch == c) {
                count++;
            } else if (ch != ' ' && ch != '\t') {
                return false;
            }
        }
        return count >= 3;
    }

    private static boolean isQuoteLine(String text) {
        String t = text.stripLeading();
        return t.startsWith(">");
    }

    private static String stripQuotePrefix(String text) {
        String t = text.stripLeading();
        if (t.startsWith(">")) {
            t = t.substring(1);
            if (t.startsWith(" ")) {
                t = t.substring(1);
            }
        }
        return t;
    }

    private record ListMarker(int indent, boolean ordered, int number, int contentStart) {
    }

    private static ListMarker matchListMarker(String text) {
        int indent = indentOf(text);
        int i = indent;
        int n = text.length();
        if (i >= n) {
            return null;
        }
        char c = text.charAt(i);
        if (c == '-' || c == '+' || c == '*') {
            if (i + 1 < n && (text.charAt(i + 1) == ' ' || text.charAt(i + 1) == '\t')) {
                return new ListMarker(indent, false, 0, skipSpaces(text, i + 1));
            }
            return null;
        }
        if (Character.isDigit(c)) {
            int j = i;
            long num = 0;
            while (j < n && Character.isDigit(text.charAt(j)) && j - i < 10) {
                num = num * 10 + (text.charAt(j) - '0');
                j++;
            }
            if (j < n && (text.charAt(j) == '.' || text.charAt(j) == ')')
                    && j + 1 < n && (text.charAt(j + 1) == ' ' || text.charAt(j + 1) == '\t')) {
                return new ListMarker(indent, true, (int) num, skipSpaces(text, j + 1));
            }
        }
        return null;
    }

    private static int skipSpaces(String text, int i) {
        while (i < text.length() && (text.charAt(i) == ' ' || text.charAt(i) == '\t')) {
            i++;
        }
        return i;
    }

    private static int indentOf(String text) {
        int indent = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ' ') {
                indent++;
            } else if (c == '\t') {
                indent += 4;
            } else {
                break;
            }
        }
        return indent;
    }

    private static boolean isSetextUnderline(String text) {
        String t = text.strip();
        if (t.isEmpty()) {
            return false;
        }
        char c = t.charAt(0);
        if (c != '=' && c != '-') {
            return false;
        }
        for (int i = 0; i < t.length(); i++) {
            if (t.charAt(i) != c) {
                return false;
            }
        }
        return true;
    }

    private static boolean startsHtmlBlock(String text) {
        String t = text.stripLeading();
        return t.length() > 1 && t.charAt(0) == '<'
                && (Character.isLetter(t.charAt(1)) || t.charAt(1) == '/'
                || t.charAt(1) == '!' || t.charAt(1) == '?');
    }

    private static boolean looksLikeTableRow(String text) {
        return text.contains("|");
    }

    private static boolean isTableDelimiter(String text) {
        String t = text.strip();
        if (t.isEmpty()) {
            return false;
        }
        List<String> cells = splitTableRow(t);
        if (cells.isEmpty()) {
            return false;
        }
        for (String cell : cells) {
            String c = cell.strip();
            if (c.isEmpty()) {
                return false;
            }
            int dashes = 0;
            for (int k = 0; k < c.length(); k++) {
                char ch = c.charAt(k);
                if (ch == '-') {
                    dashes++;
                } else if (ch != ':') {
                    return false;
                }
            }
            if (dashes == 0) {
                return false;
            }
        }
        return true;
    }

    static List<String> splitTableRow(String text) {
        String t = text.strip();
        if (t.startsWith("|")) {
            t = t.substring(1);
        }
        if (t.endsWith("|") && !t.endsWith("\\|")) {
            t = t.substring(0, t.length() - 1);
        }
        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c == '\\' && i + 1 < t.length() && t.charAt(i + 1) == '|') {
                current.append('|');
                i++;
            } else if (c == '|') {
                cells.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        cells.add(current.toString());
        return cells;
    }

    // -------------------------------------------------------------- inlines

    private void parseInlines(String text, int line, int colBase, Node parent,
                              RuntimeBudget budget, List<ParseError> errors) {
        int i = 0;
        int n = text.length();
        StringBuilder buf = new StringBuilder();
        while (i < n) {
            budget.checkTimeout();
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < n && isEscapable(text.charAt(i + 1))) {
                buf.append(text.charAt(i + 1));
                i += 2;
                continue;
            }
            if (c == '!' && i + 1 < n && text.charAt(i + 1) == '[') {
                InlineNodeResult r = matchLinkOrImage(text, i + 1, line, colBase, true, budget, errors);
                if (r != null) {
                    flushText(buf, parent, line, colBase);
                    parent.addChild(r.node());
                    i = r.nextIndex();
                    continue;
                }
            }
            if (c == '[') {
                InlineNodeResult r = matchLinkOrImage(text, i, line, colBase, false, budget, errors);
                if (r != null) {
                    flushText(buf, parent, line, colBase);
                    parent.addChild(r.node());
                    i = r.nextIndex();
                    continue;
                }
            }
            if (c == '`') {
                int ticks = countRun(text, i, '`');
                int close = findClosingRun(text, i + ticks, '`', ticks);
                if (close >= 0) {
                    flushText(buf, parent, line, colBase);
                    parent.addChild(new CodeInline(new SourceLocation(line, colBase + i, -1),
                            text.substring(i + ticks, close)));
                    i = close + ticks;
                    continue;
                }
            }
            if ((c == '*' || c == '_') && i + 1 < n && text.charAt(i + 1) == c) {
                int close = findClosingRun(text, i + 2, c, 2);
                if (close >= 0 && close > i + 2) {
                    flushText(buf, parent, line, colBase);
                    Strong strong = new Strong(new SourceLocation(line, colBase + i, -1));
                    parseInlines(text.substring(i + 2, close), line, colBase + i + 2, strong, budget, errors);
                    parent.addChild(strong);
                    i = close + 2;
                    continue;
                }
            }
            if (c == '*' || c == '_') {
                int close = findClosingRun(text, i + 1, c, 1);
                if (close >= 0 && close > i + 1) {
                    flushText(buf, parent, line, colBase);
                    Emphasis em = new Emphasis(new SourceLocation(line, colBase + i, -1));
                    parseInlines(text.substring(i + 1, close), line, colBase + i + 1, em, budget, errors);
                    parent.addChild(em);
                    i = close + 1;
                    continue;
                }
            }
            if (c == '<' && i + 1 < n
                    && (Character.isLetter(text.charAt(i + 1)) || text.charAt(i + 1) == '/')) {
                int gt = text.indexOf('>', i + 1);
                if (gt > 0) {
                    flushText(buf, parent, line, colBase);
                    String tagName = inlineTagName(text, i);
                    if (tagName != null
                            && HtmlSanitizer.DISCARD_CONTENT_TAGS.contains(tagName)) {
                        // capture the whole "<script>...</script>" span so the
                        // sanitizer discards the content, not just the tags
                        int end = findDiscardSpanEnd(text, gt, tagName);
                        parent.addChild(new RawHtml(new SourceLocation(line, colBase + i, -1),
                                text.substring(i, end)));
                        i = end;
                        continue;
                    }
                    parent.addChild(new RawHtml(new SourceLocation(line, colBase + i, -1),
                            text.substring(i, gt + 1)));
                    i = gt + 1;
                    continue;
                }
            }
            buf.append(c);
            i++;
        }
        flushText(buf, parent, line, colBase);
    }

    /** Extracts the lower-cased tag name of an inline tag starting at index i. */
    private static String inlineTagName(String text, int i) {
        int j = i + 1;
        if (j < text.length() && text.charAt(j) == '/') {
            j++;
        }
        int start = j;
        while (j < text.length()
                && (Character.isLetterOrDigit(text.charAt(j)) || text.charAt(j) == '-')) {
            j++;
        }
        if (j == start) {
            return null;
        }
        return text.substring(start, j).toLowerCase(java.util.Locale.ROOT);
    }

    /** Returns the index just past the matching close tag, or text.length(). */
    private static int findDiscardSpanEnd(String text, int from, String tagName) {
        String close = "</" + tagName;
        String lower = text.toLowerCase(java.util.Locale.ROOT);
        int idx = lower.indexOf(close, from);
        if (idx < 0) {
            return text.length();
        }
        int gt = text.indexOf('>', idx + close.length());
        return gt < 0 ? text.length() : gt + 1;
    }

    private static void flushText(StringBuilder buf, Node parent, int line, int colBase) {
        if (buf.length() > 0) {
            parent.addChild(new Text(new SourceLocation(line, colBase, -1), buf.toString()));
            buf.setLength(0);
        }
    }

    private static boolean isEscapable(char c) {
        return "\\`*_{}[]()#+-.!|<>\"'".indexOf(c) >= 0;
    }

    private static int countRun(String text, int i, char c) {
        int count = 0;
        while (i + count < text.length() && text.charAt(i + count) == c) {
            count++;
        }
        return count;
    }

    private static int findClosingRun(String text, int from, char c, int count) {
        String needle = String.valueOf(c).repeat(count);
        int idx = text.indexOf(needle, from);
        return idx;
    }

    /** A parsed inline node plus the index just past it. */
    private record InlineNodeResult(Node node, int nextIndex) {
    }

    /**
     * Tries to parse a link or image starting at the '[' index.
     * Returns the node and next index, or null if not a valid link.
     */
    private InlineNodeResult matchLinkOrImage(String text, int openBracket, int line,
                                              int colBase, boolean image,
                                              RuntimeBudget budget, List<ParseError> errors) {
        int n = text.length();
        int closeBracket = findMatchingBracket(text, openBracket);
        if (closeBracket < 0 || closeBracket + 1 >= n || text.charAt(closeBracket + 1) != '(') {
            return null;
        }
        int closeParen = findMatchingParen(text, closeBracket + 1);
        if (closeParen < 0) {
            errors.add(new ParseError(ParseError.Severity.WARNING,
                    new SourceLocation(line, colBase + openBracket, -1),
                    "unclosed link destination",
                    "treated as plain text"));
            return null;
        }
        String inner = text.substring(closeBracket + 2, closeParen).strip();
        String dest;
        String title = null;
        if (inner.startsWith("<")) {
            int gt = inner.indexOf('>');
            if (gt < 0) {
                return null;
            }
            dest = inner.substring(1, gt);
            String rest = inner.substring(gt + 1).strip();
            title = parseLinkTitle(rest);
        } else {
            int sp = firstWhitespace(inner);
            if (sp < 0) {
                dest = inner;
            } else {
                dest = inner.substring(0, sp);
                title = parseLinkTitle(inner.substring(sp).strip());
            }
        }
        String label = text.substring(openBracket + 1, closeBracket);
        SourceLocation loc = new SourceLocation(line, colBase + openBracket, -1);
        if (image) {
            budget.recordImage();
            return new InlineNodeResult(new Image(loc, dest, label, title), closeParen + 1);
        }
        Link link = new Link(loc, dest, title);
        parseInlines(label, line, colBase + openBracket + 1, link, budget, errors);
        return new InlineNodeResult(link, closeParen + 1);
    }

    private static String parseLinkTitle(String rest) {
        if (rest.length() >= 2) {
            char first = rest.charAt(0);
            char last = rest.charAt(rest.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return rest.substring(1, rest.length() - 1);
            }
        }
        return null;
    }

    private static int firstWhitespace(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.isWhitespace(s.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    private static int findMatchingBracket(String text, int open) {
        int depth = 0;
        for (int i = open; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\') {
                i++;
            } else if (c == '[') {
                depth++;
            } else if (c == ']') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static int findMatchingParen(String text, int open) {
        int depth = 0;
        boolean inQuote = false;
        char quote = 0;
        for (int i = open; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\') {
                i++;
                continue;
            }
            if (inQuote) {
                if (c == quote) {
                    inQuote = false;
                }
                continue;
            }
            if (c == '"' || c == '\'') {
                inQuote = true;
                quote = c;
            } else if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }
}
