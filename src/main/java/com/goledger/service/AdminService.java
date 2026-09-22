package com.goledger.service;

import com.goledger.domain.*;
import com.goledger.exception.NotFoundException;
import com.goledger.exception.ValidationException;
import com.goledger.repository.ApiKeyRepository;
import com.goledger.repository.TenantRepository;
import com.goledger.security.ApiKeyHasher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AdminService {

    private final TenantRepository tenantRepository;
    private final ApiKeyRepository apiKeyRepository;

    public AdminService(TenantRepository tenantRepository, ApiKeyRepository apiKeyRepository) {
        this.tenantRepository = tenantRepository;
        this.apiKeyRepository = apiKeyRepository;
    }

    @Transactional
    public Tenant createTenant(String name) {
        return tenantRepository.save(new Tenant(name));
    }

    public Tenant getTenant(UUID id) {
        return tenantRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("tenant not found: " + id));
    }

    /**
     * Issues a new key and returns it together with its one-time plaintext secret.
     * The plaintext is never persisted or retrievable again after this call returns.
     */
    @Transactional
    public IssuedKey issueKey(UUID tenantId, String name, List<String> scopeNames, Integer expiresInDays) {
        getTenant(tenantId); // 404s if the tenant doesn't exist

        Set<ApiKeyScope> scopes = scopeNames.stream()
                .map(s -> {
                    try {
                        return ApiKeyScope.valueOf(s.trim().toUpperCase());
                    } catch (IllegalArgumentException e) {
                        throw new ValidationException("unknown scope: " + s);
                    }
                })
                .collect(Collectors.toSet());

        String plaintext = ApiKeyHasher.generatePlaintextKey();
        String hash = ApiKeyHasher.sha256Hex(plaintext);
        String prefix = ApiKeyHasher.prefix(plaintext);

        ApiKey apiKey = new ApiKey(tenantId, name, hash, prefix, scopes);
        if (expiresInDays != null) {
            apiKey.setExpiresAt(Instant.now().plus(expiresInDays, ChronoUnit.DAYS));
        }
        apiKeyRepository.save(apiKey);

        return new IssuedKey(apiKey, plaintext);
    }

    public boolean tenantHasAnyKey(UUID tenantId) {
        return apiKeyRepository.existsByTenantId(tenantId);
    }

    public record IssuedKey(ApiKey apiKey, String plaintextKey) {}
}
