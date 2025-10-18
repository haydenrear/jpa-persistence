package com.hayden.persistence.db_pressure;


import com.hayden.utilitymodule.MapFunctions;
import com.hayden.utilitymodule.db.DbDataSourceTrigger;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
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

    private ConcurrentHashMap<String, Semaphore> semaphoreMap;

    @Autowired(required = false)
    DbDataSourceTrigger trigger;
    @Autowired(required = false)
    LimitAccessConfigProperties limitAccessConfigProperties = new  LimitAccessConfigProperties();

    @PostConstruct
    public void init() {
        semaphoreMap = MapFunctions.CollectMap(limitAccessConfigProperties.semaphores
                .entrySet()
                .stream()
                .map(s -> Map.entry(s.getKey(), new Semaphore(s.getValue().permits()))),
                ConcurrentHashMap::new);
    }

    public Semaphore retrieveSemaphore() {
        return semaphoreMap.compute(
                Optional.ofNullable(trigger)
                        .map(DbDataSourceTrigger::currentKey)
                        .orElse("SINGLE"),
                (key, prev) -> Optional.ofNullable(prev)
                        .orElse(new Semaphore(limitAccessConfigProperties.getMaxAccess())));
    }

    @Around("@annotation(limited)")
    public Object around(ProceedingJoinPoint joinPoint,
                         LimitAccess limited) throws Throwable {
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
