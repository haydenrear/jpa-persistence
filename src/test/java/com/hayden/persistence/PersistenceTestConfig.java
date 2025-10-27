package com.hayden.persistence;

import com.hayden.utilitymodule.otel.DisableOtelConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import(DisableOtelConfiguration.class)
public class PersistenceTestConfig {
}
