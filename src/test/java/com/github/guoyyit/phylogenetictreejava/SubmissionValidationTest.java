package com.github.guoyyit.phylogenetictreejava;

import com.github.guoyyit.phylogenetictreejava.bootstrap.BootstrapConfig;
import com.github.guoyyit.phylogenetictreejava.exception.AlignmentException;
import com.github.guoyyit.phylogenetictreejava.exception.RejectReason;
import com.github.guoyyit.phylogenetictreejava.service.AnalysisService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Submit-stage validation: rejections are synchronous, carry distinguishable
 * reasons and never leave task directories or intermediate files behind.
 */
class SubmissionValidationTest {

    private static final BootstrapConfig CONFIG = new BootstrapConfig(10, 1L, 1, 0);

    private static long entries(Path dir) throws Exception {
        if (!Files.isDirectory(dir)) {
            return 0;
        }
        try (Stream<Path> entries = Files.list(dir)) {
            return entries.count();
        }
    }

    @Test
    void pairWithoutComparableSitesIsRejectedSynchronously(@TempDir Path temp) throws Exception {
        AnalysisService service = new AnalysisService(temp, 2);
        // a and b never overlap at a comparable site, but every column still has at
        // least two known residues (a-c and b-c do), so the alignment would otherwise
        // be perfectly valid: this is exactly the pairwise-only rejection case.
        String fasta = ">a\nAC--\n>b\n--GT\n>c\nACGT\n";
        AlignmentException error = assertThrows(AlignmentException.class,
                () -> service.submit(fasta, CONFIG));
        assertEquals(RejectReason.INSUFFICIENT_INFORMATION, error.getReason());
        assertTrue(error.getMessage().contains("a"),
                "message should identify the offending pair: " + error.getMessage());
        assertTrue(error.getMessage().contains("b"),
                "message should identify the offending pair: " + error.getMessage());
        assertEquals(0, entries(temp), "rejected submission must not create a task directory");
        service.shutdown();
    }

    @Test
    void fullyUnknownSequenceIsRejectedAsInsufficientInformation(@TempDir Path temp)
            throws Exception {
        AnalysisService service = new AnalysisService(temp, 2);
        String fasta = ">a\nACGT\n>b\nNN??\n>c\nACGT\n";
        AlignmentException error = assertThrows(AlignmentException.class,
                () -> service.submit(fasta, CONFIG));
        assertEquals(RejectReason.INSUFFICIENT_INFORMATION, error.getReason());
        assertEquals(0, entries(temp));
        service.shutdown();
    }

    @Test
    void duplicateNamesAreRejectedWithDistinctReason(@TempDir Path temp) throws Exception {
        AnalysisService service = new AnalysisService(temp, 2);
        String fasta = ">a\nACGT\n>b\nACGA\n>a\nACGG\n";
        AlignmentException error = assertThrows(AlignmentException.class,
                () -> service.submit(fasta, CONFIG));
        assertEquals(RejectReason.DUPLICATE_NAME, error.getReason());
        assertTrue(error.getMessage().contains("a"));
        assertEquals(0, entries(temp), "rejected submission must not create a task directory");
        service.shutdown();
    }

    @Test
    void duplicateNameIsDistinguishableFromLengthMismatchAndInvalidCharacter(
            @TempDir Path temp) throws Exception {
        AnalysisService service = new AnalysisService(temp, 2);
        // Duplicate name takes precedence and never surfaces as an internal error.
        String fasta = ">a\nACGT\n>a\nACG\n";
        AlignmentException error = assertThrows(AlignmentException.class,
                () -> service.submit(fasta, CONFIG));
        assertEquals(RejectReason.DUPLICATE_NAME, error.getReason());

        AlignmentException mismatch = assertThrows(AlignmentException.class,
                () -> service.submit(">x\nACGT\n>y\nACG\n", CONFIG));
        assertEquals(RejectReason.LENGTH_MISMATCH, mismatch.getReason());

        AlignmentException invalid = assertThrows(AlignmentException.class,
                () -> service.submit(">x\nACGZ\n>y\nACGT\n", CONFIG));
        assertEquals(RejectReason.INVALID_CHARACTER, invalid.getReason());
        service.shutdown();
    }

    @Test
    void validSubmissionStillAccepted(@TempDir Path temp) throws Exception {
        AnalysisService service = new AnalysisService(temp, 2);
        String id = service.submit(">a\nACGT\n>b\nACGA\n", CONFIG);
        assertTrue(Files.isDirectory(temp.resolve(id)));
        for (int i = 0; i < 200 && service.status(id).orElseThrow().getStatus()
                != com.github.guoyyit.phylogenetictreejava.service.AnalysisStatus.COMPLETED;
                i++) {
            Thread.sleep(20);
        }
        assertEquals(com.github.guoyyit.phylogenetictreejava.service.AnalysisStatus.COMPLETED,
                service.status(id).orElseThrow().getStatus());
        service.shutdown();
    }
}
