package com.hayden.persistence.cdc;

import com.hayden.utilitymodule.result.ManyResult;
import com.hayden.utilitymodule.result.Result;
import com.hayden.utilitymodule.result.agg.AggregateError;
import com.hayden.utilitymodule.result.error.SingleError;
import com.hayden.utilitymodule.stream.StreamUtil;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

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

    @Value("${spring.datasource.cdc-subscriber.password:postgres}")
    String databasePassword;
    @Value("${spring.datasource.cdc-subscriber.username:postgres}")
    String databaseUsername;


    private PGConnection pgConn;
    private Connection conn;

    public Result<Boolean, AggregateError.StdAggregateError> initialize() {

        var found = Result.<CdcSubscriber, SingleError>stream(subscribers.stream())
                .flatMapResult(c -> Result.fromOpt(c.createSubscription()))
                .flatMapResult(this::executeDdl)
                .filterErr(SingleError::isError)
                .toList();

        var toRefresh = refreshConnection();

        if (toRefresh.isError())
            throw new RuntimeException("Failed to initialize with err %s".formatted(toRefresh.errorMessage()));
        else if (!found.errsList().isEmpty()) {
            return Result.from(true, new AggregateError.StandardAggregateError(new HashSet<>(found.errsList())));
        }

        return toRefresh;
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
                for (var sName : subscriber.getSubscriptionName()) {
                    try {
                        stmt.execute("LISTEN " + sName);
                    } catch (SQLException e) {
                        errors.add(SingleError.fromE(e, "Failed to load subscriber %s".formatted(subscriber)));
                    }
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

    public Result<Boolean, SingleError> executeDdl(String toExec) {
        return Result.<DataSource, SingleError>fromOpt(Optional.ofNullable(dataSource))
                .flatMapResult(ds -> {
                    try {
                        ds.getConnection().createStatement().execute(toExec);
                        return Result.ok(true);
                    } catch (Exception e) {
                        return Result.err(SingleError.fromE(e, "Failed to execute DDL %s".formatted(toExec)));
                    }
                });
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
