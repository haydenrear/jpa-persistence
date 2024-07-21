package com.hayden.persistence.config;

import com.hayden.persistence.TestJpaEntityHibernate;
import com.hayden.persistence.TestRepository;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@EnableJpaRepositories(basePackageClasses = TestRepository.class)
@EntityScan(basePackageClasses = TestJpaEntityHibernate.class)
@Configuration
@ComponentScan(basePackages = "com.hayden.persistence")
@EnableAutoConfiguration(exclude = LiquibaseAutoConfiguration.class)
public class TestJpaConfig {
}
