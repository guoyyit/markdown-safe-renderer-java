package com.github.guoyyit.markdownsaferendererjava.toc;

import com.github.guoyyit.markdownsaferendererjava.ast.Document;
import com.github.guoyyit.markdownsaferendererjava.ast.Heading;
import com.github.guoyyit.markdownsaferendererjava.ast.Node;
import com.github.guoyyit.markdownsaferendererjava.ast.NodeType;
import com.github.guoyyit.markdownsaferendererjava.ast.Text;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Assigns stable hierarchical numbers (1, 1.1, 1.2, ...) and unique anchors to
 * headings, and builds the table of contents.
 *
 * <p>Missing levels are tolerated: each level keeps its own counter, so a jump
 * from H1 to H3 yields "1.0.1" — deterministic and stable across runs.
 * Duplicate titles get distinct anchors via the {@link AnchorRegistry}.
 */
public final class HeadingNumbering {

    /**
     * Mutates headings in the document with number+anchor and returns the TOC
     * root (a synthetic level-0 entry whose children are the top-level entries).
     */
    public TocEntry assign(Document document, AnchorRegistry anchors) {
        TocEntry root = new TocEntry("", "", "", 0);
        int[] counters = new int[7]; // 1..6
        Deque<TocEntry> stack = new ArrayDeque<>();
        stack.push(root);

        List<Heading> headings = new ArrayList<>();
        document.walk(node -> {
            if (node.type() == NodeType.HEADING) {
                headings.add((Heading) node);
            }
        });

        for (Heading heading : headings) {
            int level = heading.getLevel();
            counters[level]++;
            for (int l = level + 1; l <= 6; l++) {
                counters[l] = 0;
            }
            StringBuilder number = new StringBuilder();
            for (int l = 1; l <= level; l++) {
                if (number.length() > 0) {
                    number.append('.');
                }
                number.append(counters[l]);
            }
            String title = plainText(heading);
            String anchor = anchors.allocate(title);
            heading.setNumber(number.toString());
            heading.setAnchor(anchor);

            TocEntry entry = new TocEntry(number.toString(), title, anchor, level);
            while (stack.peek() != null && stack.peek().getLevel() >= level) {
                stack.pop();
            }
            stack.peek().addChild(entry);
            stack.push(entry);
        }
        return root;
    }

    /** Concatenates the literal text of a heading's inline children. */
    static String plainText(Node node) {
        StringBuilder sb = new StringBuilder();
        node.walk(n -> {
            if (n.type() == NodeType.TEXT) {
                sb.append(((Text) n).getContent());
            }
        });
        return sb.toString();
    }
}
