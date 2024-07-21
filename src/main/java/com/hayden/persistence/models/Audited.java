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
@Data
public class Audited {

    @Column(name = "created_time")
    @CreatedDate
    @EqualsAndHashCode.Exclude
    private LocalDateTime createdTime;

    @Column(name = "updated_time")
    @LastModifiedDate
    @EqualsAndHashCode.Exclude
    private LocalDateTime updatedTime;

    @Column(name = "created_by")
    @CreatedBy
    @EqualsAndHashCode.Exclude
    private LocalDateTime createdBy;

    @Column(name = "modified_by")
    @LastModifiedBy
    @EqualsAndHashCode.Exclude
    private LocalDateTime updatedBy;

}
