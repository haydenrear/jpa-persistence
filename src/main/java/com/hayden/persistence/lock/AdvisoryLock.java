package com.hayden.persistence.lock;

import com.zaxxer.hikari.HikariDataSource;
import org.postgresql.Driver;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.hayden.utilitymodule.db.DbDataSourceTrigger;
import jakarta.persistence.PersistenceException;
import lombok.extern.slf4j.Slf4j;
import org.intellij.lang.annotations.Language;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class AdvisoryLock {

    @Language("sql")
    public static final String LOCK_SQL = """
                SELECT pg_advisory_lock(hashtext('%s'));
            """;

    @Language("sql")
    public static final String UNLOCK_SQL = """
                SELECT pg_advisory_unlock(hashtext('%s'));
            """;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired(required = false)
    DbDataSourceTrigger trigger;

    public record DatabaseMetadata(String username, String password, String jdbcUrl) {}

    public Connection newIsolatedConnection(DataSource ds) throws SQLException {
        Optional<DatabaseMetadata> databaseMetadata = retrieveMetadata(ds);
        if (databaseMetadata.isEmpty()) {
            throw new SQLException("Could not load database metadata");
        }
        return newIsolatedConnection(databaseMetadata.get());
    }

    public Connection newIsolatedConnection(DatabaseMetadata metadata) throws SQLException {
        return DriverManager.getConnection(metadata.jdbcUrl, metadata.username, metadata.password);
    }

    public Optional<DatabaseMetadata> retrieveMetadata(DataSource dataSource) {
        if (dataSource instanceof AbstractRoutingDataSource a) {
            String key = trigger.currentKey();
            dataSource = a.getResolvedDataSources().get(key);
        }
        if (dataSource instanceof HikariDataSource h) {
            return Optional.of(new DatabaseMetadata(h.getUsername(), h.getPassword(), h.getJdbcUrl()));
        }

        return Optional.empty();
    }

    public <T> T doWithAdvisoryLock(Callable<T> toDo, String sessionId) {

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            log.error("❗ Spring transaction is active! Using manual connection with advisory lock may lead to inconsistent behavior.");
        }

        DataSource dataSource = jdbcTemplate.getDataSource();

        if (dataSource == null) {
            log.error("Could not get data source");
            return null;
        }

        try (var cxn = newIsolatedConnection(dataSource);
             var ds = new SingleConnectionDataSource(cxn, false)) {
            var jdbc = new JdbcTemplate(ds);
            try {
                doLock(sessionId, jdbc);
                var called = toDo.call();
                return called;
            } finally {
                doTryClose(sessionId, jdbc);
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }

        return null;
    }

    public <T> T doWithAdvisoryLock(Callable<T> toDo, String sessionId, String name) {
        if (name == null || trigger == null)
            return doWithAdvisoryLock(toDo, sessionId);
        else {
            return trigger.doOnKey(key -> {
                key.setKey(name);
                return doWithAdvisoryLock(toDo, sessionId);
            });
        }
    }

    private void doTryClose(String sessionId, JdbcTemplate jdbc) {
        doUnlockRecursive(sessionId, jdbc);
    }

    public void doLock(String sessionId, JdbcTemplate template) {
        template.execute(LOCK_SQL.formatted(sessionId));
    }

    public void doUnlock(String sessionId, JdbcTemplate template) {
        template.execute(UNLOCK_SQL.formatted(sessionId));
    }

    public void printAdvisoryLocks(String key) {
        if (trigger == null || key == null) {
            printAdvisoryLocks();
        } else {
            trigger.doWithKey(sKey -> {
                sKey.setKey(key);
                printAdvisoryLocks();
            });
        }
    }

    public void printAdvisoryLocks() {
        var found = jdbcTemplate.query("""
            SELECT * FROM pg_locks WHERE locktype = 'advisory';
            """,
                (rs, rowNum) -> rs.getString("objid"));

        found.stream().filter(Objects::nonNull)
                .forEach(next -> log.info("Found next column advisory lock. {}", next));
    }

    public void scheduleAdvisoryLockLogger() {
        scheduleAdvisoryLockLogger(null);
    }

    public void scheduleAdvisoryLockLogger(String name) {
        Executors.newScheduledThreadPool(1)
                .scheduleAtFixedRate(
                        () -> printAdvisoryLocks(name),
                        1,
                        30,
                        TimeUnit.SECONDS);
    }

    private void doUnlockRecursive(String sessionDir, JdbcTemplate jdbc) {
        int num = 0;
        while (true) {
            try {
                doUnlock(sessionDir, jdbc);
                break;
            } catch (DataAccessException | PersistenceException e) {
                log.error("Failed to unlock session {} - retrying...", sessionDir, e);
                if (num > 10)
                    throw e;
                num += 1;
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ex) {
                    log.warn("Unlock retry loop interrupted for session {}", sessionDir);
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }
}
