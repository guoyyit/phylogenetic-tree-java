package com.github.guoyyit.phylogenetictreejava.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Mutable rooted phylogenetic tree node serialized in Newick format.
 * Internal nodes may carry a bootstrap support value.
 */
public final class TreeNode {

    private String label = "";
    private double branchLength = 0.0;
    private Double support;
    private final List<TreeNode> children = new ArrayList<>();

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public double getBranchLength() {
        return branchLength;
    }

    public void setBranchLength(double branchLength) {
        this.branchLength = branchLength;
    }

    public Double getSupport() {
        return support;
    }

    public void setSupport(Double support) {
        this.support = support;
    }

    public List<TreeNode> getChildren() {
        return children;
    }

    public void addChild(TreeNode child) {
        children.add(child);
    }

    public boolean isLeaf() {
        return children.isEmpty();
    }
}
