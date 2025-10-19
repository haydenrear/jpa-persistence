package com.hayden.persistence.trigger;

import com.hayden.utilitymodule.db.DbDataSourceTrigger;
import com.hayden.utilitymodule.db.WithDb;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@Component
@RequiredArgsConstructor
public class TestBean {

    private final DbDataSourceTrigger trigger;

    private final TestBeanTwo two;

    private final TestEntityRepository testEntityRepository;

    private final DataSource abstractRoutingDataSource;

    @SneakyThrows
    @PostConstruct
    public void init() {
        trigger.doOnKey(sk -> {
            sk.setKey("cdc-subscriber");
            try (Connection conn = abstractRoutingDataSource.getConnection()) {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("""
                     DROP TABLE test_entity_two;
                     """);
                } catch (SQLException e) {
                    log.error(e.getMessage());
                }
            } catch (SQLException e) {
                log.error(e.getMessage());
            }
            return null;
        });
    }

    @WithDb("cdc-subscriber")
    @Transactional
    public void test() {
        var curr = trigger.currentKey();

        // Create and save a test entity
        TestEntity entity = new TestEntity("TestName", "Test Description");
        TestEntity saved = testEntityRepository.save(entity);

        assertThat(curr).isEqualTo("cdc-subscriber");

        System.out.println("Saved TestEntity with ID: " + saved.getId() + " in datasource: " + curr);

        two.test();
    }

}
