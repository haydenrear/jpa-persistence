package com.hayden.persistence.cdc;

import com.google.common.collect.Lists;
import com.hayden.utilitymodule.db.DbDataSourceTrigger;
import com.hayden.utilitymodule.result.agg.AggregateParamError;
import com.hayden.utilitymodule.stream.StreamUtil;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.*;

@Service
@Slf4j
public class CdcProcessor {

    @Autowired(required = false)
    private List<CdcSubscriber> subscribers = new ArrayList<>();
    @Autowired
    private CdcConnectionExecutor executor;
    @Autowired
    private DbDataSourceTrigger dbTrigger;




    // Map of subscription name to list of subscribers
    private final Map<String, List<CdcSubscriber>> subscriptionMap =
            new ConcurrentHashMap<>();


    @PostConstruct
    public void initialize() {
        if (subscribers.isEmpty()) {
            log.info("No CDC subscribers found");
            return;
        }

        // Organize subscribers by subscription name
        for (CdcSubscriber subscriber : subscribers) {
            var subscriptionNames = subscriber.getSubscriptionName();
            subscriptionNames.forEach(subscriptionName -> {
                subscriptionMap.compute(subscriptionName, (key, prev) -> Optional.ofNullable(prev)
                        .map(c -> {
                            c.add(subscriber);
                            return c;
                        })
                        .orElseGet(() -> Lists.newArrayList(subscriber)));
                log.info(
                        "Registered CDC subscriber for subscription: {}",
                        subscriptionName);
            });

        }

        executor.initialize()
                .filterErr(AggregateParamError::isError)
                .doOnError(sa -> {
                    log.error("Error initializing CDC subscribers: {}", sa.getMessage());
                });

        var e = Executors.newScheduledThreadPool(5);

        e.scheduleAtFixedRate(() -> {
            dbTrigger.doWithKey(sKey -> {
                sKey.setKey("cdc-subscriber");
                executor.notifications()
                        .peekError(err -> {
                            if (err.isError())
                                log.error(err.getMessage());
                        })
                        .doOnEach(notification -> {
                            subscriptionMap.compute(notification.getName(), (key, prev) -> {
                                if (prev == null) {
                                    log.error("Received subscription for {} - but did not own subscriber.", key);
                                    return null;
                                }
                                prev.forEach(s -> s.getSubscriptionName()
                                        .forEach(subscriptionName -> s.onDataChange(subscriptionName, subscriptionName,
                                                Map.of(notification.getName(), notification.getParameter()))));
                                return prev;
                            });
                        });
            });


        }, 1, 1, TimeUnit.SECONDS);


    }

    public Set<String> subscriptionsActive() {
        return subscriptionMap.keySet();
    }


}
