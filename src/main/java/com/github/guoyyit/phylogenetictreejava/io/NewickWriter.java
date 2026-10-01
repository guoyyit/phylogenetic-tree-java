package com.github.guoyyit.phylogenetictreejava.io;

import com.github.guoyyit.phylogenetictreejava.model.TreeNode;

import java.util.Locale;

/**
 * Writes trees to the Newick text format produced and consumed by this service.
 */
public final class NewickWriter {

    private NewickWriter() {
    }

    public static String write(TreeNode root) {
        StringBuilder out = new StringBuilder();
        appendNode(out, root, true);
        out.append(';');
        return out.toString();
    }

    private static void appendNode(StringBuilder out, TreeNode node, boolean isRoot) {
        if (!node.isLeaf()) {
            out.append('(');
            for (int i = 0; i < node.getChildren().size(); i++) {
                if (i > 0) {
                    out.append(',');
                }
                appendNode(out, node.getChildren().get(i), false);
            }
            out.append(')');
        }
        String label = node.getLabel();
        if (label != null && !label.isEmpty()) {
            out.append(quoteIfNeeded(label));
        }
        if (node.getSupport() != null) {
            out.append(String.format(Locale.ROOT, "[%s]", formatSupport(node.getSupport())));
        }
        if (!isRoot) {
            out.append(':').append(formatLength(node.getBranchLength()));
        }
    }

    private static String formatLength(double value) {
        if (value == 0.0) {
            return "0";
        }
        return String.format(Locale.ROOT, "%.8f", value);
    }

    private static String formatSupport(double value) {
        if (value == 0.0) {
            return "0";
        }
        if (value == 1.0) {
            return "1";
        }
        return String.format(Locale.ROOT, "%.4f", value);
    }

    private static String quoteIfNeeded(String label) {
        boolean needsQuotes = false;
        for (int i = 0; i < label.length(); i++) {
            char c = label.charAt(i);
            if (c == ' ' || c == '(' || c == ')' || c == ',' || c == ':' || c == ';'
                    || c == '[' || c == ']' || c == '\'' || c == '\t' || c == '\n') {
                needsQuotes = true;
                break;
            }
        }
        if (!needsQuotes) {
            return label;
        }
        return "'" + label.replace("'", "''") + "'";
    }
}
