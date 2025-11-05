package com.hayden.persistence.db_pressure;

import java.lang.annotation.*;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface LimitAccess {

    String semaphoreName() default "default";

    boolean isPhaser() default false;

    boolean isWaiter() default false;

    boolean skipIfNotAvailable() default false;

}
