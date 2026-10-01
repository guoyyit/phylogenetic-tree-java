package com.github.guoyyit.phylogenetictreejava.tree;

import java.util.ArrayList;
import java.util.List;

/**
 * A rooted representation of a binary phylogenetic tree. Unrooted NJ trees
 * are rooted deterministically at the last-joined internal node.
 */
public final class PhyloTree {

    /** A node: leaves carry a non-null label; internal nodes may carry a
     *  support-value label (for example {@code 0.97}). */
    public static final class Node {
        private String label;
        private double length;
        private final List<Node> children = new ArrayList<>();

        public Node() {
        }

        public Node(String label, double length) {
            this.label = label;
            this.length = length;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public double getLength() {
            return length;
        }

        public void setLength(double length) {
            this.length = length;
        }

        public List<Node> getChildren() {
            return children;
        }

        public boolean isLeaf() {
            return children.isEmpty();
        }

        public Node deepCopy() {
            Node copy = new Node(label, length);
            for (Node child : children) {
                copy.children.add(child.deepCopy());
            }
            return copy;
        }
    }

    private final Node root;

    public PhyloTree(Node root) {
        this.root = root;
    }

    public Node getRoot() {
        return root;
    }

    public PhyloTree deepCopy() {
        return new PhyloTree(root.deepCopy());
    }
}
