package com.github.guoyyit.phylogenetictreejava.io;

import com.github.guoyyit.phylogenetictreejava.model.Alignment;
import com.github.guoyyit.phylogenetictreejava.model.AlignmentInput;
import com.github.guoyyit.phylogenetictreejava.model.InputValidationException;
import com.github.guoyyit.phylogenetictreejava.model.Sequence;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Validates raw sequences and produces an immutable {@link Alignment}.
 *
 * <p>Rejection reasons (never silent): empty input, fewer than two sequences,
 * length mismatch, illegal characters, missing/duplicate names, and
 * insufficient information (no comparable site at all).</p>
 */
public final class AlignmentValidator {

    public AlignmentValidator() {
    }

    public Alignment validate(List<AlignmentInput> inputs) throws InputValidationException {
        if (inputs == null || inputs.isEmpty()) {
            throw new InputValidationException(
                    InputValidationException.Reason.EMPTY_INPUT,
                    "no sequences were provided");
        }
        if (inputs.size() < 2) {
            throw new InputValidationException(
                    InputValidationException.Reason.TOO_FEW_SEQUENCES,
                    "at least two sequences are required, but only "
                            + inputs.size() + " was provided");
        }

        Set<String> names = new HashSet<>();
        List<String> normalizedNames = new ArrayList<>(inputs.size());
        for (int i = 0; i < inputs.size(); i++) {
            AlignmentInput input = inputs.get(i);
            String name = input == null ? null : input.name();
            if (name == null || name.isBlank()) {
                throw new InputValidationException(
                        InputValidationException.Reason.INVALID_NAME,
                        "sequence at index " + i + " has a blank name");
            }
            if (!names.add(name)) {
                throw new InputValidationException(
                        InputValidationException.Reason.DUPLICATE_NAME,
                        "duplicate sequence name: " + name);
            }
            normalizedNames.add(name);
        }

        int alignmentLength = -1;
        List<String> residues = new ArrayList<>(inputs.size());
        for (int i = 0; i < inputs.size(); i++) {
            String raw = inputs.get(i).sequence();
            if (raw == null || raw.isEmpty()) {
                throw new InputValidationException(
                        InputValidationException.Reason.LENGTH_MISMATCH,
                        "sequence '" + normalizedNames.get(i) + "' is empty");
            }
            if (alignmentLength < 0) {
                alignmentLength = raw.length();
            } else if (raw.length() != alignmentLength) {
                throw new InputValidationException(
                        InputValidationException.Reason.LENGTH_MISMATCH,
                        "sequence '" + normalizedNames.get(i) + "' has length "
                                + raw.length() + " but expected " + alignmentLength);
            }
            residues.add(raw);
        }

        for (int i = 0; i < inputs.size(); i++) {
            String raw = residues.get(i);
            StringBuilder normalized = new StringBuilder(raw.length());
            for (int site = 0; site < raw.length(); site++) {
                char c = Character.toUpperCase(raw.charAt(site));
                if (!isAllowed(c)) {
                    throw new InputValidationException(
                            InputValidationException.Reason.INVALID_CHARACTER,
                            "illegal character '" + raw.charAt(site) + "' at sequence '"
                                    + normalizedNames.get(i) + "', site " + (site + 1));
                }
                normalized.append(c);
            }
            residues.set(i, normalized.toString());
        }

        checkInformative(normalizedNames, residues, alignmentLength);

        List<Sequence> sequences = new ArrayList<>(inputs.size());
        for (int i = 0; i < inputs.size(); i++) {
            sequences.add(new Sequence(i, normalizedNames.get(i), residues.get(i)));
        }
        return new Alignment(sequences);
    }

    private static boolean isAllowed(char c) {
        if (c >= 'A' && c <= 'Z') {
            return switch (c) {
                case 'A', 'C', 'G', 'T', 'U',
                        'R', 'Y', 'S', 'W', 'K', 'M',
                        'B', 'D', 'H', 'V', 'N', 'X' -> true;
                default -> false;
            };
        }
        return c == '-' || c == '.' || c == '~' || c == '?';
    }

    private static void checkInformative(List<String> names, List<String> residues,
                                         int length) {
        int count = residues.size();
        for (int i = 0; i < count; i++) {
            for (int j = i + 1; j < count; j++) {
                if (!hasComparableSite(residues.get(i), residues.get(j), length)) {
                    throw new InputValidationException(
                            InputValidationException.Reason.INSUFFICIENT_INFORMATION,
                            "sequences '" + names.get(i) + "' and '" + names.get(j)
                                    + "' share no comparable (non-gap, non-missing) site");
                }
            }
        }
    }

    private static boolean hasComparableSite(String first, String second, int length) {
        for (int site = 0; site < length; site++) {
            if (isComparable(first.charAt(site)) && isComparable(second.charAt(site))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isComparable(char c) {
        return c != '-' && c != '.' && c != '~' && c != '?';
    }
}
