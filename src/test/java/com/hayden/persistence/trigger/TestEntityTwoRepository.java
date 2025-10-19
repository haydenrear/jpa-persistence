package com.hayden.persistence.trigger;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TestEntityTwoRepository extends JpaRepository<TestEntityTwo, Long> {
}
