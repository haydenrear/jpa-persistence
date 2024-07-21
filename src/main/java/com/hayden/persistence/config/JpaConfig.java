package com.hayden.persistence.config;

import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.util.Optional;

@EnableJpaAuditing
public class JpaConfig {

    AuditorAware<String> auditorAware() {
        return () -> Optional.empty();
    }

}
