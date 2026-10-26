--liquibase formatted sql

--changeset mpesa:002-create-transfer
CREATE TABLE transfer (
    id              VARCHAR(32)  PRIMARY KEY,
    from_wallet     VARCHAR(32)  NOT NULL REFERENCES wallet (id),
    to_wallet       VARCHAR(32)  NOT NULL REFERENCES wallet (id),
    amount          BIGINT       NOT NULL,
    reference       VARCHAR(64)  NOT NULL,
    status          VARCHAR(16)  NOT NULL,
    reversal_id     VARCHAR(32),
    reversal_reason VARCHAR(255),
    reversed_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
--rollback DROP TABLE transfer;
