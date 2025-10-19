package com.hayden.persistence.trigger;

import com.hayden.utilitymodule.db.DbDataSourceTrigger;
import com.hayden.utilitymodule.db.WithDbAspect;
import com.hayden.utilitymodule.otel.DisableOtelConfiguration;
import lombok.SneakyThrows;
import org.springframework.boot.actuate.autoconfigure.metrics.export.otlp.OtlpMetricsExportAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.tracing.otlp.OtlpAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.tracing.otlp.OtlpTracingAutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Configuration
@Profile("testjpa")
@Import({DbDataSourceTrigger.class, WithDbAspect.class, DisableOtelConfiguration.class})
@EnableJpaRepositories(basePackages = "com.hayden.persistence.trigger")
public class DataSourceTriggerConfig {

    @Bean
    @ConfigurationProperties("spring.datasource.cdc-subscriber")
    public DataSource cdcSubscriberDataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean
    public TestBeanTwo testBeanTwo() {
        return new TestBeanTwo();
    }

    @Bean
    @ConfigurationProperties("spring.datasource.cdc-server")
    public DataSource cdcServerDataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean
    @ConfigurationProperties("spring.datasource.another")
    public DataSource another() {
        return DataSourceBuilder.create().build();
    }

    @Bean(name = {"txManager", "transactionManager"})
    public PlatformTransactionManager txManager(DbDataSourceTrigger dbDataSourceTrigger) {
        return new DataSourceTransactionManager(abstractRoutingDataSource(dbDataSourceTrigger)); // IMPORTANT: routing DS here
    }

    @Bean
    @Primary
    public DataSource abstractRoutingDataSource(DbDataSourceTrigger dbDataSourceTrigger) {
        dbDataSourceTrigger.initializeKeyTo("cdc-subscriber");
        AbstractRoutingDataSource routingDataSource = new AbstractRoutingDataSource() {
            @Override
            protected Object determineCurrentLookupKey() {
                var curr = dbDataSourceTrigger.currentKey();
                return curr;
            }
        };

        Map<Object, Object> resolvedDataSources = new HashMap<>();
        resolvedDataSources.put("another", another());
        resolvedDataSources.put("cdc-server", cdcServerDataSource());
        resolvedDataSources.put("cdc-subscriber", cdcSubscriberDataSource());

        routingDataSource.setTargetDataSources(resolvedDataSources);
        routingDataSource.setDefaultTargetDataSource(cdcSubscriberDataSource());

        routingDataSource.afterPropertiesSet();

        // Initialize schemas
        initializeSchemas(resolvedDataSources);

        return routingDataSource;
    }

    @SneakyThrows
    private void initializeSchemas(Map<Object, Object> resolvedDataSources) {
        DataSource anotherDs = (DataSource) resolvedDataSources.get("cdc-server");
        executeSchemaScript(anotherDs, "another-schema.sql");

        // Initialize cdc-subscriber schema
        DataSource subscriberDs = (DataSource) resolvedDataSources.get("cdc-subscriber");
        executeSchemaScript(subscriberDs, "cdc-subscriber-schema.sql");
    }

    @SneakyThrows
    private void executeSchemaScript(DataSource dataSource, String scriptName) {
        try (Connection conn = dataSource.getConnection()) {
            String sql = readSqlScript(scriptName);
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(sql);
            }
        }
    }

    @SneakyThrows
    private String readSqlScript(String scriptName) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(this.getClass().getClassLoader().getResourceAsStream(scriptName)))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }

}
