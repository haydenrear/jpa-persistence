package com.hayden.persistence.config;

import com.hayden.persistence.TestJpaEntityHibernate;
import com.hayden.persistence.TestRepository;
import com.hayden.persistence.recursive.one_to_many.RecursiveLoadListener;
import com.hayden.persistence.recursive.one_to_one.RecursiveOneToOneLoadListener;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManagerFactory;
import lombok.SneakyThrows;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@EnableJpaRepositories(basePackageClasses = TestRepository.class)
@EntityScan(basePackageClasses = TestJpaEntityHibernate.class)
@Configuration
@ComponentScan(basePackages = "com.hayden.persistence")
@EnableAutoConfiguration(exclude = LiquibaseAutoConfiguration.class)
public class TestJpaConfig {

    @SneakyThrows
    @Bean
    public CommandLineRunner set(EntityManagerFactory entityManagerFactory,
                                 RecursiveOneToOneLoadListener recursiveOneToOneLoadListener,
                                 RecursiveLoadListener recursiveLoadListener) {
        CommandLineRunner c =  cmd -> {
            var s = entityManagerFactory.unwrap(SessionFactoryImplementor.class);
            s.getServiceRegistry().getService(EventListenerRegistry.class)
                    .prependListeners(EventType.POST_LOAD, recursiveOneToOneLoadListener, recursiveLoadListener)
            ;
        };
        c.run();
        return c;
    }

}
