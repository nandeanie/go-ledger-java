package com.goledger.web.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record CreateAccountRequest(
        @NotBlank String name,
        @NotBlank String type,
        @NotBlank String currency,
        UUID parentAccountId
) {}
