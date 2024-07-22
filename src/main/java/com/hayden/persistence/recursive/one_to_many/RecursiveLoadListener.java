package com.hayden.persistence.recursive.one_to_many;

import com.google.common.collect.Lists;
import com.hayden.utilitymodule.MapFunctions;
import jakarta.persistence.EntityManager;
import lombok.SneakyThrows;
import org.hibernate.collection.spi.PersistentBag;
import org.hibernate.event.spi.PostLoadEvent;
import org.hibernate.event.spi.PostLoadEventListener;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.annotation.Id;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class RecursiveLoadListener implements PostLoadEventListener {

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
            if (field.isAnnotationPresent(OneToManyRecursive.class)) {

                var idField = retrieveIdField(entity);
                var id = idField.get(entity);

                OneToManyRecursive customAnnotation = field.getAnnotation(OneToManyRecursive.class);
                String sql = customAnnotation.recursiveIdsQuery();
                var queries = jdbcTemplate.query(sql, (rs, rowNum) -> new Object[] {rs.getString(1), rs.getString(2)}, id);

                var ids = """
                        SELECT f FROM %s f
                        WHERE f.%s in ( %s )
                        """.formatted(entity.getClass().getSimpleName(), idField.getName(), queries.stream().map(o -> o[0]).map(Object::toString).collect(Collectors.joining(", ")));

                List<?> resultList = entityManager.createQuery(ids, entity.getClass()).getResultList();

                var byLevel = MapFunctions.CollectMapGroupBy(queries.stream().map(e -> Map.entry(Integer.parseInt(String.valueOf(e[1])), e[0])));

                var q = MapFunctions.CollectMap(
                        resultList.stream()
                                .flatMap(o -> {
                                    try {
                                        var declaredField = retrieveIdField(o);
                                        declaredField.trySetAccessible();
                                        return Stream.of(Map.entry(declaredField.get(o).toString(), o));
                                    } catch (IllegalAccessException e) {
                                        return Stream.empty();
                                    }
                                })
                );

                var commitIter = byLevel.keySet().stream().sorted().toList().iterator();

                Object nextHead = entity;

                Object commit;

                while (commitIter.hasNext()) {
                    commit = commitIter.next();
                    List<Object> nextCommits = byLevel.get(commit);
                    for (var n : nextCommits) {
                        var nextCommit = q.get(n.toString());

                        var finalNextHead = nextHead;
                        if (nextCommit != null && finalNextHead != null) {
                            doSetDeclaredField(finalNextHead, customAnnotation.subChildrenFieldName(), nextCommit, event);
                            doSetDeclaredFieldOne(nextCommit, customAnnotation.subParentFieldName(), finalNextHead);
                        }
                        nextHead = nextCommit;
                    }
                }

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

    private static void doSetDeclaredField(Object finalNextHead, String customAnnotation, Object nextCommit, PostLoadEvent postLoadEvent) {
        try {
            Field declaredField = finalNextHead.getClass().getDeclaredField(customAnnotation);
            declaredField.trySetAccessible();
            var toGet = declaredField.get(finalNextHead);
            if (toGet == null) {
                declaredField.set(finalNextHead, Lists.newArrayList(nextCommit));
            }else if (toGet instanceof PersistentBag b) {
                if (b.isEmpty()) {
                    declaredField.set(finalNextHead, Lists.newArrayList(nextCommit));
                } else {
                    b.add(nextCommit) ;
                }
            } else if (toGet instanceof List l) {
                l.add(nextCommit);
            }
        } catch (NoSuchFieldException |
                 IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private static void doSetDeclaredFieldOne(Object finalNextHead, String customAnnotation, Object nextCommit)  {
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
