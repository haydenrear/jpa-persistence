package com.hayden.persistence.cdc;

import com.hayden.utilitymodule.result.ManyResult;
import com.hayden.utilitymodule.result.Result;
import com.hayden.utilitymodule.result.agg.AggregateError;
import com.hayden.utilitymodule.result.error.SingleError;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

@Slf4j
@Component
public class CdcConnectionExecutor {

    @Autowired(required = false)
    private DataSource dataSource;
    @Autowired(required = false)
    private List<CdcSubscriber> subscribers = new ArrayList<>();

    @Value("${spring.datasource.password:postgres}")
    String databasePassword;
    @Value("${spring.datasource.username:postgres}")
    String databaseUsername;

    private PGConnection pgConn;
    private Connection conn;

    public Result<Boolean, AggregateError.StdAggregateError> initialize() {
        return refreshConnection();
    }

    private @NotNull Result<Boolean, AggregateError.StdAggregateError> refreshConnection() {
        Set<SingleError> errors = new HashSet<>();

        if (subscribers.isEmpty() || dataSource == null) {
            return Result.ok(true);
        }

        try {
            String url = dataSource.getConnection().getMetaData().getURL();
            conn = DriverManager.getConnection(url, databaseUsername, databasePassword);
            Statement stmt = conn.createStatement();
            pgConn = conn.unwrap(PGConnection.class);

            for (var subscriber : this.subscribers) {
                try {
                    stmt.execute("LISTEN " + subscriber.getSubscriptionName());
                } catch (SQLException e) {
                    errors.add(SingleError.fromE(e, "Failed to load subscriber %s".formatted(subscriber)));
                }
            }
        } catch (SQLException pExc) {
            return Result.err(new AggregateError.StandardAggregateError(SingleError.fromE(pExc, "Failed to load subscribers %s".formatted(subscribers))));
        }

        if (errors.isEmpty()) {
            return Result.ok(true);
        } else {
            return Result.from(true, new AggregateError.StandardAggregateError(errors));
        }
    }

    public ManyResult<PGNotification, SingleError> notifications() {
        try {
            if (this.conn == null || this.conn.isClosed()) {
                doPerformRefreshLogErr();
            }
            return getNotificationStream();
        } catch (SQLException e) {
            try {
                doPerformRefreshLogErr();
                return getNotificationStream();
            } catch (Exception exc) {
                logErrRefresh(exc.getMessage());
            }
            return Result.err(SingleError.fromE(e, "Failed to load notifications %s".formatted(e.getMessage())));
        }
    }

    private void doPerformRefreshLogErr() {
        this.refreshConnection().peekError(s -> {
            if (s.isError())
                logErrRefresh(s.getMessage());
        });
    }

    private static void logErrRefresh(String s) {
        log.error("Error refreshing connection {}", s);
    }

    private ManyResult<PGNotification, SingleError> getNotificationStream() throws SQLException {
        return Result.<PGNotification, SingleError>stream(Arrays.stream(this.pgConn.getNotifications()))
                .many();
    }

}
