package com.goledger.repository;

import com.goledger.domain.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    Optional<Transaction> findByIdAndTenantId(UUID id, UUID tenantId);
    boolean existsByTenantIdAndExternalRef(UUID tenantId, String externalRef);
}
