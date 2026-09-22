package com.goledger.web;

import com.goledger.domain.Account;
import com.goledger.domain.AccountType;
import com.goledger.domain.ApiKeyScope;
import com.goledger.exception.ValidationException;
import com.goledger.security.AuthContext;
import com.goledger.service.LedgerService;
import com.goledger.web.dto.AccountResponse;
import com.goledger.web.dto.BalanceResponse;
import com.goledger.web.dto.CreateAccountRequest;
import com.goledger.web.dto.PostingResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/accounts")
public class AccountController {

    private final LedgerService ledgerService;

    public AccountController(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse create(@Valid @RequestBody CreateAccountRequest request) {
        AuthContext.current().requireScope(ApiKeyScope.POST);
        AccountType type;
        try {
            type = AccountType.valueOf(request.type().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ValidationException("unknown account type: " + request.type());
        }
        Account account = ledgerService.createAccount(
                AuthContext.current().tenantId(), request.name(), type, request.currency().toUpperCase(),
                request.parentAccountId());
        return AccountResponse.from(account);
    }

    @GetMapping
    public List<AccountResponse> list() {
        AuthContext.current().requireScope(ApiKeyScope.READ);
        return ledgerService.listAccounts(AuthContext.current().tenantId()).stream()
                .map(AccountResponse::from).toList();
    }

    @GetMapping("/{id}")
    public AccountResponse get(@PathVariable UUID id) {
        AuthContext.current().requireScope(ApiKeyScope.READ);
        return AccountResponse.from(ledgerService.getAccount(AuthContext.current().tenantId(), id));
    }

    @GetMapping("/{id}/balance")
    public BalanceResponse balance(@PathVariable UUID id) {
        AuthContext.current().requireScope(ApiKeyScope.READ);
        UUID tenantId = AuthContext.current().tenantId();
        Account account = ledgerService.getAccount(tenantId, id);
        long balance = ledgerService.balanceOf(tenantId, id);
        return new BalanceResponse(id, account.getCurrency(), balance);
    }

    @GetMapping("/{id}/postings")
    public List<PostingResponse> statement(@PathVariable UUID id) {
        AuthContext.current().requireScope(ApiKeyScope.READ);
        return ledgerService.statementFor(AuthContext.current().tenantId(), id).stream()
                .map(PostingResponse::from).toList();
    }
}
