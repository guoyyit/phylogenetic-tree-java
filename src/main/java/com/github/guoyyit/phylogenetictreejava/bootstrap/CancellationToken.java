package com.github.guoyyit.phylogenetictreejava.bootstrap;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Cooperative cancellation signal shared with one analysis task.
 */
public final class CancellationToken {

    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    public CancellationToken() {
    }

    public void cancel() {
        cancelled.set(true);
    }

    public boolean isCancelled() {
        return cancelled.get();
    }
}
