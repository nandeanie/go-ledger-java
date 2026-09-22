package com.goledger.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "postings")
public class Posting {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", nullable = false)
    private Transaction transaction;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    /** Signed amount in minor currency units (e.g. cents). Debits and credits are just sign. */
    @Column(nullable = false)
    private long amount;

    private String description;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Posting() {}

    public Posting(UUID accountId, long amount, String description) {
        this.accountId = accountId;
        this.amount = amount;
        this.description = description;
    }

    void setTransaction(Transaction transaction) { this.transaction = transaction; }

    public UUID getId() { return id; }
    public Transaction getTransaction() { return transaction; }
    public UUID getAccountId() { return accountId; }
    public long getAmount() { return amount; }
    public String getDescription() { return description; }
    public Instant getCreatedAt() { return createdAt; }
}
