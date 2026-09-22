package com.goledger.web.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateTenantRequest(@NotBlank String name) {}
