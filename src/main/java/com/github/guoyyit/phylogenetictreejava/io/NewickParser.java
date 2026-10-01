package com.github.guoyyit.phylogenetictreejava.io;

import com.github.guoyyit.phylogenetictreejava.model.TreeNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses the Newick text format written by {@link NewickWriter}.
 */
public final class NewickParser {

    private NewickParser() {
    }

    public static TreeNode parse(String newick) {
        if (newick == null || newick.isBlank()) {
            throw new IllegalArgumentException("empty Newick input");
        }
        Cursor cursor = new Cursor(newick);
        TreeNode root = parseNode(cursor);
        cursor.skipSpaces();
        if (cursor.peek() != ';') {
            throw new IllegalArgumentException("expected ';' at position " + cursor.pos);
        }
        cursor.next();
        cursor.skipWhitespace();
        if (cursor.pos < cursor.text.length()) {
            throw new IllegalArgumentException("trailing data after ';'");
        }
        return root;
    }

    /**
     * Parses the multi-tree format written for {@code bootstrap-trees.nwk}:
     * one {@code ;}-terminated Newick tree per line (blank lines ignored).
     */
    public static List<TreeNode> parseMany(String text) {
        List<TreeNode> trees = new ArrayList<>();
        if (text == null) {
            return trees;
        }
        for (String line : text.split("\\R")) {
            if (!line.isBlank()) {
                trees.add(parse(line.trim()));
            }
        }
        return trees;
    }

    private static TreeNode parseNode(Cursor cursor) {
        cursor.skipSpaces();
        TreeNode node = new TreeNode();
        if (cursor.peek() == '(') {
            cursor.next();
            node.addChild(parseNode(cursor));
            cursor.skipSpaces();
            while (cursor.peek() == ',') {
                cursor.next();
                node.addChild(parseNode(cursor));
                cursor.skipSpaces();
            }
            if (cursor.peek() != ')') {
                throw new IllegalArgumentException("expected ')' at position " + cursor.pos);
            }
            cursor.next();
        }
        cursor.skipSpaces();
        node.setLabel(readLabel(cursor));
        cursor.skipSpaces();
        if (cursor.peek() == '[') {
            node.setSupport(Double.parseDouble(readComment(cursor)));
        }
        cursor.skipSpaces();
        if (cursor.peek() == ':') {
            cursor.next();
            cursor.skipSpaces();
            node.setBranchLength(Double.parseDouble(readNumber(cursor)));
        }
        return node;
    }

    private static String readLabel(Cursor cursor) {
        if (cursor.peek() == '\'') {
            cursor.next();
            StringBuilder label = new StringBuilder();
            while (true) {
                char ch = cursor.next();
                if (ch == '\'') {
                    if (cursor.peek() == '\'') {
                        label.append('\'');
                        cursor.next();
                    } else {
                        break;
                    }
                } else {
                    label.append(ch);
                }
            }
            return label.toString();
        }
        StringBuilder label = new StringBuilder();
        while (true) {
            char ch = cursor.peek();
            if (ch == 0 || ch == '(' || ch == ')' || ch == ',' || ch == ':'
                    || ch == ';' || ch == '[' || ch == ']' || ch == ' ') {
                break;
            }
            label.append(ch);
            cursor.next();
        }
        return label.toString();
    }

    private static String readComment(Cursor cursor) {
        cursor.next();
        StringBuilder body = new StringBuilder();
        while (cursor.peek() != ']') {
            if (cursor.peek() == 0) {
                throw new IllegalArgumentException("unterminated comment");
            }
            body.append(cursor.next());
        }
        cursor.next();
        return body.toString();
    }

    private static String readNumber(Cursor cursor) {
        StringBuilder number = new StringBuilder();
        while (true) {
            char ch = cursor.peek();
            if (ch == 0 || ch == ',' || ch == ')' || ch == ';' || ch == ' ') {
                break;
            }
            number.append(ch);
            cursor.next();
        }
        return number.toString();
    }

    private static final class Cursor {
        private final String text;
        private int pos;

        private Cursor(String text) {
            this.text = text;
        }

        private char peek() {
            return pos < text.length() ? text.charAt(pos) : 0;
        }

        private char next() {
            char c = peek();
            pos++;
            return c;
        }

        private void skipSpaces() {
            while (pos < text.length() && (text.charAt(pos) == ' ' || text.charAt(pos) == '\t')) {
                pos++;
            }
        }

        private void skipWhitespace() {
            while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
                pos++;
            }
        }
    }
}
