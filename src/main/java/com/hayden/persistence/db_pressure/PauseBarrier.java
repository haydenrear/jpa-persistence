package com.hayden.persistence.db_pressure;

import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.Phaser;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public final class PauseBarrier {

    public record ResultOrExc<T>(T t, Exception cause) {

        public static <T> ResultOrExc<T> of(T t) {
            return new ResultOrExc<>(t, null);
        }

        public static <T> ResultOrExc<T> err(Exception t) {
            return new ResultOrExc<>(null, t);
        }

        public boolean isErr() {
            return cause != null;
        }

    }

    public interface WaiterExecution<T> {
        ResultOrExc<T> run();
    }

    private final Phaser phaser = new Phaser(0);

    private volatile boolean pauseRequested = false;

    private final AtomicInteger waiterRegistrationCount = new AtomicInteger(0);

    private final AtomicInteger coordinatorDepth = new AtomicInteger(0);

    private final ThreadLocal<Integer> coordinatorDepthLocal = ThreadLocal.withInitial(() -> 0);


    boolean hasWaiterRegistration() {
        return waiterRegistrationCount.get() != 0;
    }

    boolean hasCoordinatorDepth() {
        return coordinatorDepth.get() != 0;
    }

    boolean hasCoordinatorDepthLocal() {
        return coordinatorDepthLocal.get() != 0;
    }

    /**
     * Deregister a waiter.
     */
    public void deregisterWaiter() {
        waiterRegistrationCount.decrementAndGet();
        phaser.arriveAndDeregister();
    }

    public void checkpointIfPaused() {
        if (!pauseRequested)
            return;

        //  if transaction running in this thread, don't hold it up.
        if (TransactionSynchronizationManager.isActualTransactionActive())
            return;

        if (coordinatorDepthLocal.get() != 0)
            return;

        int cnt = waiterRegistrationCount.getAndIncrement();

        phaser.register();

        try {
            var p = phaser.getPhase();
            phaser.arriveAndAwaitAdvance();
        } finally {
            deregisterWaiter();
        }


    }

    /**
     * Called by phasers (high-priority requests) to pause all waiters and execute critical section.
     */
    public <T> ResultOrExc<T> pauseWaitersAndRun(WaiterExecution<T> critical) {

        try {
            var coordinatorDepthIncremented = coordinatorDepth.getAndIncrement();

            coordinatorDepthLocal.set(coordinatorDepthLocal.get() + 1);

            phaser.register();

            pauseRequested = true;

            var p = phaser.getPhase();

            phaser.arriveAndAwaitAdvance();


            var r = critical.run();

            return r;
        } finally {
            if (coordinatorDepth.decrementAndGet() == 0) {
                pauseRequested = false;
            }

            phaser.arriveAndDeregister();
            coordinatorDepthLocal.set(coordinatorDepthLocal.get() - 1);
        }
    }


}
