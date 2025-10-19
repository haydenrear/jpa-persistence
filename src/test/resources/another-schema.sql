-- Schema initialization for another datasource
DROP TABLE IF EXISTS test_entity_two CASCADE;

CREATE TABLE test_entity_two (
    id BIGSERIAL PRIMARY KEY,
    value VARCHAR(255) NOT NULL,
    count INTEGER
);
