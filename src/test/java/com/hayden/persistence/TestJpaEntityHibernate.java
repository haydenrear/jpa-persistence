package com.hayden.persistence;

import com.hayden.persistence.models.JpaHibernateAuditedIded;
import com.hayden.persistence.recursive.one_to_one.OneToOneRecursive;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "test_jpa_entity_hibernate")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TestJpaEntityHibernate extends JpaHibernateAuditedIded {

    @Column
    String hello;

    @OneToOneRecursive(
            recursiveIdsQuery = """
                WITH recursive COMMIT_TREE AS (
                      SELECT c.uuid FROM test_jpa_entity_hibernate c
                      WHERE c.uuid = ?1
                      UNION ALL
                      SELECT c.uuid FROM test_jpa_entity_hibernate c
                      INNER JOIN test_jpa_entity_hibernate ct
                      ON ct.uuid = c.child_uuid
                )
                SELECT * FROM COMMIT_TREE
                """,
            subParentFieldName = "parent",
            subChildFieldName = "child"
    )
    @OneToOne(mappedBy = "parent")
    @JoinColumn(name = "child_uuid")
    TestJpaEntityHibernate child;

    @OneToOne
    @JoinColumn(name = "parent_uuid")
    TestJpaEntityHibernate parent;


}
