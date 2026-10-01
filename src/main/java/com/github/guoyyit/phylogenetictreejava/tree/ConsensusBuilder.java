package com.github.guoyyit.phylogenetictreejava.tree;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Builds a labeled majority-rule consensus tree from bootstrap trees.
 */
public final class ConsensusBuilder {

    public ConsensusBuilder() {
    }

    public PhyloTree build(List<PhyloTree> bootstrapTrees) {
        if (bootstrapTrees.isEmpty()) {
            throw new IllegalArgumentException("at least one bootstrap tree is required");
        }

        List<String> leaves = collectLeaves(bootstrapTrees.get(0));
        int total = bootstrapTrees.size();

        List<Clade> clades = collectClades(bootstrapTrees);
        clades.removeIf(clade -> clade.members.size() == leaves.size());
        clades.removeIf(clade -> clade.count * 2 <= total);
        clades.sort(Comparator
                .comparingInt((Clade clade) -> -clade.count)
                .thenComparingInt(clade -> -clade.members.size())
                .thenComparing(clade -> String.join(",", clade.members)));

        PhyloTree.Node root = new PhyloTree.Node(null, 0.0);
        for (String leaf : leaves) {
            root.getChildren().add(new PhyloTree.Node(leaf, 0.0));
        }

        for (Clade clade : clades) {
            insertClade(root, clade, total);
        }

        sortChildren(root);
        return new PhyloTree(root);
    }

    private static List<String> collectLeaves(PhyloTree tree) {
        List<String> leaves = new ArrayList<>();
        collectLeaves(tree.getRoot(), leaves);
        return leaves;
    }

    private static void collectLeaves(PhyloTree.Node node, List<String> leaves) {
        if (node.isLeaf()) {
            leaves.add(node.getLabel());
            return;
        }
        for (PhyloTree.Node child : node.getChildren()) {
            collectLeaves(child, leaves);
        }
    }

    private static List<Clade> collectClades(List<PhyloTree> trees) {
        List<Clade> all = new ArrayList<>();
        for (PhyloTree tree : trees) {
            Set<String> seen = new HashSet<>();
            collectClades(tree.getRoot(), all, seen);
        }
        return all;
    }

    private static TreeSet<String> collectClades(PhyloTree.Node node,
                                                 List<Clade> all,
                                                 Set<String> seenInTree) {
        TreeSet<String> members = new TreeSet<>();
        if (node.isLeaf()) {
            members.add(node.getLabel());
            return members;
        }
        for (PhyloTree.Node child : node.getChildren()) {
            members.addAll(collectClades(child, all, seenInTree));
        }
        String key = String.join(",", members);
        if (seenInTree.add(key)) {
            Clade existing = null;
            for (Clade clade : all) {
                if (clade.key.equals(key)) {
                    existing = clade;
                    break;
                }
            }
            if (existing == null) {
                all.add(new Clade(members, key));
            } else {
                existing.count++;
            }
        }
        return members;
    }

    private static void insertClade(PhyloTree.Node node, Clade clade, int total) {
        PhyloTree.Node container = findContainer(node, clade.members);
        if (container == null) {
            return;
        }

        PhyloTree.Node internal =
                new PhyloTree.Node(supportLabel(clade.count, total), 0.0);
        List<PhyloTree.Node> remaining = new ArrayList<>();
        for (PhyloTree.Node child : container.getChildren()) {
            if (clade.members.containsAll(leafSet(child))) {
                internal.getChildren().add(child);
            } else {
                remaining.add(child);
            }
        }
        if (internal.getChildren().size() < 2) {
            return;
        }
        remaining.add(internal);
        container.getChildren().clear();
        container.getChildren().addAll(remaining);
        sortChildren(container);
        sortChildren(internal);
    }

    private static PhyloTree.Node findContainer(PhyloTree.Node node,
                                                TreeSet<String> members) {
        PhyloTree.Node best = null;
        if (!node.isLeaf() && leafSet(node).containsAll(members)) {
            best = node;
        }
        for (PhyloTree.Node child : node.getChildren()) {
            if (child.isLeaf()) {
                continue;
            }
            TreeSet<String> childLeaves = leafSet(child);
            boolean overlaps = false;
            boolean childContains = true;
            for (String member : members) {
                if (childLeaves.contains(member)) {
                    overlaps = true;
                }
            }
            for (String leaf : childLeaves) {
                if (!members.contains(leaf)) {
                    childContains = false;
                }
            }
            if (overlaps && !childContains) {
                return null;
            }
            if (childContains) {
                PhyloTree.Node deeper = findContainer(child, members);
                if (deeper == null) {
                    return null;
                }
                best = deeper;
            }
        }
        return best;
    }

    private static TreeSet<String> leafSet(PhyloTree.Node node) {
        TreeSet<String> leaves = new TreeSet<>();
        leafSet(node, leaves);
        return leaves;
    }

    private static void leafSet(PhyloTree.Node node, TreeSet<String> leaves) {
        if (node.isLeaf()) {
            leaves.add(node.getLabel());
            return;
        }
        for (PhyloTree.Node child : node.getChildren()) {
            leafSet(child, leaves);
        }
    }

    private static String supportLabel(int count, int total) {
        double support = (double) count / total;
        String label = String.format(java.util.Locale.ROOT, "%.4f", support);
        return label;
    }

    private static void sortChildren(PhyloTree.Node node) {
        node.getChildren().sort(Comparator.comparing(ConsensusBuilder::childSortKey));
    }

    private static String childSortKey(PhyloTree.Node node) {
        if (node.isLeaf()) {
            return node.getLabel();
        }
        return String.join(",", leafSet(node));
    }

    private static final class Clade {
        private final TreeSet<String> members;
        private final String key;
        private int count = 1;

        private Clade(TreeSet<String> members, String key) {
            this.members = new TreeSet<>(members);
            this.key = key;
        }
    }
}
