package com.hayden.persistence.models;


import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.LocalDateTime;

@MappedSuperclass
@Data
public class JdbcAudited {

    @Column(name = "created_time")
    @CreatedDate
    @EqualsAndHashCode.Exclude
    LocalDateTime createdTime = LocalDateTime.now();
    @Column(name = "updated_time")
    @LastModifiedDate
    @EqualsAndHashCode.Exclude
    LocalDateTime updatedTime = LocalDateTime.now();
}
