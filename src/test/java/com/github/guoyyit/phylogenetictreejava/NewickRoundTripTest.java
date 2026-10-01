package com.github.guoyyit.phylogenetictreejava;

import com.github.guoyyit.phylogenetictreejava.io.NewickParser;
import com.github.guoyyit.phylogenetictreejava.io.NewickWriter;
import com.github.guoyyit.phylogenetictreejava.model.TreeNode;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NewickRoundTripTest {

    @Test
    void parseWriteReproducesOriginalOutput() {
        String original = "((b:0.05000000,c:0.25000000)N2[0.8000]:0.02500000,"
                + "(a:0,d:0.30000000)N1[1]:0.02500000)ROOT[1];";
        TreeNode parsed = NewickParser.parse(original);
        String reserialized = NewickWriter.write(parsed);
        assertEquals(original, reserialized);
    }

    @Test
    void quotedLabelsSurviveRoundTrip() {
        TreeNode root = new TreeNode();
        root.setLabel("ROOT");
        TreeNode internal = new TreeNode();
        internal.setLabel("clade, one");
        internal.setSupport(0.25);
        TreeNode leaf1 = new TreeNode();
        leaf1.setLabel("weird (name)");
        leaf1.setBranchLength(0.125);
        TreeNode leaf2 = new TreeNode();
        leaf2.setLabel("it's");
        leaf2.setBranchLength(0.25);
        internal.addChild(leaf1);
        internal.addChild(leaf2);
        root.addChild(internal);
        TreeNode leaf3 = new TreeNode();
        leaf3.setLabel("plain");
        root.addChild(leaf3);

        String newick = NewickWriter.write(root);
        assertEquals(newick, NewickWriter.write(NewickParser.parse(newick)));
        assertEquals("weird (name)",
                NewickParser.parse(newick).getChildren().get(0)
                        .getChildren().get(0).getLabel());
    }

    @Test
    void zeroBranchLengthsAreCanonical() {
        TreeNode root = NewickParser.parse("(a:0,b:0)ROOT;");
        assertEquals("(a:0,b:0)ROOT;", NewickWriter.write(root));
    }

    @Test
    void multiTreeFileRoundTripsLosslessly() {
        List<String> lines = List.of(
                "(a:0,b:0.10000000,c:0.20000000)ROOT;",
                "((a:0,b:0.05000000)N1[0.5000]:0.02500000,c:0.20000000)ROOT[1];");
        String file = lines.get(0) + "\n" + lines.get(1) + "\n";
        String reparsed = NewickParser.parseMany(file).stream()
                .map(NewickWriter::write)
                .collect(Collectors.joining("\n", "", "\n"));
        assertEquals(file, reparsed);
    }
}
