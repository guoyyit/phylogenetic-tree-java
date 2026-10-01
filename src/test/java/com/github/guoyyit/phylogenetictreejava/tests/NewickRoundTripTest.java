package com.github.guoyyit.phylogenetictreejava.tests;

import com.github.guoyyit.phylogenetictreejava.distance.DistanceMatrix;
import com.github.guoyyit.phylogenetictreejava.io.NewickCodec;
import com.github.guoyyit.phylogenetictreejava.tree.NeighborJoiningTreeBuilder;
import com.github.guoyyit.phylogenetictreejava.tree.PhyloTree;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NewickRoundTripTest {

    private final NewickCodec codec = new NewickCodec();

    @Test
    void parsesAndReserializesOwnOutputByteIdentically() {
        double[][] additive = {
                {0.00, 0.50, 0.90, 0.90, 0.70},
                {0.50, 0.00, 0.90, 0.90, 0.70},
                {0.90, 0.90, 0.00, 0.40, 0.80},
                {0.90, 0.90, 0.40, 0.00, 0.80},
                {0.70, 0.70, 0.80, 0.80, 0.00}
        };
        PhyloTree original = new NeighborJoiningTreeBuilder().build(
                new DistanceMatrix(List.of("A", "B", "C", "D", "E"), additive));

        String first = codec.write(original);
        PhyloTree parsed = codec.parse(first);
        String second = codec.write(parsed);

        assertEquals(first, second);
    }

    @Test
    void preservesInternalSupportLabels() {
        String newick = "((A,B)0.9700,C)0.5000;";
        PhyloTree parsed = codec.parse(newick);
        assertEquals(newick, codec.write(parsed));
        assertEquals("0.9700",
                parsed.getRoot().getChildren().get(0).getLabel());
    }

    @Test
    void roundTripsMultipleTrees() {
        String one = "((A:0.1,B:0.2):0.3,C);\n";
        String two = "((A:0.4,C:0.5):0.6,B);\n";
        String bundle = one + two;

        List<PhyloTree> trees = codec.parseAll(bundle);
        assertEquals(2, trees.size());
        assertEquals(bundle, codec.writeAll(trees));
    }
}
