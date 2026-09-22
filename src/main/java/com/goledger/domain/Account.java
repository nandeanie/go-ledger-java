package com.goledger.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountType type;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "parent_account_id")
    private UUID parentAccountId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Account() {}

    public Account(UUID tenantId, String name, AccountType type, String currency, UUID parentAccountId) {
        this.tenantId = tenantId;
        this.name = name;
        this.type = type;
        this.currency = currency;
        this.parentAccountId = parentAccountId;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getName() { return name; }
    public AccountType getType() { return type; }
    public String getCurrency() { return currency; }
    public UUID getParentAccountId() { return parentAccountId; }
    public Instant getCreatedAt() { return createdAt; }
}
