package com.goledger.web;

import com.goledger.domain.ApiKeyScope;
import com.goledger.domain.Tenant;
import com.goledger.security.AuthContext;
import com.goledger.service.AdminService;
import com.goledger.web.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @PostMapping("/tenants")
    @ResponseStatus(HttpStatus.CREATED)
    public TenantResponse createTenant(@Valid @RequestBody CreateTenantRequest request) {
        AuthContext.current().requireScope(ApiKeyScope.ADMIN);
        Tenant tenant = adminService.createTenant(request.name());
        return TenantResponse.from(tenant);
    }

    @GetMapping("/tenants/{id}")
    public TenantResponse getTenant(@PathVariable java.util.UUID id) {
        AuthContext.current().requireScope(ApiKeyScope.ADMIN);
        return TenantResponse.from(adminService.getTenant(id));
    }

    @PostMapping("/keys")
    @ResponseStatus(HttpStatus.CREATED)
    public IssueKeyResponse issueKey(@Valid @RequestBody IssueKeyRequest request) {
        AuthContext.current().requireScope(ApiKeyScope.ADMIN);
        AdminService.IssuedKey issued = adminService.issueKey(
                request.tenantId(), request.name(), request.scopes(), request.expiresInDays());
        return new IssueKeyResponse(
                issued.apiKey().getId(),
                issued.apiKey().getName(),
                issued.apiKey().getKeyPrefix(),
                issued.plaintextKey(),
                issued.apiKey().getScopes().stream().map(Enum::name).toList(),
                issued.apiKey().getExpiresAt()
        );
    }
}
