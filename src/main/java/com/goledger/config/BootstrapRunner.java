package com.goledger.config;

import com.goledger.domain.ApiKeyScope;
import com.goledger.domain.Tenant;
import com.goledger.repository.TenantRepository;
import com.goledger.service.AdminService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Mirrors go-ledger's first-run behaviour: if no tenant exists yet, create a
 * "default" tenant and mint it an admin-scoped API key, logging the plaintext
 * exactly once. There is no other way to retrieve it after this - if it's
 * missed, issue a fresh one directly against the database (see README).
 */
@Component
public class BootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapRunner.class);

    private final TenantRepository tenantRepository;
    private final AdminService adminService;

    public BootstrapRunner(TenantRepository tenantRepository, AdminService adminService) {
        this.tenantRepository = tenantRepository;
        this.adminService = adminService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (tenantRepository.count() > 0) {
            return;
        }

        Tenant tenant = adminService.createTenant("default");
        AdminService.IssuedKey issued = adminService.issueKey(
                tenant.getId(), "bootstrap-admin", List.of(ApiKeyScope.ADMIN.name()), null);

        log.warn("=================================================================");
        log.warn(" First boot: created tenant '{}' ({})", tenant.getName(), tenant.getId());
        log.warn(" Bootstrap admin API key (save this now, it will not be shown again):");
        log.warn("   {}", issued.plaintextKey());
        log.warn(" Use it as: Authorization: Bearer {}", issued.plaintextKey());
        log.warn("=================================================================");
    }
}
