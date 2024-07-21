package com.hayden.persistence;

import com.hayden.persistence.models.JpaInitIded;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table
public class TestJpaEntity extends JpaInitIded {

    @Column
    String hello;



}
