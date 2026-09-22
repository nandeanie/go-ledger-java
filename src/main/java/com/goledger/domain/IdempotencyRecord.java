package com.goledger.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Records that a given (tenant, idempotency key) pair has already produced a
 * transaction, so a retried request returns the original result instead of
 * posting twice.
 */
@Entity
@Table(name = "idempotency_records",
       uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "idempotency_key"}))
public class IdempotencyRecord {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected IdempotencyRecord() {}

    public IdempotencyRecord(UUID tenantId, String idempotencyKey, UUID transactionId) {
        this.tenantId = tenantId;
        this.idempotencyKey = idempotencyKey;
        this.transactionId = transactionId;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public UUID getTransactionId() { return transactionId; }
    public Instant getCreatedAt() { return createdAt; }
}
