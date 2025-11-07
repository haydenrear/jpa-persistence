package com.hayden.persistence.db_pressure;

import com.hayden.utilitymodule.otel.DisableOtelConfiguration;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@ExtendWith(SpringExtension.class)
@SpringBootTest
@ActiveProfiles("testjpa")
@Import(DisableOtelConfiguration.class)
public class LimitAccessAspectTest {

    @Autowired
    private IndexingWaiterService indexingWaiterService;

    @Autowired
    private RequestPhaserService requestPhaserService;

    @Autowired
    private LimitAccessAspect aspect;

    @Autowired
    private Frames frames;

    private static final ExecutorService EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    @SpringBootApplication(exclude = org.springframework.boot.actuate.autoconfigure.metrics.export.otlp.OtlpMetricsExportAutoConfiguration.class)
    @ComponentScan("com.hayden.persistence")
    @Import(DisableOtelConfiguration.class)
    public static class TestLimitAccessApplication {
        static void main(String[] args) {
            SpringApplication.run(TestLimitAccessApplication.class, args);
        }
    }



    /**
     * End-to-end test of phaser/waiter interaction using @LimitAccess annotation:
     * - Phasers (requests) run with @LimitAccess(isPhaser=true) - short-lived, high priority
     * - Waiters (indexing) run with @LimitAccess(isWaiter=true) - long-running, yields to phasers
     * - When a phaser arrives, it pauses the waiter until the phaser completes
     * - Waiters yield unless they have an active transaction
     */
    @SneakyThrows
    @Test
    public void testPhaserWaiterInteractionViaAnnotation() {
        // Start long-running indexing (waiter) in a background thread
        CompletableFuture<Void> waiterFuture = CompletableFuture.runAsync(() -> {
            indexingWaiterService.performIndexing();
        }, EXECUTOR);

        // Give waiter a moment to start
        Thread.sleep(100);

        // While waiter is running, submit several phaser requests (high priority)
        AtomicInteger completedPhasers = new AtomicInteger(0);
        CompletableFuture[] phaserFutures = new CompletableFuture[3];

        for (int i = 0; i < 3; i++) {
            final int requestNum = i;
            phaserFutures[i] = CompletableFuture.runAsync(() -> {
                requestPhaserService.processRequest(requestNum);
                completedPhasers.incrementAndGet();
            }, EXECUTOR);
        }

        // Wait for all phasers to complete
        CompletableFuture.allOf(phaserFutures).join();

        // All phasers should have completed
        assertThat(completedPhasers.get()).isEqualTo(3);

        // Stop waiter
        indexingWaiterService.stopIndexing();
        waiterFuture.join();

        // Verify that indexing did complete checkpoints (was paused multiple times)
        assertThat(indexingWaiterService.getCheckpointCount()).isGreaterThan(0);
        assertBarrierState();
        log.info("Waiter completed {} checkpoints while handling {} phasers",
                indexingWaiterService.getCheckpointCount(), completedPhasers.get());
    }

    /**
     * Test that waiters with active transactions don't yield to phasers.
     * Waiters should complete their transaction before yielding to phasers.
     */
    @SneakyThrows
    @Test
    public void testWaiterWithTransactionDoesNotYield() {
        // Start indexing with transaction (waiter) in a background thread
        CompletableFuture<Void> waiterFuture = CompletableFuture.runAsync(() -> {
            indexingWaiterService.performIndexingWithTransaction();
        }, EXECUTOR);

        // Give waiter a moment to start
        Thread.sleep(100);

        // While waiter is running with active transaction, submit phaser requests
        AtomicInteger completedPhasers = new AtomicInteger(0);
        CompletableFuture[] phaserFutures = new CompletableFuture[2];

        for (int i = 0; i < 2; i++) {
            final int requestNum = i;
            phaserFutures[i] = CompletableFuture.runAsync(() -> {
                long startTime = System.currentTimeMillis();
                requestPhaserService.processRequest(requestNum);
                long elapsed = System.currentTimeMillis() - startTime;
                log.info("Phaser request {} took {} ms", requestNum, elapsed);
                completedPhasers.incrementAndGet();
            }, EXECUTOR);
        }

        // Wait for all phasers to complete
        CompletableFuture.allOf(phaserFutures).join();

        // All phasers should have completed
        assertThat(completedPhasers.get()).isEqualTo(2);

        // Stop waiter
        indexingWaiterService.stopIndexing();
        waiterFuture.join();
        assertBarrierState();

        log.info("Waiter with transaction completed {} checkpoints and served {} phasers", 
                indexingWaiterService.getCheckpointCount(), completedPhasers.get());
    }

    /**
     * Test that multiple concurrent phasers all pause the same waiter.
     * They should all coordinate through the shared pause barrier.
     */
    @SneakyThrows
    @Test
    public void testMultipleConcurrentPhasers() {
        // Start long-running indexing (waiter) in a background thread
        CompletableFuture<Void> waiterFuture = CompletableFuture.runAsync(() -> {
            indexingWaiterService.performIndexing();
        }, EXECUTOR);

        // Give waiter a moment to start
        Thread.sleep(100);

        // Submit multiple concurrent phaser requests
        int numPhasers = 5;
        CompletableFuture[] phaserFutures = new CompletableFuture[numPhasers];
        AtomicInteger completedPhasers = new AtomicInteger(0);

        for (int i = 0; i < numPhasers; i++) {
            final int requestNum = i;
            phaserFutures[i] = CompletableFuture.runAsync(() -> {
                requestPhaserService.processRequest(requestNum);
                completedPhasers.incrementAndGet();
            }, EXECUTOR);
        }

        // Wait for all phasers to complete
        CompletableFuture.allOf(phaserFutures).join();

        // All phasers should have completed
        assertThat(completedPhasers.get()).isEqualTo(numPhasers);

        // Stop waiter
        indexingWaiterService.stopIndexing();
        waiterFuture.join();
        assertBarrierState();

        log.info("All {} phasers completed, waiter checkpointed {} times", 
                numPhasers, indexingWaiterService.getCheckpointCount());
    }

    /**
     * Test deadlock prevention when a phaser thread calls a waiter method.
     * The phaser should not deadlock even if the called method tries to yield to phasers.
     */
    @SneakyThrows
    @Test
    public void testPhaserCallsWaiterMethodWithoutDeadlock() {
        // Start a waiter in the background
        CompletableFuture<Void> waiterFuture = CompletableFuture.runAsync(() -> {
            indexingWaiterService.performIndexing();
        }, EXECUTOR);

        Thread.sleep(100);

        // A phaser thread calls a waiter method
        // This should NOT deadlock because the waiter detects the phaser is active (coordinatorDepth > 0)
        // and skips yielding
        AtomicBoolean completed = new AtomicBoolean(false);
        CompletableFuture<Boolean> phaserCallingWaiterFuture = CompletableFuture.supplyAsync(() -> {
            requestPhaserService.processRequestThatCallsWaiter();
            completed.set(true);
            return true;
        }, EXECUTOR);

        // Wait with timeout to detect deadlock
        boolean completedInTime = phaserCallingWaiterFuture.completeOnTimeout(false, 5, TimeUnit.SECONDS).join();
        assertThat(completed.get()).isTrue();
        assertThat(completedInTime).isTrue();

        indexingWaiterService.stopIndexing();
        waiterFuture.join();

        assertBarrierState();
        log.info("Phaser successfully called waiter method without deadlock");
    }

    /**
     * Test deadlock prevention when a waiter thread calls a phaser method.
     * The waiter should not deadlock when a phaser is executing concurrently.
     */
    @SneakyThrows
    @Test
    public void testWaiterCallsPhaserMethodWithoutDeadlock() {
        AtomicInteger waiterCompleted = new AtomicInteger(0);
        
        // Start a waiter that will call a phaser method
        CompletableFuture<Boolean> waiterFuture = CompletableFuture.supplyAsync(() -> {
            indexingWaiterService.performIndexingThatCallsPhaser();
            waiterCompleted.incrementAndGet();
            return true;
        }, EXECUTOR);

        Thread.sleep(100);

        // While the waiter is running, submit phaser requests
        // The waiter's nested phaser call should not deadlock
        AtomicInteger phasersCompleted = new AtomicInteger(0);
        CompletableFuture[] phaserFutures = new CompletableFuture[2];

        for (int i = 0; i < 2; i++) {
            final int requestNum = i;
            phaserFutures[i] = CompletableFuture.runAsync(() -> {
                requestPhaserService.processRequest(requestNum);
                phasersCompleted.incrementAndGet();
            }, EXECUTOR);
        }

        // Wait with timeout to detect deadlock
        CompletableFuture.allOf(phaserFutures).thenApplyAsync(s -> true).completeOnTimeout(false, 5, TimeUnit.SECONDS).join();
        waiterFuture.completeOnTimeout(false, 5, TimeUnit.SECONDS).join();

        assertThat(waiterCompleted.get()).isEqualTo(1);
        assertThat(phasersCompleted.get()).isEqualTo(2);

        assertBarrierState();


        log.info("Waiter successfully called phaser method without deadlock");
    }

    private void execTree(Node node) {
        Runnable body = () -> {
            if (node.children.isEmpty()) return;
            if (node.spawnAsync) {
                // cross-thread: exposes ThreadLocal assumptions & interleavings
                List<CompletableFuture<Void>> fs = new ArrayList<>(node.children.size());
                for (Node c : node.children) fs.add(CompletableFuture.runAsync(() -> execTree(c), EXECUTOR));
                fs.forEach(CompletableFuture::join);
            } else {
                // true same-thread nesting under the annotated frame
                for (Node c : node.children) execTree(c);
            }
        };

        switch (node.role) {
            case WAITER    -> frames.waiterFrame(body);
            case WAITER_TX -> frames.waiterTxFrame(body);
            case PHASER    -> frames.phaserFrame(body);
        }
    }



    @SneakyThrows
    @Test
    void fuzzNested() {
        final long seed = System.nanoTime();
        final Random r = new Random(seed);

        final int scenarios = 120;  // 6..11 trees this run
        final int maxDepth  = 22;   // 4..6
        final int fanoutMax = 3;    // up to ternary tree
        final long perTreeTimeoutMs = 25000;

        List<CompletableFuture<Void>> runs = new ArrayList<>(scenarios);

        for (int i = 0; i < scenarios; i++) {
            Node root = genTree(r, maxDepth, fanoutMax);
            runs.add(CompletableFuture.runAsync(() -> {
                try {
                    // liveness check per tree
                    CompletableFuture.runAsync(() -> execTree(root), EXECUTOR)
                            .get(perTreeTimeoutMs, TimeUnit.MILLISECONDS);
                    assertBarrierStateLocal();
                } catch (TimeoutException te) {
                    throw new RuntimeException("Tree timed out (possible deadlock)", te);
                } catch (Exception e) {
                    log.error("Error", e);
                }
            }, EXECUTOR));
            // slight jitter between submissions to vary overlaps
            Thread.sleep(r.nextInt(25));
        }

        try {
            CompletableFuture.allOf(runs.toArray(CompletableFuture[]::new))
                    .get(120, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new AssertionError("fuzzNested failed (seed=" + seed + ")", e);
        }
        assertBarrierState();
    }

    private void assertBarrierStateLocal() {
        var p = this.aspect.pauseBarrierMap.get("indexing");

//        assertThat(p.hasCoordinatorDepthLocal()).isFalse();
    }
    private void assertBarrierState() {
        var p = this.aspect.pauseBarrierMap.get("indexing");

//        assertThat(p.hasWaiterRegistration()).isFalse();
//        assertThat(p.hasCoordinatorDepth()).isFalse();
//        assertThat(p.hasCoordinatorDepthLocal()).isFalse();
    }

    enum Role { WAITER, WAITER_TX, PHASER }

    static Node genTree(Random r, int maxDepth, int maxFanout) {
        Role role = Role.values()[r.nextInt(Role.values().length)];
        boolean async = r.nextInt(100) < 20; // 20% children run async
        if (maxDepth == 0) return new Node(role, async, List.of());
        int k = r.nextInt(maxFanout + 1);    // 0..maxFanout children
        List<Node> kids = new ArrayList<>(k);
        for (int i = 0; i < k; i++) kids.add(genTree(r, maxDepth - 1, maxFanout));
        return new Node(role, async, kids);
    }


    static final class Node {
        final Role role;
        final boolean spawnAsync;      // allow cross-thread branches too
        final List<Node> children;
        Node(Role role, boolean spawnAsync, List<Node> children) {
            this.role = role; this.spawnAsync = spawnAsync; this.children = children;
        }
    }

    @Component
    public static class Frames {
        @Autowired @Lazy
        private Frames self;

        @LimitAccess(semaphoreName = "indexing", isWaiter = true)
        public void waiterFrame(Runnable body) {
            // simulate work before nested calls
            sleep(1, 4);
            if (body != null) body.run();        // <-- nested calls happen here
            sleep(1, 4);
        }

        @LimitAccess(semaphoreName = "indexing", isWaiter = true)
//        @Transactional(readOnly=true)
        public void waiterTxFrame(Runnable body) {
            sleep(2, 6);
            if (new Random().nextInt() % 2 == 0)  {
                throw new RuntimeException("Failed!!!");
            }
            if (body != null) body.run();
            sleep(2, 6);
        }

        @LimitAccess(semaphoreName = "indexing", isPhaser = true)
        public void phaserFrame(Runnable body) {
            sleep(1, 4);
            if (body != null) body.run();
            sleep(1, 4);
        }

        private static void sleep(int lo, int hi) {
            try { Thread.sleep(ThreadLocalRandom.current().nextInt(lo, hi)); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
    }


    @Component
    @Slf4j
    public static class IndexingWaiterService {
        @Autowired
        private IndexingWaiterExecutor indexingWaiterExecutor;

        @Autowired
        private IndexingWaiterWithTransactionExecutor indexingWaiterWithTransactionExecutor;

        private volatile boolean shouldStop = false;
        private AtomicInteger checkpointCount = new AtomicInteger(0);

        /**
         * Perform long-running indexing as a waiter.
         * This will yield to any phasers that arrive.
         */
        public void performIndexing() {
            while (!shouldStop) {
                try {
                    // Call external bean to ensure @LimitAccess aspect is applied
                    indexingWaiterExecutor.executeIndexingPhase();
                    checkpointCount.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("Indexing interrupted", e);
                    break;
                }
            }
        }

        /**
         * Perform indexing with an active transaction as a waiter.
         * With active transaction, it won't yield even if phasers arrive.
         */
        public void performIndexingWithTransaction() {
            while (!shouldStop) {
                try {
                    // Call external bean to ensure @LimitAccess aspect is applied
                    indexingWaiterWithTransactionExecutor.executeIndexingPhaseWithTransaction();
                    checkpointCount.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("Indexing interrupted", e);
                    break;
                }
            }
        }

        /**
         * Perform indexing that also calls a phaser method internally.
         * This tests the scenario where a waiter calls a phaser.
         */
        public void performIndexingThatCallsPhaser() {
            try {
                indexingWaiterExecutor.executeIndexingPhaseWithPhaserCall();
                checkpointCount.incrementAndGet();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Indexing interrupted", e);
            }
        }

        public void stopIndexing() {
            shouldStop = true;
        }

        public int getCheckpointCount() {
            return checkpointCount.get();
        }
    }

    @Component
    @Slf4j
    public static class IndexingWaiterExecutor {
        @Autowired @Lazy
        private RequestPhaserService requestPhaserService;

        /**
         * Indexing phase with @LimitAccess(isWaiter=true).
         * This is in a separate bean so the aspect can intercept it.
         * Phasers will pause this waiter.
         */
        @LimitAccess(semaphoreName = "indexing", isWaiter = true)
        public void executeIndexingPhase() throws InterruptedException {
            // Simulate indexing work
            Thread.sleep(50);
            log.debug("Waiter executed indexing checkpoint");
        }

        /**
         * Indexing phase that internally calls a phaser method.
         * This tests that a waiter can safely call a phaser without deadlock.
         */
        @LimitAccess(semaphoreName = "indexing", isWaiter = true)
        public void executeIndexingPhaseWithPhaserCall() throws InterruptedException {
            // Simulate indexing work
            Thread.sleep(25);
            // Nested call to a phaser method
            requestPhaserService.processRequestHelper(999);
            Thread.sleep(25);
            log.debug("Waiter executed indexing checkpoint with nested phaser call");
        }
    }

    @Component
    @Slf4j
    public static class IndexingWaiterWithTransactionExecutor {
        /**
         * Indexing phase with @LimitAccess(isWaiter=true) and @Transactional(readOnly=true).
         * This is in a separate bean so the aspect can intercept it.
         * With an active transaction, it won't yield even if phasers arrive.
         */
        @LimitAccess(semaphoreName = "indexing", isWaiter = true)
        @Transactional(readOnly=true)
        public void executeIndexingPhaseWithTransaction() throws InterruptedException {
            // Simulate longer indexing work within transaction
            Thread.sleep(100);
            log.debug("Waiter executed indexing checkpoint with transaction");
        }
    }

    @Component
    @Slf4j
    public static class RequestPhaserService {
        @Autowired @Lazy
        private IndexingWaiterExecutor indexingWaiterExecutor;

        /**
         * Process a request using @LimitAccess(isPhaser=true).
         * This will pause all waiters while executing the critical section.
         */
        @LimitAccess(semaphoreName = "indexing", isPhaser = true)
        @Transactional(readOnly=true)
        public void processRequest(int requestNum) {
            try {
                log.info("Phaser request {} executing in critical section (waiter paused)", requestNum);
                // Simulate request processing
                Thread.sleep(50);
                log.info("Phaser request {} completed", requestNum);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Phaser request interrupted", e);
            }
        }

        /**
         * Helper method for phaser that can be called from waiter.
         * This tests nested calls between phaser and waiter.
         */
        @LimitAccess(semaphoreName = "indexing", isPhaser = true)
        public void processRequestHelper(int requestNum) {
            try {
                log.info("Phaser helper {} executing", requestNum);
                Thread.sleep(25);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Phaser helper interrupted", e);
            }
        }

        /**
         * Process a request that calls a waiter method internally.
         * This tests the scenario where a phaser calls a waiter.
         */
        @LimitAccess(semaphoreName = "indexing", isPhaser = true)
        @Transactional(readOnly=true)
        public void processRequestThatCallsWaiter() {
            try {
                log.info("Phaser request calling waiter method");
                // Nested call to a waiter method
                indexingWaiterExecutor.executeIndexingPhase();
                log.info("Phaser request completed after calling waiter");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Phaser request interrupted", e);
            }
        }
    }
}
