package com.goledger.web.dto;

import com.goledger.domain.Account;
import java.time.Instant;
import java.util.UUID;

public record AccountResponse(
        UUID id, String name, String type, String currency, UUID parentAccountId, Instant createdAt
) {
    public static AccountResponse from(Account a) {
        return new AccountResponse(a.getId(), a.getName(), a.getType().name(), a.getCurrency(),
                a.getParentAccountId(), a.getCreatedAt());
    }
}
