package com.github.guoyyit.phylogenetictreejava.bootstrap;

import com.github.guoyyit.phylogenetictreejava.model.Alignment;
import com.github.guoyyit.phylogenetictreejava.model.Sequence;

import java.util.ArrayList;
import java.util.Random;
import java.util.List;

/**
 * Site-wise resampling of an alignment with replacement.
 */
public final class BootstrapResampler {

    public BootstrapResampler() {
    }

    public Alignment resample(Alignment alignment, Random random) {
        int sites = alignment.length();
        int[] sampled = new int[sites];
        for (int site = 0; site < sites; site++) {
            sampled[site] = random.nextInt(sites);
        }

        List<Sequence> sequences = new ArrayList<>(alignment.size());
        for (int i = 0; i < alignment.size(); i++) {
            Sequence original = alignment.get(i);
            String residues = original.residues();
            StringBuilder builder = new StringBuilder(sites);
            for (int site : sampled) {
                builder.append(residues.charAt(site));
            }
            sequences.add(new Sequence(i, original.name(), builder.toString()));
        }
        return new Alignment(sequences);
    }
}
