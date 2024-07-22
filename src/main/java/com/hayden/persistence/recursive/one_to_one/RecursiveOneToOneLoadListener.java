package com.hayden.persistence.recursive.one_to_one;

import com.hayden.utilitymodule.MapFunctions;
import io.micrometer.common.util.StringUtils;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Id;
import lombok.SneakyThrows;
import org.hibernate.event.spi.PostLoadEvent;
import org.hibernate.event.spi.PostLoadEventListener;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.sql.JDBCType;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class RecursiveOneToOneLoadListener implements PostLoadEventListener {

    @Autowired
    @Lazy
    JdbcTemplate jdbcTemplate;
    @Autowired
    @Lazy
    EntityManager entityManager;

    @SneakyThrows
    @Override
    public void onPostLoad(PostLoadEvent event) {
        Object entity = event.getEntity();
        for (var field : entity.getClass().getDeclaredFields()) {
            if (field.isAnnotationPresent(OneToOneRecursive.class)) {

                var idField = retrieveIdField(entity);
                var id = idField.get(entity);

                OneToOneRecursive customAnnotation = field.getAnnotation(OneToOneRecursive.class);
                String sql = customAnnotation.recursiveIdsQuery();
                var queries = jdbcTemplate.queryForList(sql, new Object[] {id}, Object.class);

                var ids = """
                        SELECT f FROM %s f
                        WHERE f.%s in ( %s )
                        """.formatted(entity.getClass().getSimpleName(), idField.getName(), queries.stream().map(Object::toString).collect(Collectors.joining(", ")));

                var q = MapFunctions.CollectMap(
                                entityManager.createQuery(ids, entity.getClass()).getResultList()
                                        .stream()
                                        .flatMap(o -> {
                                            try {
                                                Field declaredField = retrieveIdField(o);
                                                declaredField.trySetAccessible();
                                                return Stream.of(Map.entry(declaredField.get(o), o));
                                            } catch (IllegalAccessException e) {
                                                return Stream.empty();
                                            }
                                        })
                        );


                Object nextHead = entity;
                var commitIter = queries.iterator();

                Object commit;

                while(commitIter.hasNext()) {
                    commit = commitIter.next();
                    var nextCommit = q.get(commit);
                    var finalNextHead = nextHead;
                    if (nextCommit != null && finalNextHead != null) {
                        if (finalNextHead != nextCommit) {
                            doSetDeclaredField(finalNextHead, customAnnotation.subChildFieldName(), nextCommit);
                            doSetDeclaredField(nextCommit, customAnnotation.subParentFieldName(), finalNextHead);
                        }
                    }
                    nextHead = nextCommit;
                }

                System.out.println();

            }
        }
    }

    private static @NotNull Field retrieveIdField(Object entity) {
        var idFieldOpt = tryFindIdField(entity.getClass());
        if (idFieldOpt.isEmpty()) {
            idFieldOpt = tryFindIdField(entity.getClass().getSuperclass());
        }

        var idField = idFieldOpt.get();
        idField.trySetAccessible();
        return idField;
    }

    private static @NotNull Optional<Field> tryFindIdField(Class<?> aClass) {
        var idField = Arrays.stream(aClass.getDeclaredFields())
                .filter(f -> f.isAnnotationPresent(Id.class))
                .findAny();
        return idField;
    }

    private static void doSetDeclaredField(Object finalNextHead, String customAnnotation, Object nextCommit)  {
        try {
            Field declaredField = finalNextHead.getClass().getDeclaredField(customAnnotation);
            declaredField.trySetAccessible();
            declaredField.set(finalNextHead, nextCommit);
        } catch (NoSuchFieldException |
                 IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

}
