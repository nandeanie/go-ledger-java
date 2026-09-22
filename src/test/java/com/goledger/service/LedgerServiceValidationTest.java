package com.goledger.service;

import com.goledger.domain.*;
import com.goledger.exception.ValidationException;
import com.goledger.repository.*;
import com.goledger.web.dto.PostingRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * These deliberately don't touch a database: they pin down the zero-sum and
 * idempotency-key rules that make this a *ledger* rather than just a CRUD
 * app, fast enough to run on every save. The Postgres-trigger backstop for
 * the same invariant is covered separately in LedgerServiceIntegrationTest.
 */
@ExtendWith(MockitoExtension.class)
class LedgerServiceValidationTest {

    @Mock AccountRepository accountRepository;
    @Mock TransactionRepository transactionRepository;
    @Mock PostingRepository postingRepository;
    @Mock IdempotencyRecordRepository idempotencyRecordRepository;

    LedgerService service;
    final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new LedgerService(accountRepository, transactionRepository, postingRepository, idempotencyRecordRepository);
    }

    @Test
    void rejectsMissingIdempotencyKey() {
        assertThatThrownBy(() -> service.postTransaction(tenantId, "USD", null, "   ", null, List.of()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Idempotency-Key");

        verifyNoInteractions(accountRepository, transactionRepository, postingRepository, idempotencyRecordRepository);
    }

    @Test
    void rejectsFewerThanTwoPostings() {
        when(idempotencyRecordRepository.findByTenantIdAndIdempotencyKey(any(), any())).thenReturn(Optional.empty());
        List<PostingRequest> postings = List.of(new PostingRequest(UUID.randomUUID(), 1000L, "solo"));

        assertThatThrownBy(() -> service.postTransaction(tenantId, "USD", null, "key-1", null, postings))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("at least 2 postings");

        verifyNoInteractions(accountRepository);
    }

    @Test
    void rejectsPostingsThatDoNotSumToZero() {
        when(idempotencyRecordRepository.findByTenantIdAndIdempotencyKey(any(), any())).thenReturn(Optional.empty());
        List<PostingRequest> postings = List.of(
                new PostingRequest(UUID.randomUUID(), 1000L, "debit"),
                new PostingRequest(UUID.randomUUID(), -500L, "credit"));

        assertThatThrownBy(() -> service.postTransaction(tenantId, "USD", null, "key-1", null, postings))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("sum to zero");

        // The sum check must happen before ever touching accounts or persistence.
        verifyNoInteractions(accountRepository);
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void rejectsAPostingToAnAccountOutsideTheTenant() {
        when(idempotencyRecordRepository.findByTenantIdAndIdempotencyKey(any(), any())).thenReturn(Optional.empty());
        UUID missingAccount = UUID.randomUUID();
        when(accountRepository.findByIdAndTenantId(missingAccount, tenantId)).thenReturn(Optional.empty());

        List<PostingRequest> postings = List.of(
                new PostingRequest(missingAccount, 1000L, "debit"),
                new PostingRequest(UUID.randomUUID(), -1000L, "credit"));

        assertThatThrownBy(() -> service.postTransaction(tenantId, "USD", null, "key-1", null, postings))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("not found in this tenant");

        verify(transactionRepository, never()).save(any());
    }

    @Test
    void rejectsCurrencyMismatchBetweenTransactionAndAccount() {
        when(idempotencyRecordRepository.findByTenantIdAndIdempotencyKey(any(), any())).thenReturn(Optional.empty());
        UUID usdAccountId = UUID.randomUUID();
        UUID eurAccountId = UUID.randomUUID();
        Account usdAccount = new Account(tenantId, "USD wallet", AccountType.ASSET, "USD", null);
        Account eurAccount = new Account(tenantId, "EUR wallet", AccountType.ASSET, "EUR", null);
        // First posting matches the transaction's currency so the loop reaches
        // the second, mismatched one - proving every posting is checked, not
        // just the first.
        when(accountRepository.findByIdAndTenantId(usdAccountId, tenantId)).thenReturn(Optional.of(usdAccount));
        when(accountRepository.findByIdAndTenantId(eurAccountId, tenantId)).thenReturn(Optional.of(eurAccount));

        List<PostingRequest> postings = List.of(
                new PostingRequest(usdAccountId, 1000L, "debit"),
                new PostingRequest(eurAccountId, -1000L, "credit"));

        assertThatThrownBy(() -> service.postTransaction(tenantId, "USD", null, "key-1", null, postings))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("EUR");

        verify(transactionRepository, never()).save(any());
    }

    @Test
    void replaysTheExistingTransactionForAKnownIdempotencyKeyWithoutTouchingAccountsOrSaving() {
        UUID txnId = UUID.randomUUID();
        IdempotencyRecord record = new IdempotencyRecord(tenantId, "key-1", txnId);
        Transaction existing = new Transaction(tenantId, "USD", null, "key-1", Instant.now(), null);
        when(idempotencyRecordRepository.findByTenantIdAndIdempotencyKey(tenantId, "key-1"))
                .thenReturn(Optional.of(record));
        when(transactionRepository.findById(txnId)).thenReturn(Optional.of(existing));

        // Deliberately pass postings that would otherwise be invalid (unbalanced)
        // to prove the replay path returns early and never re-validates them.
        List<PostingRequest> wouldBeUnbalanced = List.of(
                new PostingRequest(UUID.randomUUID(), 1L, "x"),
                new PostingRequest(UUID.randomUUID(), 2L, "y"));

        Transaction result = service.postTransaction(tenantId, "USD", null, "key-1", null, wouldBeUnbalanced);

        assertThat(result).isSameAs(existing);
        verifyNoInteractions(accountRepository);
        verify(transactionRepository, never()).save(any());
    }
}
