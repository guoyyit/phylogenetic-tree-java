package com.github.guoyyit.phylogenetictreejava.io;

import com.github.guoyyit.phylogenetictreejava.model.Alignment;
import com.github.guoyyit.phylogenetictreejava.exception.AlignmentException;
import com.github.guoyyit.phylogenetictreejava.exception.RejectReason;
import com.github.guoyyit.phylogenetictreejava.model.Sequence;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Reads and validates aligned sequences in FASTA format.
 */
public final class FastaReader {

    /**
     * Residues accepted in aligned input. Unambiguous: A C G T. IUPAC ambiguity:
     * R Y S W K M B D H V. Fully unknown: N X. Gaps: '-'.'. Missing/unknown: '?'.
     */
    private static final Set<Character> ALPHABET = Set.of(
            'A', 'C', 'G', 'T',
            'R', 'Y', 'S', 'W', 'K', 'M',
            'B', 'D', 'H', 'V',
            'N', 'X', '?', '-', '.');

    private FastaReader() {
    }

    public static Alignment parse(String text) {
        if (text == null || text.isBlank()) {
            throw new AlignmentException(RejectReason.EMPTY_INPUT, "input is empty");
        }

        List<String> names = new ArrayList<>();
        List<StringBuilder> bodies = new ArrayList<>();
        Set<String> seenNames = new HashSet<>();
        int current = -1;

        for (String rawLine : text.split("\\R", -1)) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith(";")) {
                continue;
            }
            if (line.startsWith(">")) {
                String header = line.substring(1).trim();
                String name = header.isEmpty()
                        ? "seq" + (names.size() + 1)
                        : header.split("\\s+", 2)[0];
                if (!seenNames.add(name)) {
                    throw new IllegalArgumentException("duplicate sequence name: " + name);
                }
                names.add(name);
                bodies.add(new StringBuilder());
                current++;
            } else {
                if (current < 0) {
                    throw new AlignmentException(RejectReason.EMPTY_INPUT,
                            "sequence data before any FASTA header");
                }
                for (int k = 0; k < line.length(); k++) {
                    char c = Character.toUpperCase(line.charAt(k));
                    if (!ALPHABET.contains(c)) {
                        throw new AlignmentException(RejectReason.INVALID_CHARACTER,
                                "unsupported character '" + line.charAt(k) + "' in sequence "
                                        + names.get(current));
                    }
                    bodies.get(current).append(c);
                }
            }
        }

        if (names.isEmpty()) {
            throw new AlignmentException(RejectReason.EMPTY_INPUT,
                    "no FASTA records found");
        }
        long nonEmptyRecords = bodies.stream().filter(body -> body.length() > 0).count();
        if (nonEmptyRecords == 0) {
            throw new AlignmentException(RejectReason.EMPTY_INPUT,
                    "FASTA headers present but no sequence data");
        }
        if (names.size() < 2) {
            throw new AlignmentException(RejectReason.TOO_FEW_SEQUENCES,
                    "at least 2 sequences are required, got " + names.size());
        }
        if (nonEmptyRecords < 2) {
            throw new AlignmentException(RejectReason.TOO_FEW_SEQUENCES,
                    "at least 2 non-empty sequences are required, got " + nonEmptyRecords);
        }

        int length = bodies.get(0).length();
        List<Sequence> sequences = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            int len = bodies.get(i).length();
            if (len == 0) {
                throw new AlignmentException(RejectReason.LENGTH_MISMATCH,
                        "sequence '" + names.get(i) + "' is empty, expected length " + length);
            }
            if (length == 0 || len != length) {
                throw new AlignmentException(RejectReason.LENGTH_MISMATCH,
                        "sequence '" + names.get(i) + "' has length " + len
                                + ", expected " + length);
            }
            sequences.add(new Sequence(names.get(i), bodies.get(i).toString()));
        }
        if (length == 0) {
            throw new AlignmentException(RejectReason.INSUFFICIENT_INFORMATION,
                    "alignment contains zero columns");
        }
        boolean anyComparableColumn = false;
        for (int column = 0; column < length && !anyComparableColumn; column++) {
            int known = 0;
            for (StringBuilder body : bodies) {
                if (isKnown(body.charAt(column))) {
                    known++;
                }
            }
            if (known >= 2) {
                anyComparableColumn = true;
            }
        }
        if (!anyComparableColumn) {
            throw new AlignmentException(RejectReason.INSUFFICIENT_INFORMATION,
                    "alignment has no column with at least two non-gap, non-unknown residues");
        }
        return new Alignment(sequences, length);
    }

    public static Alignment read(Path path) throws IOException {
        return parse(Files.readString(path));
    }

    private static boolean isKnown(char c) {
        return c != '-' && c != '.' && c != 'N' && c != 'X' && c != '?';
    }
}
