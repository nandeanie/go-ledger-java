package com.goledger.web;

import com.goledger.domain.ApiKeyScope;
import com.goledger.domain.Transaction;
import com.goledger.security.AuthContext;
import com.goledger.service.LedgerService;
import com.goledger.web.dto.CreateTransactionRequest;
import com.goledger.web.dto.TransactionResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/v1/transactions")
public class TransactionController {

    private final LedgerService ledgerService;

    public TransactionController(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse post(@Valid @RequestBody CreateTransactionRequest request,
                                     @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        AuthContext.current().requireScope(ApiKeyScope.POST);
        Transaction transaction = ledgerService.postTransaction(
                AuthContext.current().tenantId(),
                request.currency().toUpperCase(),
                request.externalRef(),
                idempotencyKey,
                request.effectiveAt(),
                request.postings()
        );
        return TransactionResponse.from(transaction);
    }

    @GetMapping("/{id}")
    public TransactionResponse get(@PathVariable UUID id) {
        AuthContext.current().requireScope(ApiKeyScope.READ);
        return TransactionResponse.from(ledgerService.getTransaction(AuthContext.current().tenantId(), id));
    }

    @PostMapping("/{id}/reverse")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse reverse(@PathVariable UUID id,
                                        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        AuthContext.current().requireScope(ApiKeyScope.POST);
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new com.goledger.exception.ValidationException("Idempotency-Key header is required");
        }
        Transaction reversal = ledgerService.reverseTransaction(AuthContext.current().tenantId(), id, idempotencyKey);
        return TransactionResponse.from(reversal);
    }
}
