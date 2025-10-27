package com.hayden.persistence.db_pressure;

import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.Phaser;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public final class PauseBarrier {

    // ---- Coordinator side ----
    public record ResultOrExc<T>(T t, Throwable cause) {

        public static <T> ResultOrExc<T> of(T t) {
            return new ResultOrExc<>(t, null);
        }

        public static <T> ResultOrExc<T> err(Throwable th) {
            return new ResultOrExc<>(null, th);
        }

        public boolean isErr() {
            return cause != null;
        }
    }

    @FunctionalInterface
    public interface WaiterExecution<T> {
        ResultOrExc<T> run();
    }

    private final Phaser phaser = new Phaser(0);

    private volatile boolean pauseRequested = false;

    // global: counts overlapping phasers; first does handshake, last releases
    private final AtomicInteger pauseDepth = new AtomicInteger(0);

    // per-thread: prevents coordinator from blocking on its own checkpoints
    private final ThreadLocal<Integer> coordinatorDepthLocal = ThreadLocal.withInitial(() -> 0);

    // ---- Waiter side ----
    public void checkpointIfPaused() {
        if (!pauseRequested) return;
        if (coordinatorDepthLocal.get() > 0) return; // don’t block the coordinator thread
        if (TransactionSynchronizationManager.isActualTransactionActive()) return;

        // Two-phase handshake: acknowledge, then wait for release
        phaser.register();
        try {
            int phase = phaser.getPhase();
            // 1) Acknowledge pause (let coordinator know we're parked at the gate)
            phaser.arriveAndAwaitAdvance();
            // 2) Stay parked until coordinator releases
            phaser.arriveAndAwaitAdvance();
        } finally {
            phaser.arriveAndDeregister();
        }
    }

    public <T> ResultOrExc<T> pauseWaitersAndRun(WaiterExecution<T> critical) {
        boolean coordParty = false;
        try {
            // mark this thread as coordinator for re-entrant waiter calls
            coordinatorDepthLocal.set(coordinatorDepthLocal.get() + 1);

            // coordinator participates as a party
            phaser.register();
            coordParty = true;

            int before = pauseDepth.getAndIncrement();
            if (before == 0) {
                // First/outermost phaser: publish pause and do the 2-phase handshake
                pauseRequested = true;

                // Phase A: wait for all waiters to ACK they’re paused (first gate)
                phaser.arriveAndAwaitAdvance();

                // ---- critical section runs while waiters are parked at the second gate ----
                ResultOrExc<T> res = critical.run();

                // Phase B: release waiters (second gate)
                phaser.arriveAndAwaitAdvance();

                return res;
            } else {
                // Already paused: no handshake; just arrive this phase
                phaser.arrive(); // arrive at whatever gate we're at so we don't block others
                return critical.run();
            }

        } catch (Throwable th) {
            return ResultOrExc.err(th);
        } finally {
            // Decrement depth and clear pause flag only when last phaser exits
            int remaining = pauseDepth.decrementAndGet();

            if (remaining == 0)
                pauseRequested = false;

            if (coordParty)
                phaser.arriveAndDeregister();
            else
                phaser.arrive();

            int d = coordinatorDepthLocal.get() - 1;
            if (d == 0)
                coordinatorDepthLocal.remove();
            else
                coordinatorDepthLocal.set(d);
        }
    }
}
