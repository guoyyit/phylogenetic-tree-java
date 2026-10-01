package com.github.guoyyit.phylogenetictreejava.model;

/**
 * Thrown when submitted sequences cannot form a valid, informative alignment.
 * The {@link Reason} lets callers distinguish every rejection cause.
 */
public final class InputValidationException extends RuntimeException {

    public enum Reason {
        EMPTY_INPUT,
        TOO_FEW_SEQUENCES,
        LENGTH_MISMATCH,
        INVALID_CHARACTER,
        INVALID_NAME,
        DUPLICATE_NAME,
        INSUFFICIENT_INFORMATION
    }

    private final Reason reason;

    public InputValidationException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
