package org.example.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

// one leg of a double-entry posting; the legs of a posting share (sourceType, sourceId)
@Entity
@Table(name = "ledger_entry")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LedgerEntry {

    public enum Type { DEBIT, CREDIT }

    public enum Source { OPENING, TRANSFER, REVERSAL, PAYOUT }

    // counterparty accounts that are not wallets
    public static final String BANK_ACCOUNT = "SYS:BANK";
    public static final String OPENING_ACCOUNT = "SYS:OPENING";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false, length = 32)
    private String accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 8)
    private Type type;

    @Column(nullable = false)
    private long amount;

    @Column(name = "balance_after")
    private Long balanceAfter;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 16)
    private Source sourceType;

    @Column(name = "source_id", nullable = false, length = 32)
    private String sourceId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    private LedgerEntry(String accountId, Type type, long amount, Long balanceAfter, Source sourceType, String sourceId, Instant now) {
        this.accountId = accountId;
        this.type = type;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.createdAt = now;
    }

    // call after the wallet balance has been changed, so balanceAfter is right
    public static LedgerEntry wallet(Wallet wallet, Type type, long amount, Source source, String sourceId, Instant now) {
        return new LedgerEntry(wallet.getId(), type, amount, wallet.getBalance(), source, sourceId, now);
    }

    public static LedgerEntry system(String accountId, Type type, long amount, Source source, String sourceId, Instant now) {
        return new LedgerEntry(accountId, type, amount, null, source, sourceId, now);
    }
}
