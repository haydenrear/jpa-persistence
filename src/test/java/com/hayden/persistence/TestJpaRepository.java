package com.hayden.persistence;

import com.hayden.persistence.models.JpaInitIded;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TestJpaRepository extends JpaRepository<JpaInitIded, Long> {
}
