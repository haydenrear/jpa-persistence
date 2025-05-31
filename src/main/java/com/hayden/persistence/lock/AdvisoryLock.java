package com.hayden.persistence.lock;

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
import java.sql.SQLException;
import java.util.Objects;
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

    public <T> T doWithAdvisoryLock(Callable<T> toDo, String sessionId) {
        try {

            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                log.error("❗ Spring transaction is active! Using manual connection with advisory lock may lead to inconsistent behavior.");
            }

            DataSource dataSource = jdbcTemplate.getDataSource();
            if (dataSource == null) {
                log.error("Could not get data source");
                return null;
            }

            try(var cxn = dataSource.getConnection();
                var ds = new SingleConnectionDataSource(cxn, false)) {
                JdbcTemplate jdbc = null;
                try {
                    jdbc = new JdbcTemplate(ds);
                    doLock(sessionId, jdbc);
                    var called = toDo.call();
                    return called;
                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                } finally {
                    doTryClose(sessionId, jdbc);
                }
            }
        } catch (SQLException e) {
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
        if (jdbc != null)
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
        while (true) {
            try {
                doUnlock(sessionDir, jdbc);
                break;
            } catch (DataAccessException | PersistenceException e) {
                log.error("Failed to unlock session {} - retrying...", sessionDir, e);
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }
}
