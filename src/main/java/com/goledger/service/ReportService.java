package com.goledger.service;

import com.goledger.domain.Account;
import com.goledger.repository.AccountRepository;
import com.goledger.repository.PostingRepository;
import com.goledger.web.dto.CurrencyTotal;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The trial balance is the double-entry "proof": since every transaction's
 * postings sum to zero, the sum across every posting for a tenant, per
 * currency, must always be zero too. If it isn't, the invariant has been
 * violated somewhere and that's a bug, not a business event.
 */
@Service
public class ReportService {

    private final AccountRepository accountRepository;
    private final PostingRepository postingRepository;

    public ReportService(AccountRepository accountRepository, PostingRepository postingRepository) {
        this.accountRepository = accountRepository;
        this.postingRepository = postingRepository;
    }

    public List<CurrencyTotal> trialBalance(UUID tenantId) {
        List<Account> accounts = accountRepository.findAllByTenantId(tenantId);
        Map<String, List<Account>> byCurrency = accounts.stream()
                .collect(Collectors.groupingBy(Account::getCurrency));

        return byCurrency.entrySet().stream()
                .map(e -> {
                    long total = e.getValue().stream()
                            .mapToLong(a -> postingRepository.sumAmountByAccountId(a.getId()))
                            .sum();
                    return new CurrencyTotal(e.getKey(), total, e.getValue().size(), total == 0);
                })
                .toList();
    }
}
