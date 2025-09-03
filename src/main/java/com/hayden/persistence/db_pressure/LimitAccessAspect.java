package com.hayden.persistence.db_pressure;


import com.hayden.utilitymodule.db.DbDataSourceTrigger;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Phaser;
import java.util.concurrent.Semaphore;

/**
 * <p>
 * TODO: ??? Use the scoped variables with the router db-aware to add semaphore aspect, for ? package
 * </p>
 */
@Slf4j
@Aspect
@Component
public class LimitAccessAspect {

    private final ConcurrentHashMap<String, Semaphore> semaphoreMap = new ConcurrentHashMap<>();

    @Autowired(required = false)
    DbDataSourceTrigger trigger;
    // 6:15
    public Semaphore retrieveSemaphore() {
        return semaphoreMap.compute(
                Optional.ofNullable(trigger)
                        .map(DbDataSourceTrigger::currentKey)
                        .orElse("SINGLE"),
                (key, prev) -> Optional.ofNullable(prev)
                        .orElse(new Semaphore(170)));
    }

    @Around("@annotation(limited)")
    public Object around(ProceedingJoinPoint joinPoint, LimitAccess limited) throws Throwable {
        try {
            retrieveSemaphore().acquire();
            return joinPoint.proceed(joinPoint.getArgs());
        }  catch (InterruptedException e) {
            log.error("Interrupted while waiting for Semaphore to acquire - could not acquire semaphore.");
            return joinPoint.proceed(joinPoint.getArgs());
        } finally {
            retrieveSemaphore().release();
        }


    }


}
