package com.github.guoyyit.phylogenetictreejava.bootstrap;

/**
 * Raised when a bootstrap run is stopped by cancellation or timeout.
 */
public class BootstrapCancelledException extends RuntimeException {

    public BootstrapCancelledException() {
        super("bootstrap run cancelled");
    }
}
