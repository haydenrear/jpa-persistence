package com.hayden.persistence.models;


import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.LocalDateTime;

@MappedSuperclass
public abstract class AuditedEntity<ID> extends Audited implements EqualsAndHashCodeId<ID> {

    @Override
    public boolean equals(Object o) {
        return equalsId(o);
    }

    @Override
    public int hashCode() {
        return hashCodeId();
    }

}
