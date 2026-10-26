--liquibase formatted sql

--changeset mpesa:003-create-wallet-payout
CREATE TABLE wallet_payout (
    id              VARCHAR(32)  PRIMARY KEY,
    wallet_id       VARCHAR(32)  NOT NULL REFERENCES wallet (id),
    bank_account    VARCHAR(34)  NOT NULL,
    amount          BIGINT       NOT NULL,
    status          VARCHAR(16)  NOT NULL,
    state           VARCHAR(16)  NOT NULL,
    bank_ref        VARCHAR(32),
    conflict        BOOLEAN      NOT NULL DEFAULT FALSE,
    attempts        INT          NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    locked_until    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_wallet_payout_due ON wallet_payout (state, next_attempt_at);

CREATE TABLE bank_event (
    event_id    VARCHAR(64)  PRIMARY KEY,
    payout_id   VARCHAR(32)  NOT NULL,
    status      VARCHAR(16)  NOT NULL,
    received_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
--rollback DROP TABLE bank_event;
--rollback DROP TABLE wallet_payout;
