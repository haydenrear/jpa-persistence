package com.hayden.persistence.lock;

import com.hayden.utilitymodule.db.DbDataSourceTrigger;
import lombok.extern.slf4j.Slf4j;
import org.intellij.lang.annotations.Language;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class AdvisoryLock {

    @Autowired(required = false)
    DbDataSourceTrigger trigger;

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

    public void doLock(String sessionId) {
        jdbcTemplate.execute(LOCK_SQL.formatted(sessionId));
    }

    public void doUnlock(String sessionId) {
        jdbcTemplate.execute(UNLOCK_SQL.formatted(sessionId));
    }

    public void doLock(String sessionId, String key) {
        if (trigger != null) {
            trigger.doWithKey(sKey -> {
                sKey.setKey(key);
                doLock(sessionId);
            });
        } else {
            doLock(sessionId);
        }
    }

    public void doUnlock(String sessionId, String key) {
        if (trigger != null) {
            trigger.doWithKey(sKey -> {
                sKey.setKey(key);
                doUnlock(sessionId);
            });
        } else {
            doUnlock(sessionId);
        }
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
}
