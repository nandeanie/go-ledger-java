package com.goledger.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 3)
    private String currency;

    /** Optional caller-supplied reconciliation id, unique per tenant. */
    @Column(name = "external_ref")
    private String externalRef;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(name = "effective_at", nullable = false)
    private Instant effectiveAt;

    @Column(name = "reversal_of")
    private UUID reversalOf;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<Posting> postings = new ArrayList<>();

    protected Transaction() {}

    public Transaction(UUID tenantId, String currency, String externalRef, String idempotencyKey,
                        Instant effectiveAt, UUID reversalOf) {
        this.tenantId = tenantId;
        this.currency = currency;
        this.externalRef = externalRef;
        this.idempotencyKey = idempotencyKey;
        this.effectiveAt = effectiveAt;
        this.reversalOf = reversalOf;
    }

    public void addPosting(Posting posting) {
        posting.setTransaction(this);
        postings.add(posting);
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getCurrency() { return currency; }
    public String getExternalRef() { return externalRef; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Instant getEffectiveAt() { return effectiveAt; }
    public UUID getReversalOf() { return reversalOf; }
    public Instant getCreatedAt() { return createdAt; }
    public List<Posting> getPostings() { return postings; }
}
