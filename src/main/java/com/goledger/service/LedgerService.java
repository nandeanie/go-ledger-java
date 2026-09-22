package com.goledger.service;

import com.goledger.domain.*;
import com.goledger.exception.ConflictException;
import com.goledger.exception.NotFoundException;
import com.goledger.exception.ValidationException;
import com.goledger.repository.*;
import com.goledger.web.dto.PostingRequest;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The core double-entry posting engine. Mirrors go-ledger's central invariant:
 * a transaction is a set of two or more postings whose signed amounts sum to
 * zero, in a single currency. Balances are never stored - they're derived by
 * summing the immutable posting history (see PostingRepository.sumAmountByAccountId).
 *
 * Every posting path here re-validates the zero-sum invariant in application
 * code AND relies on a Postgres CHECK trigger (see V1__init.sql) as a backstop,
 * so no write path - including a future one we forget to route through this
 * service - can ever leave an unbalanced transaction persisted.
 */
@Service
public class LedgerService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final PostingRepository postingRepository;
    private final IdempotencyRecordRepository idempotencyRecordRepository;

    public LedgerService(AccountRepository accountRepository,
                          TransactionRepository transactionRepository,
                          PostingRepository postingRepository,
                          IdempotencyRecordRepository idempotencyRecordRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.postingRepository = postingRepository;
        this.idempotencyRecordRepository = idempotencyRecordRepository;
    }

    @Transactional
    public Account createAccount(UUID tenantId, String name, AccountType type, String currency, UUID parentAccountId) {
        if (parentAccountId != null) {
            accountRepository.findByIdAndTenantId(parentAccountId, tenantId)
                    .orElseThrow(() -> new ValidationException("parent account not found in this tenant: " + parentAccountId));
        }
        return accountRepository.save(new Account(tenantId, name, type, currency, parentAccountId));
    }

    public Account getAccount(UUID tenantId, UUID accountId) {
        return accountRepository.findByIdAndTenantId(accountId, tenantId)
                .orElseThrow(() -> new NotFoundException("account not found: " + accountId));
    }

    public List<Account> listAccounts(UUID tenantId) {
        return accountRepository.findAllByTenantId(tenantId);
    }

    /** Sums the account's own postings. Does not roll up child accounts - see docs/adr/023 for that behaviour. */
    public long balanceOf(UUID tenantId, UUID accountId) {
        getAccount(tenantId, accountId); // 404 + tenant isolation check
        return postingRepository.sumAmountByAccountId(accountId);
    }

    public List<Posting> statementFor(UUID tenantId, UUID accountId) {
        getAccount(tenantId, accountId);
        return postingRepository.findAllByAccountIdOrderByCreatedAtAsc(accountId);
    }

    /**
     * Posts a balanced transaction, idempotent on (tenantId, idempotencyKey):
     * a retried request with the same key returns the original transaction
     * instead of posting a second time.
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Transaction postTransaction(UUID tenantId, String currency, String externalRef,
                                        String idempotencyKey, Instant effectiveAt,
                                        List<PostingRequest> postingRequests) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ValidationException("Idempotency-Key header is required");
        }

        Optional<IdempotencyRecord> existing =
                idempotencyRecordRepository.findByTenantIdAndIdempotencyKey(tenantId, idempotencyKey);
        if (existing.isPresent()) {
            return transactionRepository.findById(existing.get().getTransactionId())
                    .orElseThrow(() -> new IllegalStateException("idempotency record points at a missing transaction"));
        }

        if (postingRequests == null || postingRequests.size() < 2) {
            throw new ValidationException("a transaction needs at least 2 postings");
        }

        if (externalRef != null && !externalRef.isBlank()
                && transactionRepository.existsByTenantIdAndExternalRef(tenantId, externalRef)) {
            throw new ConflictException("external_ref already used for this tenant: " + externalRef);
        }

        long sum = 0;
        for (PostingRequest p : postingRequests) {
            sum += p.amount();
        }
        if (sum != 0) {
            throw new ValidationException("postings must sum to zero; got " + sum);
        }

        for (PostingRequest p : postingRequests) {
            Account account = accountRepository.findByIdAndTenantId(p.accountId(), tenantId)
                    .orElseThrow(() -> new ValidationException("account not found in this tenant: " + p.accountId()));
            if (!account.getCurrency().equalsIgnoreCase(currency)) {
                throw new ValidationException("account " + account.getId() + " is denominated in "
                        + account.getCurrency() + ", not " + currency
                        + " (cross-currency transactions require an FX conversion, not yet supported in this port)");
            }
        }

        Transaction transaction = new Transaction(
                tenantId, currency,
                (externalRef == null || externalRef.isBlank()) ? null : externalRef,
                idempotencyKey,
                effectiveAt != null ? effectiveAt : Instant.now(),
                null
        );
        for (PostingRequest p : postingRequests) {
            transaction.addPosting(new Posting(p.accountId(), p.amount(), p.description()));
        }

        try {
            Transaction saved = transactionRepository.save(transaction);
            idempotencyRecordRepository.save(new IdempotencyRecord(tenantId, idempotencyKey, saved.getId()));
            return saved;
        } catch (DataIntegrityViolationException | ConcurrencyFailureException e) {
            // Lost a race on the idempotency key or external_ref unique constraint,
            // or Postgres flagged a SERIALIZABLE conflict on the check-then-insert
            // (the classic case for this exact pattern) - either way, someone else's
            // identical request is winning this race, so replay their result rather
            // than erroring the caller. If the winner hasn't committed yet, this
            // lookup can still come up empty; the caller retrying with the same
            // Idempotency-Key is always safe and will resolve once it has.
            return idempotencyRecordRepository.findByTenantIdAndIdempotencyKey(tenantId, idempotencyKey)
                    .flatMap(r -> transactionRepository.findById(r.getTransactionId()))
                    .orElseThrow(() -> e);
        }
    }

    public Transaction getTransaction(UUID tenantId, UUID transactionId) {
        return transactionRepository.findByIdAndTenantId(transactionId, tenantId)
                .orElseThrow(() -> new NotFoundException("transaction not found: " + transactionId));
    }

    /**
     * Posts the exact inverse of an existing transaction. Idempotent against
     * re-reversal: reversing an already-reversed transaction returns the
     * original reversal rather than posting a second one.
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Transaction reverseTransaction(UUID tenantId, UUID transactionId, String idempotencyKey) {
        Transaction original = getTransaction(tenantId, transactionId);

        Optional<IdempotencyRecord> existing =
                idempotencyRecordRepository.findByTenantIdAndIdempotencyKey(tenantId, idempotencyKey);
        if (existing.isPresent()) {
            return transactionRepository.findById(existing.get().getTransactionId()).orElseThrow();
        }

        Transaction reversal = new Transaction(
                tenantId, original.getCurrency(), null, idempotencyKey, Instant.now(), original.getId()
        );
        for (Posting p : original.getPostings()) {
            reversal.addPosting(new Posting(p.getAccountId(), -p.getAmount(), "reversal of " + original.getId()));
        }

        try {
            Transaction saved = transactionRepository.save(reversal);
            idempotencyRecordRepository.save(new IdempotencyRecord(tenantId, idempotencyKey, saved.getId()));
            return saved;
        } catch (DataIntegrityViolationException | ConcurrencyFailureException e) {
            return idempotencyRecordRepository.findByTenantIdAndIdempotencyKey(tenantId, idempotencyKey)
                    .flatMap(r -> transactionRepository.findById(r.getTransactionId()))
                    .orElseThrow(() -> e);
        }
    }
}
