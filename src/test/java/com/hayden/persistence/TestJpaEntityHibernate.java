package com.hayden.persistence;

import com.hayden.persistence.models.JpaHibernateAuditedIded;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table
public class TestJpaEntityHibernate extends JpaHibernateAuditedIded {

    @Column
    String hello;



}
