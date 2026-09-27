package com.github.guoyyit.markdownsaferendererjava.parser;

import com.github.guoyyit.markdownsaferendererjava.ast.Node;
import com.github.guoyyit.markdownsaferendererjava.ast.NodeType;
import com.github.guoyyit.markdownsaferendererjava.ast.Nodes;
import com.github.guoyyit.markdownsaferendererjava.ast.SourcePos;
import com.github.guoyyit.markdownsaferendererjava.limits.LimitExceededException;
import com.github.guoyyit.markdownsaferendererjava.limits.LimitReason;
import com.github.guoyyit.markdownsaferendererjava.limits.ResourceLimits;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Line-oriented markdown parser with per-construct error recovery.
 * <p>
 * Guarantees:
 * <ul>
 *   <li>Never throws on malformed input; problems are reported as {@link ParseError}
 *       and the offending construct is skipped or degraded.</li>
 *   <li>Resource limits (input size, nesting depth, table size) abort the whole
 *       request via {@link LimitExceededException} — no partial result is returned.</li>
 *   <li>Cooperatively cancellable via thread interrupt (used for timeouts).</li>
 * </ul>
 */
public class MarkdownParser {

    private static final Pattern FENCE = Pattern.compile("^ {0,3}(```+|~~~+)\\s*([^`]*)$");
    private static final Pattern ATX = Pattern.compile("^ {0,3}(#{1,6})[ \t]+(.*)$");
    private static final Pattern ATX_CLOSED = Pattern.compile("^(.*?)[ \t]+#+[ \t]*$");
    private static final Pattern HR = Pattern.compile("^ {0,3}((\\*[ \t]*){3,}|(-[ \t]*){3,}|(_[ \t]*){3,})$");
    private static final Pattern BULLET = Pattern.compile("^( *)([-+*])[ \t]+(.*)$");
    private static final Pattern ORDERED = Pattern.compile("^( *)(\\d{1,9})([.)])[ \t]+(.*)$");
    private static final Pattern QUOTE = Pattern.compile("^ {0,3}>[ \t]?(.*)$");
    private static final Pattern TABLE_DELIM = Pattern.compile("^\\|?[ \t]*:?-+:?[ \t]*(\\|[ \t]*:?-+:?[ \t]*)*\\|?[ \t]*$");
    private static final Pattern INDENTED_CODE = Pattern.compile("^(    |\t)(.*)$");
    private static final Pattern AUTOLINK = Pattern.compile("^<((?:https?|HTTPS?)://[^\\s<>]+)>");
    private static final Pattern RAW_TAG = Pattern.compile("^</?[A-Za-z][^<>]*>?");

    private final ResourceLimits limits;

    public MarkdownParser(ResourceLimits limits) {
        this.limits = limits;
    }

    public ParseResult parse(String input) {
        String text = input == null ? "" : input;
        if (text.length() > limits.maxInputChars()) {
            throw new LimitExceededException(LimitReason.INPUT_TOO_LONG,
                    "input length " + text.length() + " exceeds limit " + limits.maxInputChars());
        }
        Ctx ctx = new Ctx();
        String[] lines = text.split("\n", -1);
        Nodes.MutableNode doc = Nodes.node(NodeType.DOCUMENT, SourcePos.of(1, 1));
        parseBlocks(lines, 0, lines.length, 1, doc, ctx);
        return new ParseResult(doc.freeze(), List.copyOf(ctx.errors));
    }

    // ---------------------------------------------------------------- blocks

    private static final class Ctx {
        final List<ParseError> errors = new ArrayList<>();
    }

    private void checkTimeout() {
        if (Thread.currentThread().isInterrupted()) {
            throw new LimitExceededException(LimitReason.PROCESSING_TIMEOUT, "processing aborted (timeout)");
        }
    }

    private void parseBlocks(String[] lines, int start, int end, int depth,
                             Nodes.MutableNode parent, Ctx ctx) {
        if (depth > limits.maxDepth()) {
            throw new LimitExceededException(LimitReason.NESTING_TOO_DEEP,
                    "nesting depth exceeds limit " + limits.maxDepth());
        }
        int i = start;
        while (i < end) {
            checkTimeout();
            String line = lines[i];
            if (line.isBlank()) {
                i++;
                continue;
            }
            Matcher fence = FENCE.matcher(line);
            if (fence.matches()) {
                i = parseFencedCode(lines, i, end, fence, parent, ctx);
                continue;
            }
            Matcher atx = ATX.matcher(line);
            if (atx.matches()) {
                parseHeading(atx, i + 1, parent, ctx);
                i++;
                continue;
            }
            if (HR.matcher(line).matches()) {
                parent.addChild(Nodes.node(NodeType.THEMATIC_BREAK, SourcePos.of(i + 1, 1)));
                i++;
                continue;
            }
            Matcher quote = QUOTE.matcher(line);
            if (quote.matches()) {
                i = parseQuote(lines, i, end, depth, parent, ctx);
                continue;
            }
            if (BULLET.matcher(line).matches() || ORDERED.matcher(line).matches()) {
                i = parseList(lines, i, end, depth, parent, ctx);
                continue;
            }
            if (i + 1 < end && isTableStart(lines[i], lines[i + 1])) {
                i = parseTable(lines, i, end, parent, ctx);
                continue;
            }
            Matcher indented = INDENTED_CODE.matcher(line);
            if (indented.matches()) {
                i = parseIndentedCode(lines, i, end, parent);
                continue;
            }
            i = parseParagraph(lines, i, end, parent, ctx);
        }
    }

    private int parseFencedCode(String[] lines, int i, int end, Matcher fence,
                                Nodes.MutableNode parent, Ctx ctx) {
        String marker = fence.group(1);
        char fenceChar = marker.charAt(0);
        int fenceLen = marker.length();
        String info = fence.group(2) == null ? "" : fence.group(2).trim();
        int startLine = i + 1;
        StringBuilder code = new StringBuilder();
        int j = i + 1;
        boolean closed = false;
        while (j < end) {
            checkTimeout();
            String l = lines[j];
            String trimmed = l.strip();
            if (!trimmed.isEmpty() && trimmed.charAt(0) == fenceChar) {
                int run = 0;
                while (run < trimmed.length() && trimmed.charAt(run) == fenceChar) {
                    run++;
                }
                if (run >= fenceLen && trimmed.substring(run).isBlank()) {
                    closed = true;
                    j++;
                    break;
                }
            }
            code.append(l).append('\n');
            j++;
        }
        if (!closed) {
            ctx.errors.add(new ParseError(SourcePos.of(startLine, 1),
                    "unclosed fenced code block; consumed until end of input",
                    marker));
        }
        Nodes.MutableNode node = Nodes.node(NodeType.CODE_BLOCK, SourcePos.of(startLine, 1));
        node.setLiteral(code.toString());
        if (!info.isEmpty()) {
            node.putAttribute("info", info);
        }
        parent.addChild(node);
        return j;
    }

    private void parseHeading(Matcher atx, int lineNo, Nodes.MutableNode parent, Ctx ctx) {
        int level = atx.group(1).length();
        String content = atx.group(2).trim();
        Matcher closed = ATX_CLOSED.matcher(content);
        if (closed.matches()) {
            content = closed.group(1).trim();
        }
        Nodes.MutableNode heading = Nodes.node(NodeType.HEADING, SourcePos.of(lineNo, 1));
        heading.putAttribute("level", String.valueOf(level));
        parseInlines(content, lineNo, heading, ctx);
        parent.addChild(heading);
    }

    private int parseQuote(String[] lines, int i, int end, int depth,
                           Nodes.MutableNode parent, Ctx ctx) {
        int startLine = i + 1;
        List<String> inner = new ArrayList<>();
        int j = i;
        while (j < end) {
            Matcher m = QUOTE.matcher(lines[j]);
            if (m.matches()) {
                inner.add(m.group(1));
                j++;
            } else if (lines[j].isBlank()) {
                break;
            } else {
                break;
            }
        }
        Nodes.MutableNode quote = Nodes.node(NodeType.BLOCK_QUOTE, SourcePos.of(startLine, 1));
        String[] innerLines = inner.toArray(new String[0]);
        parseBlocks(innerLines, 0, innerLines.length, depth + 1, quote, ctx);
        parent.addChild(quote);
        return j;
    }

    private int parseList(String[] lines, int i, int end, int depth,
                          Nodes.MutableNode parent, Ctx ctx) {
        int startLine = i + 1;
        // Gather the raw item lines belonging to this list block.
        List<String> gathered = new ArrayList<>();
        int j = i;
        while (j < end) {
            String l = lines[j];
            if (l.isBlank()) {
                // A blank line ends the list unless the next non-blank line is
                // an indented continuation (loose list); keep it simple: stop.
                break;
            }
            if (BULLET.matcher(l).matches() || ORDERED.matcher(l).matches()) {
                gathered.add(l);
                j++;
                continue;
            }
            // Continuation lines must be indented relative to the list start.
            if (l.startsWith(" ") || l.startsWith("\t")) {
                gathered.add(l);
                j++;
                continue;
            }
            break;
        }
        if (gathered.isEmpty()) {
            // Should not happen (first line matched), but degrade gracefully.
            ctx.errors.add(new ParseError(SourcePos.of(startLine, 1),
                    "malformed list; line skipped", lines[i]));
            return i + 1;
        }
        buildListTree(gathered, startLine, depth, parent, ctx);
        return j;
    }

    private void buildListTree(List<String> gathered, int startLine, int depth,
                               Nodes.MutableNode parent, Ctx ctx) {
        if (depth > limits.maxDepth()) {
            throw new LimitExceededException(LimitReason.NESTING_TOO_DEEP,
                    "list nesting exceeds limit " + limits.maxDepth());
        }
        // Stack entries: [indent, listNode, lastItemNode]
        List<int[]> indentStack = new ArrayList<>();
        List<Nodes.MutableNode> listStack = new ArrayList<>();
        List<Nodes.MutableNode> itemStack = new ArrayList<>();

        Nodes.MutableNode currentItem = null;
        List<String> itemContent = new ArrayList<>();
        int itemLine = startLine;

        for (int k = 0; k < gathered.size(); k++) {
            String raw = gathered.get(k);
            Matcher bullet = BULLET.matcher(raw);
            Matcher ordered = ORDERED.matcher(raw);
            if (bullet.matches() || ordered.matches()) {
                // flush previous item
                if (currentItem != null) {
                    flushItemContent(itemContent, itemLine, depth, currentItem, ctx);
                }
                int indent = raw.length() - raw.stripLeading().length();
                boolean isOrdered = ordered.matches();
                String content = isOrdered ? ordered.group(4) : bullet.group(3);
                String startNum = isOrdered ? ordered.group(2) : null;

                // find or create the list node for this indent level
                while (!indentStack.isEmpty() && indent < indentStack.get(indentStack.size() - 1)[0]) {
                    indentStack.remove(indentStack.size() - 1);
                    listStack.remove(listStack.size() - 1);
                    itemStack.remove(itemStack.size() - 1);
                }
                Nodes.MutableNode listNode;
                if (!indentStack.isEmpty() && indent == indentStack.get(indentStack.size() - 1)[0]) {
                    listNode = listStack.get(listStack.size() - 1);
                } else {
                    NodeType listType = isOrdered ? NodeType.ORDERED_LIST : NodeType.UNORDERED_LIST;
                    listNode = Nodes.node(listType, SourcePos.of(startLine + k, indent + 1));
                    if (isOrdered && startNum != null && !"1".equals(startNum)) {
                        listNode.putAttribute("start", startNum);
                    }
                    if (listStack.isEmpty()) {
                        parent.addChild(listNode);
                    } else {
                        Nodes.MutableNode parentItem = itemStack.get(itemStack.size() - 1);
                        parentItem.addChild(listNode);
                    }
                    indentStack.add(new int[]{indent});
                    listStack.add(listNode);
                    itemStack.add(null);
                }
                Nodes.MutableNode item = Nodes.node(NodeType.LIST_ITEM, SourcePos.of(startLine + k, indent + 1));
                listNode.addChild(item);
                itemStack.set(itemStack.size() - 1, item);
                currentItem = item;
                itemContent = new ArrayList<>();
                itemContent.add(content);
                itemLine = startLine + k;
            } else {
                // continuation line of the current item
                if (currentItem == null) {
                    ctx.errors.add(new ParseError(SourcePos.of(startLine + k, 1),
                            "list continuation without item; line skipped", raw));
                    continue;
                }
                itemContent.add(raw.strip());
            }
        }
        if (currentItem != null) {
            flushItemContent(itemContent, itemLine, depth, currentItem, ctx);
        }
    }

    private void flushItemContent(List<String> content, int lineNo, int depth,
                                  Nodes.MutableNode item, Ctx ctx) {
        if (content.isEmpty()) {
            return;
        }
        String[] arr = content.toArray(new String[0]);
        parseBlocks(arr, 0, arr.length, depth + 1, item, ctx);
    }

    private boolean isTableStart(String headerLine, String delimLine) {
        return headerLine.contains("|") && TABLE_DELIM.matcher(delimLine).matches();
    }

    private int parseTable(String[] lines, int i, int end, Nodes.MutableNode parent, Ctx ctx) {
        int startLine = i + 1;
        String[] headerCells = splitRow(lines[i]);
        String[] alignRow = splitRow(lines[i + 1]);
        if (headerCells.length == 0 || headerCells.length != alignRow.length) {
            ctx.errors.add(new ParseError(SourcePos.of(startLine, 1),
                    "invalid table: header/delimiter column mismatch; treated as paragraph",
                    lines[i]));
            // degrade: header + delimiter become a paragraph
            Nodes.MutableNode para = Nodes.node(NodeType.PARAGRAPH, SourcePos.of(startLine, 1));
            parseInlines(lines[i] + "\n" + lines[i + 1], startLine, para, ctx);
            parent.addChild(para);
            return i + 2;
        }
        if (headerCells.length > limits.maxTableCols()) {
            throw new LimitExceededException(LimitReason.TABLE_TOO_LARGE,
                    "table has " + headerCells.length + " columns, limit is " + limits.maxTableCols());
        }
        Nodes.MutableNode table = Nodes.node(NodeType.TABLE, SourcePos.of(startLine, 1));
        Nodes.MutableNode headerRow = Nodes.node(NodeType.TABLE_ROW, SourcePos.of(startLine, 1));
        headerRow.putAttribute("header", "true");
        for (int c = 0; c < headerCells.length; c++) {
            headerRow.addChild(makeCell(headerCells[c], alignRow[c], startLine, ctx));
        }
        table.addChild(headerRow);

        int j = i + 2;
        int rowCount = 0;
        while (j < end && !lines[j].isBlank() && lines[j].contains("|")) {
            checkTimeout();
            String[] cells = splitRow(lines[j]);
            if (cells.length != headerCells.length) {
                ctx.errors.add(new ParseError(SourcePos.of(j + 1, 1),
                        "table row has " + cells.length + " cells, expected "
                                + headerCells.length + "; row skipped", lines[j]));
                j++;
                continue;
            }
            rowCount++;
            if (rowCount > limits.maxTableRows()) {
                throw new LimitExceededException(LimitReason.TABLE_TOO_LARGE,
                        "table exceeds " + limits.maxTableRows() + " rows");
            }
            Nodes.MutableNode row = Nodes.node(NodeType.TABLE_ROW, SourcePos.of(j + 1, 1));
            for (int c = 0; c < cells.length; c++) {
                row.addChild(makeCell(cells[c], alignRow[c], j + 1, ctx));
            }
            table.addChild(row);
            j++;
        }
        parent.addChild(table);
        return j;
    }

    private Nodes.MutableNode makeCell(String content, String alignSpec, int lineNo, Ctx ctx) {
        Nodes.MutableNode cell = Nodes.node(NodeType.TABLE_CELL, SourcePos.of(lineNo, 1));
        String align = alignOf(alignSpec);
        if (align != null) {
            cell.putAttribute("align", align);
        }
        parseInlines(content.trim(), lineNo, cell, ctx);
        return cell;
    }

    private static String alignOf(String spec) {
        String s = spec.trim();
        boolean left = s.startsWith(":");
        boolean right = s.endsWith(":");
        if (left && right) {
            return "center";
        }
        if (right) {
            return "right";
        }
        if (left) {
            return "left";
        }
        return null;
    }

    private static String[] splitRow(String line) {
        String s = line.trim();
        if (s.startsWith("|")) {
            s = s.substring(1);
        }
        if (s.endsWith("|")) {
            s = s.substring(0, s.length() - 1);
        }
        List<String> cells = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean escaped = false;
        for (int k = 0; k < s.length(); k++) {
            char ch = s.charAt(k);
            if (escaped) {
                if (ch == '|') {
                    cur.append('|');
                } else {
                    cur.append('\\').append(ch);
                }
                escaped = false;
            } else if (ch == '\\') {
                escaped = true;
            } else if (ch == '|') {
                cells.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(ch);
            }
        }
        if (escaped) {
            cur.append('\\');
        }
        cells.add(cur.toString());
        return cells.toArray(new String[0]);
    }

    private int parseIndentedCode(String[] lines, int i, int end, Nodes.MutableNode parent) {
        int startLine = i + 1;
        StringBuilder code = new StringBuilder();
        int j = i;
        while (j < end) {
            Matcher m = INDENTED_CODE.matcher(lines[j]);
            if (m.matches()) {
                code.append(m.group(2)).append('\n');
                j++;
            } else if (lines[j].isBlank()) {
                code.append('\n');
                j++;
            } else {
                break;
            }
        }
        Nodes.MutableNode node = Nodes.node(NodeType.CODE_BLOCK, SourcePos.of(startLine, 1));
        node.setLiteral(code.toString());
        parent.addChild(node);
        return j;
    }

    private int parseParagraph(String[] lines, int i, int end, Nodes.MutableNode parent, Ctx ctx) {
        int startLine = i + 1;
        StringBuilder text = new StringBuilder();
        int j = i;
        while (j < end) {
            String l = lines[j];
            if (l.isBlank()) {
                break;
            }
            if (j > i && startsBlock(lines, j, end)) {
                break;
            }
            if (j > i) {
                text.append('\n');
            }
            text.append(l);
            j++;
        }
        Nodes.MutableNode para = Nodes.node(NodeType.PARAGRAPH, SourcePos.of(startLine, 1));
        parseInlines(text.toString(), startLine, para, ctx);
        parent.addChild(para);
        return j;
    }

    private boolean startsBlock(String[] lines, int j, int end) {
        String l = lines[j];
        return FENCE.matcher(l).matches()
                || ATX.matcher(l).matches()
                || HR.matcher(l).matches()
                || QUOTE.matcher(l).matches()
                || BULLET.matcher(l).matches()
                || ORDERED.matcher(l).matches()
                || (j + 1 < end && isTableStart(l, lines[j + 1]));
    }

    // ---------------------------------------------------------------- inlines

    private void parseInlines(String text, int lineNo, Nodes.MutableNode parent, Ctx ctx) {
        int i = 0;
        int n = text.length();
        StringBuilder buf = new StringBuilder();
        while (i < n) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < n && isEscapable(text.charAt(i + 1))) {
                buf.append(text.charAt(i + 1));
                i += 2;
                continue;
            }
            if (c == '`') {
                int close = text.indexOf('`', i + 1);
                if (close < 0) {
                    ctx.errors.add(new ParseError(SourcePos.of(lineNo, i + 1),
                            "unclosed code span; treated as literal text", "`"));
                    buf.append('`');
                    i++;
                } else {
                    flushText(buf, lineNo, parent);
                    Nodes.MutableNode span = Nodes.node(NodeType.CODE_SPAN, SourcePos.of(lineNo, i + 1));
                    span.setLiteral(text.substring(i + 1, close));
                    parent.addChild(span);
                    i = close + 1;
                }
                continue;
            }
            if (c == '!' && i + 1 < n && text.charAt(i + 1) == '[') {
                int[] parsed = parseLinkLike(text, i + 1);
                if (parsed != null) {
                    flushText(buf, lineNo, parent);
                    String label = text.substring(i + 2, parsed[0]);
                    String[] dt = splitDestTitle(text.substring(parsed[0] + 2, parsed[1]));
                    Nodes.MutableNode img = Nodes.node(NodeType.IMAGE, SourcePos.of(lineNo, i + 1));
                    img.putAttribute("src", dt[0]);
                    img.putAttribute("alt", label);
                    if (dt[1] != null) {
                        img.putAttribute("title", dt[1]);
                    }
                    parent.addChild(img);
                    i = parsed[1] + 1;
                    continue;
                }
                buf.append(c);
                i++;
                continue;
            }
            if (c == '[') {
                int[] parsed = parseLinkLike(text, i);
                if (parsed != null) {
                    flushText(buf, lineNo, parent);
                    String label = text.substring(i + 1, parsed[0]);
                    String[] dt = splitDestTitle(text.substring(parsed[0] + 2, parsed[1]));
                    Nodes.MutableNode link = Nodes.node(NodeType.LINK, SourcePos.of(lineNo, i + 1));
                    link.putAttribute("href", dt[0]);
                    if (dt[1] != null) {
                        link.putAttribute("title", dt[1]);
                    }
                    parseInlines(label, lineNo, link, ctx);
                    parent.addChild(link);
                    i = parsed[1] + 1;
                    continue;
                }
                buf.append(c);
                i++;
                continue;
            }
            if (c == '<') {
                String rest = text.substring(i);
                Matcher auto = AUTOLINK.matcher(rest);
                if (auto.find()) {
                    flushText(buf, lineNo, parent);
                    String url = auto.group(1);
                    Nodes.MutableNode link = Nodes.node(NodeType.LINK, SourcePos.of(lineNo, i + 1));
                    link.putAttribute("href", url);
                    link.addChild(Nodes.text(url, SourcePos.of(lineNo, i + 1)));
                    parent.addChild(link);
                    i += auto.end();
                    continue;
                }
                Matcher tag = RAW_TAG.matcher(rest);
                if (tag.find() && tag.group().endsWith(">")) {
                    flushText(buf, lineNo, parent);
                    Nodes.MutableNode raw = Nodes.node(NodeType.RAW_HTML, SourcePos.of(lineNo, i + 1));
                    raw.setLiteral(tag.group());
                    parent.addChild(raw);
                    i += tag.end();
                    continue;
                }
                if (rest.startsWith("<!--")) {
                    int close = text.indexOf("-->", i + 4);
                    if (close >= 0) {
                        flushText(buf, lineNo, parent);
                        Nodes.MutableNode raw = Nodes.node(NodeType.RAW_HTML, SourcePos.of(lineNo, i + 1));
                        raw.setLiteral(text.substring(i, close + 3));
                        parent.addChild(raw);
                        i = close + 3;
                        continue;
                    }
                }
                buf.append(c);
                i++;
                continue;
            }
            buf.append(c);
            i++;
        }
        flushText(buf, lineNo, parent);
    }

    /**
     * Parses "[label](dest)" starting at the '['. Returns
     * {indexOfCloseBracket, indexOfCloseParen} or null if malformed.
     */
    private int[] parseLinkLike(String text, int openBracket) {
        int closeBracket = -1;
        int depth = 0;
        for (int k = openBracket; k < text.length(); k++) {
            char ch = text.charAt(k);
            if (ch == '\\') {
                k++;
                continue;
            }
            if (ch == '[') {
                depth++;
            } else if (ch == ']') {
                depth--;
                if (depth == 0) {
                    closeBracket = k;
                    break;
                }
            }
        }
        if (closeBracket < 0 || closeBracket + 1 >= text.length()
                || text.charAt(closeBracket + 1) != '(') {
            return null;
        }
        int closeParen = -1;
        int parenDepth = 0;
        for (int k = closeBracket + 1; k < text.length(); k++) {
            char ch = text.charAt(k);
            if (ch == '\\') {
                k++;
                continue;
            }
            if (ch == '(') {
                parenDepth++;
            } else if (ch == ')') {
                parenDepth--;
                if (parenDepth == 0) {
                    closeParen = k;
                    break;
                }
            } else if (ch == '\n') {
                return null; // destinations may not span lines
            }
        }
        if (closeParen < 0) {
            return null;
        }
        return new int[]{closeBracket, closeParen};
    }

    /**
     * Splits "destination [title]" inside link parentheses.
     * Returns {href, titleOrNull}.
     */
    private static String[] splitDestTitle(String raw) {
        String s = raw.trim();
        String href;
        String rest;
        if (s.startsWith("<")) {
            int gt = s.indexOf('>');
            if (gt < 0) {
                return new String[]{s, null};
            }
            href = s.substring(1, gt);
            rest = s.substring(gt + 1).trim();
        } else {
            int sp = -1;
            for (int k = 0; k < s.length(); k++) {
                if (Character.isWhitespace(s.charAt(k))) {
                    sp = k;
                    break;
                }
            }
            if (sp < 0) {
                return new String[]{s, null};
            }
            href = s.substring(0, sp);
            rest = s.substring(sp).trim();
        }
        if (rest.isEmpty()) {
            return new String[]{href, null};
        }
        char q = rest.charAt(0);
        if (rest.length() >= 2 && (q == '"' || q == '\'' || q == '(')
                && rest.endsWith(q == '(' ? ")" : String.valueOf(q))) {
            return new String[]{href, rest.substring(1, rest.length() - 1)};
        }
        // not a valid title: treat the whole thing as the destination
        return new String[]{s, null};
    }

    private void flushText(StringBuilder buf, int lineNo, Nodes.MutableNode parent) {
        if (buf.length() > 0) {
            parent.addChild(Nodes.text(buf.toString(), SourcePos.of(lineNo, 1)));
            buf.setLength(0);
        }
    }

    private static boolean isEscapable(char c) {
        return "\\`*_{}[]()#+-.!|<>".indexOf(c) >= 0;
    }
}
