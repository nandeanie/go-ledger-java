package com.goledger.repository;

import com.goledger.domain.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {
    Optional<Account> findByIdAndTenantId(UUID id, UUID tenantId);
    List<Account> findAllByTenantId(UUID tenantId);
    List<Account> findAllByParentAccountId(UUID parentAccountId);
}
