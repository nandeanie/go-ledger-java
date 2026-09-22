package com.goledger.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * An API key is stored only as a SHA-256 hash of the plaintext secret.
 * The plaintext is shown to the caller exactly once, at issue time.
 */
@Entity
@Table(name = "api_keys")
public class ApiKey {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String name;

    @Column(name = "key_hash", nullable = false, unique = true)
    private String keyHash;

    @Column(name = "key_prefix", nullable = false)
    private String keyPrefix;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "api_key_scopes", joinColumns = @JoinColumn(name = "api_key_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "scope")
    private Set<ApiKeyScope> scopes = new HashSet<>();

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected ApiKey() {}

    public ApiKey(UUID tenantId, String name, String keyHash, String keyPrefix, Set<ApiKeyScope> scopes) {
        this.tenantId = tenantId;
        this.name = name;
        this.keyHash = keyHash;
        this.keyPrefix = keyPrefix;
        this.scopes = scopes;
    }

    public boolean isActive() {
        if (revokedAt != null) return false;
        if (expiresAt != null && expiresAt.isBefore(Instant.now())) return false;
        return true;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getName() { return name; }
    public String getKeyHash() { return keyHash; }
    public String getKeyPrefix() { return keyPrefix; }
    public Set<ApiKeyScope> getScopes() { return scopes; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public void setRevokedAt(Instant revokedAt) { this.revokedAt = revokedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
