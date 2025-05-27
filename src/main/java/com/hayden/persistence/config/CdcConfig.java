package com.hayden.persistence.config;

import com.google.common.collect.Lists;
import com.hayden.persistence.cdc.CdcSubscriber;
import com.hayden.utilitymodule.stream.StreamUtil;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import liquibase.database.jvm.JdbcConnection;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
@Slf4j
public class CdcConfig {

    @Bean
    @ConfigurationProperties("spring.datasource.cdc-subscriber")
    public DataSource cdcDataSource() {
        return DataSourceBuilder.create().build();
    }


}
