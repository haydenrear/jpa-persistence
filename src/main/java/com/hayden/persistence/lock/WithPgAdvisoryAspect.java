package com.hayden.persistence.lock;

import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
@RequiredArgsConstructor
public class WithPgAdvisoryAspect {


    private final AdvisoryLock advisoryLock;

    @Around("@annotation(advisory)")
    public Object around(ProceedingJoinPoint joinPoint, WithPgAdvisory advisory) throws Throwable {
        var a = joinPoint.getArgs();

        int lockIndex = advisory.lockArg();
        if (lockIndex >= a.length) {
            throw new RuntimeException("Could not lock on advisory where arg index doesn't match args.");
        }

        if (a[lockIndex] == null) {
            throw new NullPointerException("Lock arg did not have valid lock key!");
        }

        String lockPath = a[lockIndex].toString();

        return advisoryLock.doWithAdvisoryLock(() -> {
            try {
                var ret = joinPoint.proceed(joinPoint.getArgs());
                return ret;
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }, lockPath);

    }

}
