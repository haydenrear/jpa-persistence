package com.hayden.persistence.models;

import java.util.Objects;

public interface EqualsAndHashCodeId<T> {

    T equalsAndHashCodeId();

    default boolean equalsId(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass())
            return false;
        if (this.equalsAndHashCodeId() == null)
            throw new RuntimeException("Cannot run equals without initializing entity!");

        EqualsAndHashCodeId<T> jpaHibernateIded = (EqualsAndHashCodeId<T>) o;

        if (jpaHibernateIded.equalsAndHashCodeId() == null)
            throw new RuntimeException("Cannot run equals without initializing entity!");

        return Objects.equals(equalsAndHashCodeId(), jpaHibernateIded.equalsAndHashCodeId());
    }

    default int hashCodeId() {
        if (equalsAndHashCodeId() == null)
            throw new RuntimeException("Cannot compute hashcode for JPA entity that does not have UUID.");
        return Objects.hashCode(equalsAndHashCodeId());
    }


}
