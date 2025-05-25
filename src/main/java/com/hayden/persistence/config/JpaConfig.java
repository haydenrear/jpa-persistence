package com.hayden.persistence.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.util.Optional;

@EnableJpaAuditing(auditorAwareRef = "auditorAware")
@Configuration
public class JpaConfig {

    @Bean
    AuditorAware<String> auditorAware() {
        return () -> Optional.of("HAYDEN");
    }

}
