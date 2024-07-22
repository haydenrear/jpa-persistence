package com.hayden.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TestManyRepo extends JpaRepository<TestJpaEntityHibernateMany, Long> {
}
