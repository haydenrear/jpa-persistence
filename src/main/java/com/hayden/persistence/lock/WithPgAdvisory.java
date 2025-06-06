package com.hayden.persistence.lock;

import java.lang.annotation.*;

/**
 * Must @Import({AdvisoryLock.class, WithPgAdvisoryAspect.class})
 * Then it locks the postgres DB with the string in the lockArg-th position arg as per AdvisoryLock.class.
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface WithPgAdvisory {

    int lockArg() default 0;

}
