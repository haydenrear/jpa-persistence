package com.hayden.persistence;

import com.hayden.persistence.models.JpaHibernateAuditedIded;
import com.hayden.persistence.recursive.one_to_many.OneToManyRecursive;
import com.hayden.persistence.recursive.one_to_one.OneToOneRecursive;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Entity
@Table(name = "test_jpa_entity_hibernate_many")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TestJpaEntityHibernateMany extends JpaHibernateAuditedIded {

    @Column
    String hello;

    @OneToManyRecursive(
            recursiveIdsQuery = """
                WITH recursive COMMIT_TREE(id, child_id, level) AS (
                      SELECT c.uuid, c.child_uuid, 1 as level FROM test_jpa_entity_hibernate_many c
                      WHERE c.uuid = ?1
                      UNION ALL
                      SELECT c.uuid, c.child_uuid, ct.level + 1
                      FROM test_jpa_entity_hibernate_many c
                      INNER JOIN ( select * from COMMIT_TREE ct ) ct
                      ON ct.child_id = c.uuid
                      LEFT JOIN ( select max(*) from COMMIT_TREE ct ) ct1
                      ON ct.child_id = c.uuid
                      WHERE ct.level = ct1.level
                )
                SELECT id, level FROM COMMIT_TREE
                """,
            subParentFieldName = "parent",
            subChildrenFieldName = "child"
    )
    @OneToMany
    @JoinColumn
    List<TestJpaEntityHibernateMany> child;

}
