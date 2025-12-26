package com.hayden.persistence.trigger;

import com.hayden.utilitymodule.db.DbDataSourceTrigger;
import com.hayden.utilitymodule.db.WithDb;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@Component
@NoArgsConstructor
public class TestBeanTwo {

    @Autowired
    private DbDataSourceTrigger trigger;

    @Autowired
    private TestEntityTwoRepository testEntityTwoRepository;

    @WithDb("cdc-server")
//    @Transactional
    public void test() {
        var curr = trigger.currentKey();

        assertThat(curr).isEqualTo("cdc-server");

        // Create and save a test entity
        TestEntityTwo entity = new TestEntityTwo("TestValue", 42);
        TestEntityTwo saved = testEntityTwoRepository.saveAndFlush(entity);


        System.out.println("Saved TestEntityTwo with ID: " + saved.getId() + " in datasource: " + curr);
    }

}
