package com.goledger.repository;

import com.goledger.domain.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {
    Optional<ApiKey> findByKeyHash(String keyHash);
    boolean existsByTenantId(UUID tenantId);
}
