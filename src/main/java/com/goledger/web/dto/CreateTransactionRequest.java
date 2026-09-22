package com.goledger.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public record CreateTransactionRequest(
        @NotBlank String currency,
        String externalRef,
        Instant effectiveAt,
        @Valid @Size(min = 2, message = "a transaction needs at least 2 postings") List<PostingRequest> postings
) {}
