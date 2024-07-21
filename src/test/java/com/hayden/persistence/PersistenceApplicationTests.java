package com.hayden.persistence;

import com.google.common.collect.Sets;
import com.hayden.persistence.config.TestJpaConfig;
import com.hayden.persistence.models.JpaInitIded;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactoryBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@ExtendWith(SpringExtension.class)
@ActiveProfiles("test")
@ContextConfiguration(classes = TestJpaConfig.class)
class PersistenceApplicationTests {

    @Autowired
    private TestRepository testRepository;

    @Test
    void contextLoads() {
        var t = assertDoesNotThrow(() ->  testRepository.save(new TestJpaEntityHibernate()));
        assertThat(t.getUuid()).isNotNull();
        var again = assertDoesNotThrow(() ->  testRepository.save(new TestJpaEntityHibernate()));
        assertThat(again.getUuid()).isNotNull();

        assertThat(t).isNotEqualTo(again);
        var s = Sets.newHashSet(t, again);
        assertThat(s.size()).isEqualTo(2);
        s = Sets.newHashSet(t);
        assertThat(s.size()).isEqualTo(1);
        s.add(again);
        assertThat(s.size()).isEqualTo(2);

    }

}
