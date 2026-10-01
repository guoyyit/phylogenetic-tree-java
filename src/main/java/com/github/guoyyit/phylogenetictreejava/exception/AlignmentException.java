package com.github.guoyyit.phylogenetictreejava.exception;

/**
 * Thrown when an alignment cannot be analyzed, carrying a machine-readable reason.
 */
public class AlignmentException extends RuntimeException {

    private final RejectReason reason;

    public AlignmentException(RejectReason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public RejectReason getReason() {
        return reason;
    }
}
