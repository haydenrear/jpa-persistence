package com.hayden.persistence.lock;

import com.hayden.utilitymodule.db.DbDataSourceTrigger;
import org.intellij.lang.annotations.Language;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
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
        if (trigger != null) {
            trigger.doWithKey(sKey -> {
                sKey.setKey("function-calling");
                jdbcTemplate.execute(LOCK_SQL.formatted(sessionId));
            });
        } else {
            jdbcTemplate.execute(LOCK_SQL.formatted(sessionId));
        }
    }

    public void doUnlock(String sessionId) {
        if (trigger != null) {
            trigger.doWithKey(sKey -> {
                sKey.setKey("function-calling");
                jdbcTemplate.execute(UNLOCK_SQL.formatted(sessionId));
            });
        } else {
            jdbcTemplate.execute(UNLOCK_SQL.formatted(sessionId));
        }
    }


}
