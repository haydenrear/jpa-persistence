package com.hayden.persistence.db_pressure;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "db")
@Component
@Data
public class LimitAccessConfigProperties {

    public record Semaphore(String name, int permits) {}

    Map<String, Semaphore> semaphores = new HashMap<>();

    int maxAccess = 170;

    boolean enable;

}
