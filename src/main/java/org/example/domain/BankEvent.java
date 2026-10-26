package org.example.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

// one row per bank callback event_id, so duplicates are handled once
@Entity
@Table(name = "bank_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BankEvent implements Persistable<String> {

    @Id
    @Column(name = "event_id", length = 64)
    private String eventId;

    @Column(name = "payout_id", nullable = false, length = 32)
    private String payoutId;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    public BankEvent(String eventId, String payoutId, String status, Instant now) {
        this.eventId = eventId;
        this.payoutId = payoutId;
        this.status = status;
        this.receivedAt = now;
    }

    // ids are assigned by us, so tell Spring Data this is an insert, not a merge:
    // a duplicate must fail on the primary key instead of overwriting the row
    @Transient
    private boolean persisted;

    @Override
    public String getId() {
        return eventId;
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
