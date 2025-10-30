package com.hayden.persistence.db_pressure;


import com.hayden.utilitymodule.MapFunctions;
import com.hayden.utilitymodule.db.DbDataSourceTrigger;
import com.hayden.utilitymodule.db.WithDb;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

import static com.hayden.utilitymodule.reflection.ParameterAnnotationUtils.resolveAnnotationForMethod;

/**
 * <p>
 * Aspect that limits concurrent access to database operations using semaphores and pause barriers.
 * Supports two modes:
 * - Phaser: Short-lived, high-priority requests (e.g., user requests) that should pause waiters
 * - Waiter: Long-running operations (e.g., indexing) that should yield to phasers
 * <p>
 * The interaction between phasers and waiters:
 * - Waiters register/deregister with the PauseBarrier and call checkpoints periodically
 * - If a transaction is open, waiters complete it without yielding
 * - If no transaction is open, waiters yield to allow phasers to execute
 * - Phasers pause all waiters, execute their critical section, then resume waiters
 * </p>
 */
@Slf4j
@Aspect
@Component
// we need this to run before transaction annotation, so won't start transaction yet unless previous to this method.
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LimitAccessAspect {

    public static final String DEFAULT_SEMAPHORE_NAME = "default";

    public record ReentrantSemaphore(Semaphore semaphore, ThreadLocal<Integer> holds) {

        public ReentrantSemaphore(Semaphore semaphore) {
            this(semaphore, ThreadLocal.withInitial(() -> 0));
        }

        public void acquire() throws InterruptedException {
            int h = holds.get();
            if (h == 0) {
                // Only the outermost acquire touches the real semaphore
                semaphore.acquire();
            }
            holds.set(h + 1);
        }

        public void release() {
            int h = holds.get();
            if (h <= 0) {
                return;
            }
            int next = h - 1;
            if (next == 0) {
                holds.remove();
                semaphore.release();
            } else {
                holds.set(next);
            }
        }
    }

    ConcurrentHashMap<String, ReentrantSemaphore> semaphoreMap;
    ConcurrentHashMap<String, PauseBarrier> pauseBarrierMap;

    @Autowired(required = false)
    DbDataSourceTrigger trigger;
    @Autowired(required = false)
    LimitAccessConfigProperties limitAccessConfigProperties = new LimitAccessConfigProperties();

    @PostConstruct
    public void init() {
        semaphoreMap = MapFunctions.CollectMap(limitAccessConfigProperties.semaphores
                        .entrySet()
                        .stream()
                        .map(s -> Map.entry(s.getKey(), new ReentrantSemaphore(new Semaphore(s.getValue().permits(), true)))),
                ConcurrentHashMap::new);

        pauseBarrierMap = new ConcurrentHashMap<>();
    }

    @Pointcut("@annotation(com.hayden.persistence.db_pressure.LimitAccess)")
    public void withLimitAccess() {
    }

    @Around("withLimitAccess()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        Optional<LimitAccess> limitAccessOpt = resolveAnnotationForMethod(joinPoint, LimitAccess.class);

        if (limitAccessOpt.isEmpty()) {
            throw new IllegalArgumentException("Limit access did not contain any annotation");
        }

        var limited = limitAccessOpt.get();

        PauseBarrier pauseBarrier = retrievePauseBarrier(limited.semaphoreName());
        boolean isPhaser = limited.isPhaser();
        boolean isWaiter = limited.isWaiter();

        // Handle phaser (short-lived, high-priority request)
        if (isPhaser) {
            var p = handlePhaser(joinPoint, limited, pauseBarrier);
            return p;
        }

        // Handle waiter (long-running operation)
        if (isWaiter) {
            var w = handleWaiter(joinPoint, limited, pauseBarrier);
            return w;
        }

        var d = handleDefault(joinPoint, limited);

        return d;
    }

    /**
     * Handle phaser: pause all waiters, execute critical section, resume waiters
     */
    private Object handlePhaser(ProceedingJoinPoint joinPoint,
                                LimitAccess limited,
                                PauseBarrier pauseBarrier) throws Throwable {
        ReentrantSemaphore reentrantSemaphore = retrieveSemaphore(limited);
        try {
            reentrantSemaphore.acquire();
            var p = pauseBarrier.pauseWaitersAndRun(() -> {
                try {
                    return PauseBarrier.ResultOrExc.of(joinPoint.proceed(joinPoint.getArgs()));
                } catch (Throwable e) {
                    return PauseBarrier.ResultOrExc.err(e);
                }
            });

            if (p.isErr()) {
                throw p.cause();
            } else {
                return p.t();
            }
        } catch (InterruptedException e) {
            doInterrupt();
            return joinPoint.proceed(joinPoint.getArgs());
        } finally {
            reentrantSemaphore.release();
        }
    }

    /**
     * Handle waiter: register with pause barrier, call checkpoints periodically
     */
    private Object handleWaiter(ProceedingJoinPoint joinPoint,
                                LimitAccess limited,
                                PauseBarrier pauseBarrier) throws Throwable {
        ReentrantSemaphore reentrantSemaphore = retrieveSemaphore(limited);
        try {
            reentrantSemaphore.acquire();
            pauseBarrier.checkpointIfPaused();
            return joinPoint.proceed(joinPoint.getArgs());
        } catch (InterruptedException e) {
            doInterrupt();
            return joinPoint.proceed(joinPoint.getArgs());
        } finally {

            reentrantSemaphore.release();

        }
    }

    private static void logInterrupted(InterruptedException e) {
        log.error("Interrupted while waiting for Semaphore to acquire - could not acquire semaphore.", e);
    }

    /**
     * Default behavior: apply semaphore without pause barrier logic
     */
    private Object handleDefault(ProceedingJoinPoint joinPoint,
                                 LimitAccess limited) throws Throwable {
        ReentrantSemaphore reentrantSemaphore = retrieveSemaphore(limited);
        try {
            reentrantSemaphore.acquire();
            return joinPoint.proceed(joinPoint.getArgs());
        } catch (InterruptedException e) {
            doInterrupt();
            return joinPoint.proceed(joinPoint.getArgs());
        } finally {
            reentrantSemaphore.release();
        }
    }

    private static void doInterrupt() {
        log.error("Interrupted while waiting for Semaphore to acquire - could not acquire semaphore.");
        Thread.currentThread().interrupt();
    }

    public ReentrantSemaphore retrieveSemaphore(LimitAccess limitAccess) {
        if (!Objects.equals(limitAccess.semaphoreName(), DEFAULT_SEMAPHORE_NAME)) {
            return semaphoreMap.compute(limitAccess.semaphoreName(), (key, prev) -> {
                if (prev != null)
                    return prev;

                int access;
                if (!this.limitAccessConfigProperties.semaphores.containsKey(limitAccess.semaphoreName())) {
                    log.error("Limit access did not contain semaphore with key {}. Using default size of {}",
                            limitAccess.semaphoreName(), this.limitAccessConfigProperties.maxAccess);
                    access = this.limitAccessConfigProperties.maxAccess;
                } else {
                    access = this.limitAccessConfigProperties.semaphores.get(limitAccess.semaphoreName()).permits();
                }

                return new ReentrantSemaphore(new Semaphore(access, true));
            });
        }

        return semaphoreMap.compute(
                Optional.ofNullable(trigger)
                        .map(DbDataSourceTrigger::currentKey)
                        .orElse("default"),
                (key, prev) -> Optional.ofNullable(prev)
                        .orElseGet(() -> {
                            Semaphore semaphore = new Semaphore(limitAccessConfigProperties.getMaxAccess(), true);
                            return new ReentrantSemaphore(semaphore);
                        }));
    }

    /**
     * Retrieves or creates the PauseBarrier for the given semaphore name
     */
    public PauseBarrier retrievePauseBarrier(String semaphoreName) {
        return pauseBarrierMap.computeIfAbsent(semaphoreName, k -> new PauseBarrier());
    }

}
