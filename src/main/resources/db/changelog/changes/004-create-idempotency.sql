--liquibase formatted sql

--changeset mpesa:004-create-idempotency
CREATE TABLE idempotency_key (
    key           VARCHAR(128) PRIMARY KEY,
    endpoint      VARCHAR(64)  NOT NULL,
    request_hash  VARCHAR(64)  NOT NULL,
    status_code   INT          NOT NULL,
    response_body TEXT         NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
--rollback DROP TABLE idempotency_key;
