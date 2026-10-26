--liquibase formatted sql

--changeset mpesa:001-create-wallet
CREATE TABLE wallet (
    id                VARCHAR(32)  PRIMARY KEY,
    status            VARCHAR(16)  NOT NULL,
    balance           BIGINT       NOT NULL DEFAULT 0,
    reserved          BIGINT       NOT NULL DEFAULT 0,
    status_reason     VARCHAR(255),
    status_changed_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- available (balance - reserved) can never go negative, whatever the code does
    CONSTRAINT ck_wallet_funds CHECK (reserved >= 0 AND balance >= reserved)
);
--rollback DROP TABLE wallet;
