package com.hayden.persistence.trigger;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "test_entity_two")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TestEntityTwo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String value;

    @Column
    private Integer count;

    public TestEntityTwo(String value, Integer count) {
        this.value = value;
        this.count = count;
    }
}
