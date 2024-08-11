package com.hayden.persistence.examples;

import com.hayden.persistence.models.AuditedEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Array;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Type;
import org.hibernate.type.SqlTypes;

import java.util.List;

//@Entity
//@Table
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class CommitDiff  {


    @Column
    @Type(value = VectorType.class)
    private float[] embedding;
    @Id
    private Long id;


}
