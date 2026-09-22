package com.goledger.repository;

import com.goledger.domain.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, UUID> {
    Optional<IdempotencyRecord> findByTenantIdAndIdempotencyKey(UUID tenantId, String idempotencyKey);
}
