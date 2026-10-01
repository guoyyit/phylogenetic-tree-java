package com.github.guoyyit.phylogenetictreejava.tests;

import com.github.guoyyit.phylogenetictreejava.io.AlignmentValidator;
import com.github.guoyyit.phylogenetictreejava.model.Alignment;
import com.github.guoyyit.phylogenetictreejava.model.AlignmentInput;
import com.github.guoyyit.phylogenetictreejava.model.InputValidationException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AlignmentValidationTest {

    private final AlignmentValidator validator = new AlignmentValidator();

    @Test
    void rejectsEmptyInput() {
        InputValidationException exception = assertThrows(
                InputValidationException.class,
                () -> validator.validate(List.of()));
        assertEquals(InputValidationException.Reason.EMPTY_INPUT, exception.reason());
    }

    @Test
    void rejectsSingleSequence() {
        InputValidationException exception = assertThrows(
                InputValidationException.class,
                () -> validator.validate(List.of(
                        new AlignmentInput("a", "ACGT"))));
        assertEquals(InputValidationException.Reason.TOO_FEW_SEQUENCES,
                exception.reason());
    }

    @Test
    void rejectsLengthMismatch() {
        InputValidationException exception = assertThrows(
                InputValidationException.class,
                () -> validator.validate(List.of(
                        new AlignmentInput("a", "ACGT"),
                        new AlignmentInput("b", "ACG"))));
        assertEquals(InputValidationException.Reason.LENGTH_MISMATCH, exception.reason());
    }

    @Test
    void rejectsIllegalCharacter() {
        InputValidationException exception = assertThrows(
                InputValidationException.class,
                () -> validator.validate(List.of(
                        new AlignmentInput("a", "ACGT"),
                        new AlignmentInput("b", "ACGZ"))));
        assertEquals(InputValidationException.Reason.INVALID_CHARACTER,
                exception.reason());
    }

    @Test
    void rejectsBlankAndDuplicateNames() {
        InputValidationException blank = assertThrows(
                InputValidationException.class,
                () -> validator.validate(List.of(
                        new AlignmentInput(" ", "ACGT"),
                        new AlignmentInput("b", "ACGT"))));
        assertEquals(InputValidationException.Reason.INVALID_NAME, blank.reason());

        InputValidationException duplicate = assertThrows(
                InputValidationException.class,
                () -> validator.validate(List.of(
                        new AlignmentInput("a", "ACGT"),
                        new AlignmentInput("a", "ACGA"))));
        assertEquals(InputValidationException.Reason.DUPLICATE_NAME,
                duplicate.reason());
    }

    @Test
    void rejectsAlignmentWithoutAnyComparableSite() {
        InputValidationException exception = assertThrows(
                InputValidationException.class,
                () -> validator.validate(List.of(
                        new AlignmentInput("a", "---?"),
                        new AlignmentInput("b", "???-"))));
        assertEquals(InputValidationException.Reason.INSUFFICIENT_INFORMATION,
                exception.reason());
    }

    @Test
    void acceptsGappyButComparableAlignment() {
        Alignment alignment = validator.validate(List.of(
                new AlignmentInput("a", "AC-GT"),
                new AlignmentInput("b", "ACAGT"),
                new AlignmentInput("c", "ACAG-")));
        assertEquals(3, alignment.size());
        assertEquals(5, alignment.length());
    }
}
