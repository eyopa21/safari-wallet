package org.example.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

import java.time.Duration;
import java.time.Instant;

@Entity
@Table(name = "wallet_payout")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WalletPayout implements Persistable<String> {

    @Id
    @Column(length = 32)
    private String id;

    @Column(name = "wallet_id", nullable = false, length = 32)
    private String walletId;

    @Column(name = "bank_account", nullable = false, length = 34)
    private String bankAccount;

    @Column(nullable = false)
    private long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private WalletPayoutStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private WalletPayoutState state;

    @Column(name = "bank_ref", length = 32)
    private String bankRef;

    @Column(nullable = false)
    private boolean conflict;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public WalletPayout(String id, String walletId, String bankAccount, long amount, Instant now) {
        this.id = id;
        this.walletId = walletId;
        this.bankAccount = bankAccount;
        this.amount = amount;
        this.status = WalletPayoutStatus.PENDING;
        this.state = WalletPayoutState.ACCEPTED;
        this.nextAttemptAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public boolean isDone() {
        return state == WalletPayoutState.DONE;
    }

    public void lock(Instant until) {
        lockedUntil = until;
    }

    public void submitted(String bankRef, Instant now, Duration pollAfter) {
        this.state = WalletPayoutState.SUBMITTED;
        this.bankRef = bankRef;
        this.attempts = 0;
        this.nextAttemptAt = now.plus(pollAfter);
        this.lockedUntil = null;
        this.updatedAt = now;
    }

    // bank wasn't reachable or isn't done yet: try again later
    public void retryLater(Instant now, Duration after) {
        this.attempts++;
        this.nextAttemptAt = now.plus(after);
        this.lockedUntil = null;
        this.updatedAt = now;
    }

    // bank forgot about us (NOT_FOUND): submit again, the bank is idempotent on our id
    public void resubmit(Instant now) {
        this.state = WalletPayoutState.ACCEPTED;
        this.nextAttemptAt = now;
        this.lockedUntil = null;
        this.updatedAt = now;
    }

    public void finish(WalletPayoutStatus finalStatus, String bankRef, Instant now) {
        this.state = WalletPayoutState.DONE;
        this.status = finalStatus;
        if (bankRef != null) {
            this.bankRef = bankRef;
        }
        this.lockedUntil = null;
        this.updatedAt = now;
    }

    // a later signal disagreed with the recorded final status
    public void markConflict(Instant now) {
        this.conflict = true;
        this.updatedAt = now;
    }

    // ids are assigned by us, so tell Spring Data this is an insert, not a merge:
    // a duplicate must fail on the primary key instead of overwriting the row
    @Transient
    private boolean persisted;

    @Override
    public String getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return !persisted;
    }

    @PostPersist
    @PostLoad
    void markPersisted() {
        persisted = true;
    }
}
