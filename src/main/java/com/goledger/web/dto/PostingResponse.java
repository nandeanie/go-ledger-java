package com.goledger.web.dto;

import com.goledger.domain.Posting;
import java.util.UUID;

public record PostingResponse(UUID id, UUID accountId, long amount, String description) {
    public static PostingResponse from(Posting p) {
        return new PostingResponse(p.getId(), p.getAccountId(), p.getAmount(), p.getDescription());
    }
}
