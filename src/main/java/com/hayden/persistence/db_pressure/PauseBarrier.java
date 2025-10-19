package com.hayden.persistence.db_pressure;

import java.util.concurrent.Phaser;
import java.util.function.Supplier;

public final class PauseBarrier {
    private final Phaser phaser = new Phaser(1); // party 0 = the coordinator
    private volatile boolean pauseRequested = false;

    public void registerIndexer() { phaser.register(); }
    public void deregisterIndexer() { phaser.arriveAndDeregister(); }

    // Called by indexers periodically
    public void checkpointIfPaused() {
        if (!pauseRequested) return;
        int phase = phaser.getPhase();
        phaser.arriveAndAwaitAdvance(); // park at checkpoint until coordinator advances
        // when resumed, we are in phase+1
    }

    // Requests:
    public <T> T pauseThenRun(Supplier<T> critical) {
        pauseRequested = true;
        // Wait until all currently running indexers hit a checkpoint
        int phase = phaser.getPhase();
        phaser.arriveAndAwaitAdvance(); // advance to "paused" phase
        try {
            return critical.get();
        } finally {
            // resume: advance again so indexers continue
            pauseRequested = false;
            phaser.arriveAndAwaitAdvance();
        }
    }
}
