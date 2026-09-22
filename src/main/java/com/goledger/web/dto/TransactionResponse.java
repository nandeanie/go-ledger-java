package com.goledger.web.dto;

import com.goledger.domain.Transaction;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TransactionResponse(
        UUID id, String currency, String externalRef, Instant effectiveAt, Instant createdAt,
        UUID reversalOf, List<PostingResponse> postings
) {
    public static TransactionResponse from(Transaction t) {
        return new TransactionResponse(
                t.getId(), t.getCurrency(), t.getExternalRef(), t.getEffectiveAt(), t.getCreatedAt(),
                t.getReversalOf(),
                t.getPostings().stream().map(PostingResponse::from).toList()
        );
    }
}
