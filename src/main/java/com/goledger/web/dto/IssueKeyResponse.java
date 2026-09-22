package com.goledger.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record IssueKeyResponse(
        UUID id,
        String name,
        String keyPrefix,
        String plaintextKey,
        List<String> scopes,
        Instant expiresAt
) {}
