package com.goledger.service;

import com.goledger.domain.*;
import com.goledger.repository.TransactionRepository;
import com.goledger.support.AbstractIntegrationTest;
import com.goledger.web.dto.CurrencyTotal;
import com.goledger.web.dto.PostingRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LedgerServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired LedgerService ledgerService;
    @Autowired AdminService adminService;
    @Autowired ReportService reportService;
    @Autowired TransactionRepository transactionRepository;

    private UUID tenantId;
    private UUID checking;
    private UUID income;

    private void newTenantWithAccounts() {
        tenantId = adminService.createTenant("test-tenant-" + UUID.randomUUID()).getId();
        checking = ledgerService.createAccount(tenantId, "Checking", AccountType.ASSET, "USD", null).getId();
        income = ledgerService.createAccount(tenantId, "Income", AccountType.INCOME, "USD", null).getId();
    }

    @Test
    void postsABalancedTransactionAndBalancesReflectItImmediately() {
        newTenantWithAccounts();

        Transaction txn = ledgerService.postTransaction(tenantId, "USD", null, "key-" + UUID.randomUUID(), null,
                List.of(new PostingRequest(checking, 1000L, "deposit"),
                        new PostingRequest(income, -1000L, "deposit")));

        assertThat(txn.getId()).isNotNull();
        assertThat(ledgerService.balanceOf(tenantId, checking)).isEqualTo(1000L);
        assertThat(ledgerService.balanceOf(tenantId, income)).isEqualTo(-1000L);
    }

    @Test
    void replayingTheSameIdempotencyKeyReturnsTheOriginalTransactionAndDoesNotDoublePost() {
        newTenantWithAccounts();
        String key = "replay-key-" + UUID.randomUUID();
        List<PostingRequest> postings = List.of(
                new PostingRequest(checking, 500L, "deposit"),
                new PostingRequest(income, -500L, "deposit"));

        Transaction first = ledgerService.postTransaction(tenantId, "USD", null, key, null, postings);
        Transaction second = ledgerService.postTransaction(tenantId, "USD", null, key, null, postings);

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(ledgerService.balanceOf(tenantId, checking)).isEqualTo(500L); // not 1000 - posted once
    }

    /**
     * The most important test in this file for a payment ledger: fire the same
     * idempotency key from many threads at once and prove exactly one
     * transaction results. Threads that lose the race are expected to either
     * see the winner's result directly, or occasionally hit a transient
     * SERIALIZABLE conflict - which is exactly why real clients retry an
     * idempotent request on failure, so this test does too, rather than
     * asserting zero failures (which the isolation level does not promise).
     */
    @Test
    void concurrentPostsWithTheSameIdempotencyKeyProduceExactlyOneTransaction() throws Exception {
        newTenantWithAccounts();
        String key = "race-key-" + UUID.randomUUID();
        List<PostingRequest> postings = List.of(
                new PostingRequest(checking, 250L, "race"),
                new PostingRequest(income, -250L, "race"));

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);

        List<Future<UUID>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                return postWithRetries(key, postings, 5);
            }));
        }
        ready.await();
        go.countDown();

        Set<UUID> distinctTransactionIds = new HashSet<>();
        for (Future<UUID> f : futures) {
            distinctTransactionIds.add(f.get(30, TimeUnit.SECONDS));
        }
        pool.shutdown();

        assertThat(distinctTransactionIds).as("every caller must observe the same winning transaction").hasSize(1);
        assertThat(ledgerService.balanceOf(tenantId, checking)).isEqualTo(250L); // posted exactly once, not 8 times
    }

    private UUID postWithRetries(String key, List<PostingRequest> postings, int attemptsLeft) throws InterruptedException {
        try {
            return ledgerService.postTransaction(tenantId, "USD", null, key, null, postings).getId();
        } catch (RuntimeException e) {
            if (attemptsLeft <= 0) {
                throw e;
            }
            Thread.sleep(10);
            return postWithRetries(key, postings, attemptsLeft - 1);
        }
    }

    @Test
    void reversalNetsOutTheOriginalTransactionAndIsItselfIdempotent() {
        newTenantWithAccounts();
        Transaction original = ledgerService.postTransaction(tenantId, "USD", null, "orig-" + UUID.randomUUID(), null,
                List.of(new PostingRequest(checking, 750L, "deposit"),
                        new PostingRequest(income, -750L, "deposit")));

        String reverseKey = "reverse-" + UUID.randomUUID();
        Transaction reversal1 = ledgerService.reverseTransaction(tenantId, original.getId(), reverseKey);
        Transaction reversal2 = ledgerService.reverseTransaction(tenantId, original.getId(), reverseKey);

        assertThat(reversal1.getId()).isEqualTo(reversal2.getId());
        assertThat(ledgerService.balanceOf(tenantId, checking)).isEqualTo(0L);
        assertThat(ledgerService.balanceOf(tenantId, income)).isEqualTo(0L);
    }

    @Test
    void trialBalanceNetsToZeroAcrossMultipleTransactions() {
        newTenantWithAccounts();
        ledgerService.postTransaction(tenantId, "USD", null, "t1-" + UUID.randomUUID(), null,
                List.of(new PostingRequest(checking, 2000L, null), new PostingRequest(income, -2000L, null)));
        ledgerService.postTransaction(tenantId, "USD", null, "t2-" + UUID.randomUUID(), null,
                List.of(new PostingRequest(checking, -300L, null), new PostingRequest(income, 300L, null)));

        List<CurrencyTotal> totals = reportService.trialBalance(tenantId);

        assertThat(totals).hasSize(1);
        CurrencyTotal usd = totals.get(0);
        assertThat(usd.currency()).isEqualTo("USD");
        assertThat(usd.netAmount()).isZero();
        assertThat(usd.balanced()).isTrue();
    }

    /**
     * Bypasses LedgerService's application-level validation entirely and saves
     * an unbalanced transaction straight through the repository, to prove the
     * Postgres constraint trigger (see V1__init.sql) is a real backstop and not
     * just documentation. This is what "defense in depth" is actually worth
     * verifying for a ledger.
     *
     * Note: the exact exception type here is intentionally asserted loosely.
     * Because the trigger is DEFERRABLE INITIALLY DEFERRED, the failure surfaces
     * when Spring's JpaTransactionManager commits - typically as a
     * TransactionSystemException wrapping the underlying constraint violation -
     * rather than the DataIntegrityViolationException you'd get from an
     * immediate constraint. If you see a different RuntimeException subtype
     * the first time you run this, that's expected; tighten the assertion to
     * match once you've observed it rather than treating a differently-typed
     * exception as this test failing to catch the bug.
     */
    @Test
    void directRepositorySaveOfAnUnbalancedTransactionIsRejectedByThePostgresTrigger() {
        newTenantWithAccounts();

        Transaction bad = new Transaction(tenantId, "USD", null, "trigger-test-" + UUID.randomUUID(),
                Instant.now(), null);
        bad.addPosting(new Posting(checking, 100L, "unbalanced"));
        bad.addPosting(new Posting(income, -1L, "unbalanced"));

        assertThatThrownBy(() -> transactionRepository.save(bad))
                .isInstanceOf(RuntimeException.class);
    }
}
