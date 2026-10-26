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

@Entity
@Table(name = "idempotency_key")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdempotencyRecord implements Persistable<String> {

    @Id
    @Column(name = "key", length = 128)
    private String key;

    @Column(nullable = false, length = 64)
    private String endpoint;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "status_code", nullable = false)
    private int statusCode;

    @Column(name = "response_body", nullable = false)
    private String responseBody;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public IdempotencyRecord(String key, String endpoint, String requestHash, int statusCode, String responseBody, Instant now) {
        this.key = key;
        this.endpoint = endpoint;
        this.requestHash = requestHash;
        this.statusCode = statusCode;
        this.responseBody = responseBody;
        this.createdAt = now;
    }

    public boolean sameRequest(String endpoint, String requestHash) {
        return this.endpoint.equals(endpoint) && this.requestHash.equals(requestHash);
    }

    // ids are assigned by us, so tell Spring Data this is an insert, not a merge:
    // a duplicate must fail on the primary key instead of overwriting the row
    @Transient
    private boolean persisted;

    @Override
    public String getId() {
        return key;
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
