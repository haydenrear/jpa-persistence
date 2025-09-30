package com.hayden.persistence.db_pressure;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "db")
@Component
@Data
public class LimitAccessConfigProperties {

    int maxAccess = 170;

}
