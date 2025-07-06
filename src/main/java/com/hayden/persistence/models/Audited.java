package com.hayden.persistence.models;


import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.ExtensionMethod;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@MappedSuperclass
@Data
@EntityListeners(AuditingEntityListener.class)
public class Audited {

    @Column(name = "created_time")
    @CreatedDate
    @EqualsAndHashCode.Exclude
    @JsonIgnore
    private LocalDateTime createdTime;

    @Column(name = "updated_time")
    @LastModifiedDate
    @JsonIgnore
    @EqualsAndHashCode.Exclude
    private LocalDateTime updatedTime;

    @Column(name = "created_by")
    @CreatedBy
    @EqualsAndHashCode.Exclude
    @JsonIgnore
    private String createdBy;

    @Column(name = "modified_by")
    @LastModifiedBy
    @EqualsAndHashCode.Exclude
    @JsonIgnore
    private String updatedBy;

}
