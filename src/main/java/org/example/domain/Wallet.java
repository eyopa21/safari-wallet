package org.example.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "wallet")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Wallet {

    @Id
    @Column(length = 32)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private WalletStatus status;

    @Column(nullable = false)
    private long balance;

    @Column(nullable = false)
    private long reserved;

    @Column(name = "status_reason")
    private String statusReason;

    @Column(name = "status_changed_at", nullable = false)
    private Instant statusChangedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public long available() {
        return balance - reserved;
    }

    public boolean isActive() {
        return status == WalletStatus.ACTIVE;
    }

    public void debit(long amount) {
        check(available() >= amount, "insufficient funds");
        balance -= amount;
    }

    public void credit(long amount) {
        balance += amount;
    }

    public void reserve(long amount) {
        check(available() >= amount, "insufficient funds");
        reserved += amount;
    }

    // payout succeeded: the reserved money leaves the wallet
    public void capture(long amount) {
        check(reserved >= amount, "reserved below amount");
        reserved -= amount;
        balance -= amount;
    }

    // payout failed: the reserved money is available again
    public void release(long amount) {
        check(reserved >= amount, "reserved below amount");
        reserved -= amount;
    }
    // returns false when nothing changed
    public boolean changeStatus(WalletStatus newStatus, String reason, Instant now) {
        if (status == newStatus) {
            return false;
        }
        status = newStatus;
        statusReason = reason;
        statusChangedAt = now;
        return true;
    }

    private void check(boolean ok, String msg) {
        if (!ok) {
            throw new IllegalStateException("wallet " + id + ": " + msg);
        }
    }
}
