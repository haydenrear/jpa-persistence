package com.hayden.persistence.recursive.one_to_many;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OneToManyRecursive {

    String recursiveIdsQuery() default "";

    String subParentFieldName() default "";

    String subChildrenFieldName() default "";

}
