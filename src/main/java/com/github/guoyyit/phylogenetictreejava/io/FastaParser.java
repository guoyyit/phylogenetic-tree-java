package com.github.guoyyit.phylogenetictreejava.io;

import com.github.guoyyit.phylogenetictreejava.model.AlignmentInput;
import com.github.guoyyit.phylogenetictreejava.model.InputValidationException;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses FASTA text into raw {@link AlignmentInput} records. No validation here.
 */
public final class FastaParser {

    public FastaParser() {
    }

    public List<AlignmentInput> parse(String content) {
        List<AlignmentInput> records = new ArrayList<>();
        if (content == null || content.isBlank()) {
            return records;
        }

        String header = null;
        StringBuilder residues = new StringBuilder();
        for (String rawLine : content.split("\\R", -1)) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.charAt(0) == ';') {
                continue;
            }
            if (line.charAt(0) == '>') {
                if (header != null) {
                    records.add(new AlignmentInput(header, residues.toString()));
                }
                header = extractName(line.substring(1));
                residues.setLength(0);
            } else {
                if (header == null) {
                    throw new InputValidationException(
                            InputValidationException.Reason.INVALID_CHARACTER,
                            "sequence data appears before any '>' FASTA header");
                }
                residues.append(stripWhitespace(line));
            }
        }
        if (header != null) {
            records.add(new AlignmentInput(header, residues.toString()));
        }
        return records;
    }

    private static String extractName(String headerText) {
        String trimmed = headerText.trim();
        int space = trimmed.indexOf(' ');
        int tab = trimmed.indexOf('\t');
        int end = trimmed.length();
        if (space >= 0) {
            end = Math.min(end, space);
        }
        if (tab >= 0) {
            end = Math.min(end, tab);
        }
        return trimmed.substring(0, end);
    }

    private static String stripWhitespace(String line) {
        StringBuilder builder = new StringBuilder(line.length());
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (!Character.isWhitespace(c)) {
                builder.append(c);
            }
        }
        return builder.toString();
    }
}
