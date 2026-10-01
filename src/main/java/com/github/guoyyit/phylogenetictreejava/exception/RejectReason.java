package com.github.guoyyit.phylogenetictreejava.exception;

/**
 * Distinguishable reasons an alignment is rejected.
 */
public enum RejectReason {
    EMPTY_INPUT,
    TOO_FEW_SEQUENCES,
    LENGTH_MISMATCH,
    INVALID_CHARACTER,
    INSUFFICIENT_INFORMATION
}
