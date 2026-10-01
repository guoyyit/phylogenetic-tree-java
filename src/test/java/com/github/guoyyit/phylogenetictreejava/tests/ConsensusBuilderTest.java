package com.github.guoyyit.phylogenetictreejava.tests;

import com.github.guoyyit.phylogenetictreejava.io.NewickCodec;
import com.github.guoyyit.phylogenetictreejava.tree.ConsensusBuilder;
import com.github.guoyyit.phylogenetictreejava.tree.PhyloTree;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ConsensusBuilderTest {

    private final NewickCodec codec = new NewickCodec();
    private final ConsensusBuilder builder = new ConsensusBuilder();

    @Test
    void labelsInternalNodesWithSupportProportion() {
        List<PhyloTree> trees = List.of(
                codec.parse("((A,B),C,D);"),
                codec.parse("((A,B),C,D);"),
                codec.parse("((A,B),(C,D));"),
                codec.parse("(A,B,C,D);"));

        PhyloTree consensus = builder.build(trees);
        String newick = codec.write(consensus);

        assertNotNull(newick);
        assertEquals(1, countOccurrences(newick, "0.7500"));
        assertEquals(0, countOccurrences(newick, "0.5000"),
                "strict majority rule excludes 50% clades");
    }

    @Test
    void unanimousCladeHasFullSupportAndOutputIsStable() {
        List<PhyloTree> trees = List.of(
                codec.parse("((A,B),(C,D));"),
                codec.parse("((A,B),(C,D));"));

        String first = codec.write(builder.build(trees));
        String second = codec.write(builder.build(trees));
        assertEquals(first, second);
        assertEquals(2, countOccurrences(first, "1.0000"));
    }

    private static int countOccurrences(String text, String token) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(token, index)) >= 0) {
            count++;
            index += token.length();
        }
        return count;
    }
}
