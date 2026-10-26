--liquibase formatted sql

--changeset mpesa:005-seed-wallets context:!test
INSERT INTO wallet (id, status, balance, reserved) VALUES
    ('W001', 'ACTIVE', 1000000, 0),
    ('W002', 'ACTIVE', 1000000, 0),
    ('W003', 'ACTIVE', 1000000, 0),
    ('W030', 'ACTIVE', 1000000, 0),
    ('M001', 'ACTIVE', 1000000, 0),
    ('M002', 'ACTIVE', 1000000, 0);
--rollback DELETE FROM wallet WHERE id IN ('W001','W002','W003','W030','M001','M002');
