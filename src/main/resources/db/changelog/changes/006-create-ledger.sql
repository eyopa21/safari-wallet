--liquibase formatted sql

--changeset mpesa:006-create-ledger
-- Double-entry ledger. Every movement posts legs that sum to zero under one (source_type, source_id):
-- a transfer debits one wallet and credits the other, a payout debits the wallet and credits SYS:BANK,
-- an opening balance credits the wallet and debits SYS:OPENING. account_id is a wallet id or a SYS:* account.
CREATE TABLE ledger_entry (
    id            BIGSERIAL    PRIMARY KEY,
    account_id    VARCHAR(32)  NOT NULL,
    entry_type    VARCHAR(8)   NOT NULL,            -- DEBIT or CREDIT
    amount        BIGINT       NOT NULL CHECK (amount > 0),
    balance_after BIGINT       CHECK (balance_after >= 0),   -- wallet balance after this leg, null for SYS accounts
    source_type   VARCHAR(16)  NOT NULL,            -- OPENING, TRANSFER, REVERSAL, PAYOUT
    source_id     VARCHAR(32)  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_ledger_account ON ledger_entry (account_id, id);
CREATE INDEX idx_ledger_source ON ledger_entry (source_type, source_id);

-- opening legs so every seeded balance is explained by the ledger
INSERT INTO ledger_entry (account_id, entry_type, amount, balance_after, source_type, source_id)
SELECT id, 'CREDIT', balance, balance, 'OPENING', id FROM wallet WHERE balance > 0;
INSERT INTO ledger_entry (account_id, entry_type, amount, balance_after, source_type, source_id)
SELECT 'SYS:OPENING', 'DEBIT', balance, NULL, 'OPENING', id FROM wallet WHERE balance > 0;
--rollback DROP TABLE ledger_entry;
