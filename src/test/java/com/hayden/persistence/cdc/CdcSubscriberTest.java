package com.hayden.persistence.cdc;

import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@ActiveProfiles("testjpa")
class CdcSubscriberTest {

    public static AtomicReference<List<String>> ref = new AtomicReference<>(new ArrayList<>());

    public static CountDownLatch countDownLatch = new CountDownLatch(1);

    @SpringBootApplication
    @ComponentScan("com.hayden.persistence")
    public static class CdcSubscriberApplication {
        public static void main(String[] args) {
            SpringApplication.run(CdcSubscriberApplication.class, args);
        }

        @Component
        public static class CdcTestSubscriber implements CdcSubscriber {

            @Override
            public void onDataChange(String tableName, String operation, Map<String, Object> data) {
                ref.get().add(operation);
                countDownLatch.countDown();
            }

            @Override
            public String getSubscriptionName() {
                return "test_subscription";
            }
        }
    }



    @SneakyThrows
    @BeforeAll
    public static void beforeAnything() {
        Connection conn = DriverManager.getConnection("jdbc:postgresql://localhost:5489/postgres", "postgres", "postgres");
        PGConnection pgConn = conn.unwrap(org.postgresql.PGConnection.class);
        Statement stmt = conn.createStatement();
        stmt.execute("""
                CREATE TABLE IF NOT EXISTS my_table (hello bigint, goodbye varchar(9999));
                INSERT INTO my_table VALUES (1, 'test');
                """);
        String subscription = """
                CREATE OR REPLACE FUNCTION notify_trigger() RETURNS trigger AS
                $$
                BEGIN
                PERFORM pg_notify('test_subscription', row_to_json(NEW)::text);
                RETURN NEW;
                END;
                $$ LANGUAGE plpgsql;
                CREATE OR REPLACE TRIGGER my_trigger
                AFTER INSERT OR UPDATE
                ON my_table
                FOR EACH ROW
                EXECUTE FUNCTION notify_trigger();
                """;

        stmt.execute(subscription);
    }

    @Autowired
    private CdcSubscriber cdcSubscriber;
    @Autowired
    private CdcProcessor cdcProcessor;

    @SneakyThrows
    @Test
    public void doFind() {
        assertThat(cdcProcessor.subscriptionsActive()).contains("test_subscription");

        Connection conn = DriverManager.getConnection("jdbc:postgresql://localhost:5489/postgres", "postgres", "postgres");
        PGConnection pgConn = conn.unwrap(org.postgresql.PGConnection.class);
        Statement stmt = conn.createStatement();
        stmt.execute("""
                INSERT INTO my_table VALUES (2, 'another-test');
                """);

        assertThat(countDownLatch.await(10, TimeUnit.SECONDS)).isTrue();

        assertThat(ref.get().size()).isEqualTo(1);
    }

}