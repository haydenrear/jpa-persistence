package com.hayden.persistence.lock;

import java.lang.annotation.*;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface WithPgAdvisory {

    int lockArg() default 0;

}
