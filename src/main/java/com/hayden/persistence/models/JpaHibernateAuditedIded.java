package com.hayden.persistence.models;


import com.hayden.persistence.generator.TsidGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.MappedSuperclass;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;
import org.springframework.data.annotation.Id;


@MappedSuperclass
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JpaHibernateAuditedIded extends Audited implements EqualsAndHashCodeId<Long> {

    @Id
    @jakarta.persistence.Id
    @GenericGenerator(name = "jpa_id_seq", type = TsidGenerator.class)
    @GeneratedValue(generator = "jpa_id_seq")
    @Column(name = "uuid")
    protected Long uuid;

    @Override
    public boolean equals(Object o) {
        return this.equalsId(o);
    }

    @Override
    public int hashCode() {
        return this.hashCodeId();
    }

    @Override
    public Long equalsAndHashCodeId() {
        return uuid;
    }
}
