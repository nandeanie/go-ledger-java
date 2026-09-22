package com.goledger.web.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record PostingRequest(@NotNull UUID accountId, @NotNull Long amount, String description) {}
