package com.hayden.persistence.generator;

import com.hayden.persistence.TsidUtils;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.id.IdentifierGenerator;
import org.hibernate.id.enhanced.SequenceStyleGenerator;

public class TsidGenerator implements IdentifierGenerator {

    @Override
    public Object generate(SharedSessionContractImplementor session, Object object) {
        return TsidUtils.randomTsid().toLong();
    }
}