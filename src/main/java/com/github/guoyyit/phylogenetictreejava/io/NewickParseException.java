package com.github.guoyyit.phylogenetictreejava.io;

/**
 * Thrown when Newick text produced (or claimed to be produced) by this
 * service cannot be parsed.
 */
public final class NewickParseException extends RuntimeException {

    public NewickParseException(String message) {
        super(message);
    }
}
