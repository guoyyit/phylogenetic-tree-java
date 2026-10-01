package com.github.guoyyit.phylogenetictreejava.io;

import com.github.guoyyit.phylogenetictreejava.tree.PhyloTree;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Canonical Newick writer and reader.
 *
 * <p>The serialization is deterministic: parse(write(t)) equals t, and
 * re-serializing the parsed tree is byte-identical to the first output.</p>
 */
public final class NewickCodec {

    public NewickCodec() {
    }

    public String write(PhyloTree tree) {
        StringBuilder builder = new StringBuilder();
        writeNode(tree.getRoot(), builder);
        builder.append(';');
        return builder.toString();
    }

    public String writeAll(List<PhyloTree> trees) {
        StringBuilder builder = new StringBuilder();
        for (PhyloTree tree : trees) {
            writeNode(tree.getRoot(), builder);
            builder.append(";\n");
        }
        return builder.toString();
    }

    public PhyloTree parse(String text) {
        List<PhyloTree> trees = parseAll(text);
        if (trees.size() != 1) {
            throw new NewickParseException(
                    "expected exactly one tree but found " + trees.size());
        }
        return trees.get(0);
    }

    public List<PhyloTree> parseAll(String text) {
        List<PhyloTree> trees = new ArrayList<>();
        Parser parser = new Parser(text);
        while (parser.skipUntilTree()) {
            PhyloTree.Node root = parser.parseNode();
            parser.expect(';');
            trees.add(new PhyloTree(root));
        }
        return trees;
    }

    private static void writeNode(PhyloTree.Node node, StringBuilder builder) {
        List<PhyloTree.Node> children = node.getChildren();
        if (!children.isEmpty()) {
            builder.append('(');
            for (int i = 0; i < children.size(); i++) {
                if (i > 0) {
                    builder.append(',');
                }
                writeNode(children.get(i), builder);
            }
            builder.append(')');
        }
        if (node.getLabel() != null) {
            builder.append(node.getLabel());
        }
        if (node.getLength() != 0.0) {
            builder.append(':').append(formatLength(node.getLength()));
        }
    }

    static String formatLength(double length) {
        String formatted = String.format(Locale.ROOT, "%.10f", length);
        while (formatted.endsWith("0")) {
            formatted = formatted.substring(0, formatted.length() - 1);
        }
        if (formatted.endsWith(".")) {
            formatted = formatted.substring(0, formatted.length() - 1);
        }
        if (formatted.isEmpty() || formatted.equals("-0")) {
            formatted = "0";
        }
        return formatted;
    }

    private static final class Parser {
        private final String text;
        private int position;

        private Parser(String text) {
            this.text = text == null ? "" : text;
        }

        private boolean skipUntilTree() {
            while (position < text.length()) {
                char c = text.charAt(position);
                if (Character.isWhitespace(c)) {
                    position++;
                } else if (c == '(' || isLabelStart(c)) {
                    return true;
                } else {
                    throw new NewickParseException(
                            "unexpected character '" + c + "' at position " + position);
                }
            }
            return false;
        }

        private PhyloTree.Node parseNode() {
            skipWhitespace();
            PhyloTree.Node node = new PhyloTree.Node();
            char current = currentChar();
            if (current == '(') {
                position++;
                while (true) {
                    node.getChildren().add(parseNode());
                    skipWhitespace();
                    char separator = currentChar();
                    if (separator == ',') {
                        position++;
                        continue;
                    }
                    if (separator == ')') {
                        position++;
                        break;
                    }
                    throw error("expected ',' or ')'");
                }
            }
            String label = parseLabel();
            node.setLabel(label.isEmpty() ? null : label);
            skipWhitespace();
            if (position < text.length() && text.charAt(position) == ':') {
                position++;
                node.setLength(parseNumber());
            }
            return node;
        }

        private String parseLabel() {
            skipWhitespace();
            StringBuilder label = new StringBuilder();
            if (position < text.length() && text.charAt(position) == '\'') {
                position++;
                while (position < text.length()) {
                    char c = text.charAt(position++);
                    if (c == '\'') {
                        if (position < text.length() && text.charAt(position) == '\'') {
                            label.append('\'');
                            position++;
                        } else {
                            break;
                        }
                    } else {
                        label.append(c);
                    }
                }
            } else {
                while (position < text.length() && isLabelChar(text.charAt(position))) {
                    label.append(text.charAt(position++));
                }
            }
            return label.toString();
        }

        private double parseNumber() {
            skipWhitespace();
            int start = position;
            if (position < text.length() && (text.charAt(position) == '-'
                    || text.charAt(position) == '+')) {
                position++;
            }
            while (position < text.length()) {
                char c = text.charAt(position);
                if (Character.isDigit(c) || c == '.' || c == 'e' || c == 'E'
                        || c == '-' || c == '+') {
                    position++;
                } else {
                    break;
                }
            }
            if (start == position) {
                throw error("expected branch length");
            }
            try {
                return Double.parseDouble(text.substring(start, position));
            } catch (NumberFormatException e) {
                throw new NewickParseException(
                        "invalid branch length at position " + start);
            }
        }

        private void expect(char expected) {
            skipWhitespace();
            if (position >= text.length() || text.charAt(position) != expected) {
                throw error("expected '" + expected + "'");
            }
            position++;
        }

        private void skipWhitespace() {
            while (position < text.length()
                    && Character.isWhitespace(text.charAt(position))) {
                position++;
            }
        }

        private char currentChar() {
            if (position >= text.length()) {
                throw error("unexpected end of input");
            }
            return text.charAt(position);
        }

        private NewickParseException error(String message) {
            return new NewickParseException(message + " at position " + position);
        }

        private static boolean isLabelStart(char c) {
            return isLabelChar(c) || c == '\'';
        }

        private static boolean isLabelChar(char c) {
            return !Character.isWhitespace(c)
                    && c != '(' && c != ')' && c != ',' && c != ':' && c != ';';
        }
    }
}
