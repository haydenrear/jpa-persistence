package com.hayden.persistence;

import com.hayden.persistence.models.JpaInitIded;
import com.hayden.persistence.recursive.one_to_one.OneToOneRecursive;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table
public class TestJpaEntity extends JpaInitIded {

    @Column
    String hello;


}
