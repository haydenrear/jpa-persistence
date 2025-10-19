-- Schema initialization for cdc-subscriber datasource
DROP TABLE IF EXISTS test_entity CASCADE;

CREATE TABLE test_entity (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(255)
);
