package com.hayden.persistence.models;


import com.hayden.persistence.TsidUtils;
import jakarta.persistence.MappedSuperclass;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;


@MappedSuperclass
@NoArgsConstructor
@AllArgsConstructor
public class JpaInitIded extends Audited implements EqualsAndHashCodeId<Long>{

    @Id
    @jakarta.persistence.Id
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
