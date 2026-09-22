package com.goledger.web.dto;

import java.util.UUID;

public record BalanceResponse(UUID accountId, String currency, long balance) {}
