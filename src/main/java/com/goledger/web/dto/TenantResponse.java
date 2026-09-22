package com.goledger.web.dto;

import com.goledger.domain.Tenant;
import java.time.Instant;
import java.util.UUID;

public record TenantResponse(UUID id, String name, String status, Instant createdAt) {
    public static TenantResponse from(Tenant t) {
        return new TenantResponse(t.getId(), t.getName(), t.getStatus().name(), t.getCreatedAt());
    }
}
