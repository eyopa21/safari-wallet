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

import java.time.Instant;

@Entity
@Table(name = "transfer")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Transfer implements Persistable<String> {

    @Id
    @Column(length = 32)
    private String id;

    @Column(name = "from_wallet", nullable = false, length = 32)
    private String fromWallet;

    @Column(name = "to_wallet", nullable = false, length = 32)
    private String toWallet;

    @Column(nullable = false)
    private long amount;

    @Column(nullable = false, length = 64)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TransferStatus status;

    @Column(name = "reversal_id", length = 32)
    private String reversalId;

    @Column(name = "reversal_reason")
    private String reversalReason;

    @Column(name = "reversed_at")
    private Instant reversedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Transfer(String id, String fromWallet, String toWallet, long amount, String reference, Instant now) {
        this.id = id;
        this.fromWallet = fromWallet;
        this.toWallet = toWallet;
        this.amount = amount;
        this.reference = reference;
        this.status = TransferStatus.COMPLETED;
        this.createdAt = now;
    }

    public boolean isCompleted() {
        return status == TransferStatus.COMPLETED;
    }

    public boolean isReversed() {
        return status == TransferStatus.REVERSED;
    }

    public void reverse(String reversalId, String reason, Instant now) {
        if (!isCompleted()) {
            throw new IllegalStateException("transfer " + id + " is " + status);
        }
        this.status = TransferStatus.REVERSED;
        this.reversalId = reversalId;
        this.reversalReason = reason;
        this.reversedAt = now;
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
