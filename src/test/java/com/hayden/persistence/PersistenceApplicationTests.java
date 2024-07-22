package com.hayden.persistence;

import com.google.common.collect.Sets;
import com.hayden.persistence.config.TestJpaConfig;
import com.hayden.persistence.models.JpaInitIded;
import org.assertj.core.util.Lists;
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
    @Autowired
    private TestManyRepo testManyRepo;

    @Test
    void contextLoads() {
        var t = assertDoesNotThrow(() ->  testRepository.save(new TestJpaEntityHibernate()));
        assertThat(t.getUuid()).isNotNull();
        var again = assertDoesNotThrow(() ->  testRepository.save(new TestJpaEntityHibernate()));
        assertThat(again.getUuid()).isNotNull();
        var third = assertDoesNotThrow(() ->  testRepository.save(new TestJpaEntityHibernate()));
        assertThat(again.getUuid()).isNotNull();
        var fourth = assertDoesNotThrow(() ->  testRepository.save(new TestJpaEntityHibernate()));
        assertThat(fourth.getUuid()).isNotNull();
        var fifth = assertDoesNotThrow(() ->  testRepository.save(new TestJpaEntityHibernate()));
        assertThat(fifth.getUuid()).isNotNull();

        assertThat(t).isNotEqualTo(again);
        var s = Sets.newHashSet(t, again);
        assertThat(s.size()).isEqualTo(2);
        s = Sets.newHashSet(t);
        assertThat(s.size()).isEqualTo(1);
        s.add(again);
        assertThat(s.size()).isEqualTo(2);

        t.child = again;
        t = testRepository.save(t);
        again.parent = t;
        again = testRepository.save(again);
        again.child = third;
        again = testRepository.save(again);
        third.parent = again;
        third = testRepository.save(third);
        third.child = fourth;
        third = testRepository.save(third);
        fourth.parent = third;
        testRepository.save(fourth);
        fifth.parent = fourth;
        testRepository.save(fifth);

        var found=testRepository.findAll();

        System.out.println(found);

    }

    @Test
    void testMany() {
        var t = assertDoesNotThrow(() ->  testManyRepo.save(new TestJpaEntityHibernateMany()));
        assertThat(t.getUuid()).isNotNull();
        var again = assertDoesNotThrow(() ->  testManyRepo.save(new TestJpaEntityHibernateMany()));
        assertThat(again.getUuid()).isNotNull();
        var third = assertDoesNotThrow(() ->  testManyRepo.save(new TestJpaEntityHibernateMany()));
        assertThat(again.getUuid()).isNotNull();
        var fourth = assertDoesNotThrow(() ->  testManyRepo.save(new TestJpaEntityHibernateMany()));
        assertThat(fourth.getUuid()).isNotNull();
        var fifth = assertDoesNotThrow(() ->  testManyRepo.save(new TestJpaEntityHibernateMany()));
        assertThat(fifth.getUuid()).isNotNull();

        assertThat(t).isNotEqualTo(again);
        var s = Sets.newHashSet(t, again);
        assertThat(s.size()).isEqualTo(2);
        s = Sets.newHashSet(t);
        assertThat(s.size()).isEqualTo(1);
        s.add(again);
        assertThat(s.size()).isEqualTo(2);

        t.child = Lists.newArrayList(again);
        t = testManyRepo.save(t);
        again = testManyRepo.save(again);
        again.child = Lists.newArrayList(third);
        again = testManyRepo.save(again);
        third = testManyRepo.save(third);
        third.child = Lists.newArrayList(fourth, fifth);
        third = testManyRepo.save(third);
        testManyRepo.save(fourth);
        testManyRepo.save(fifth);

        var found=testManyRepo.findAll();

        System.out.println(found);

    }
}
