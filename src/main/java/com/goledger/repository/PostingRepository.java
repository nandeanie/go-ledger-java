package com.goledger.repository;

import com.goledger.domain.Posting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

public interface PostingRepository extends JpaRepository<Posting, UUID> {

    @Query("select coalesce(sum(p.amount), 0) from Posting p where p.accountId = :accountId")
    long sumAmountByAccountId(@Param("accountId") UUID accountId);

    List<Posting> findAllByAccountIdOrderByCreatedAtAsc(UUID accountId);
}
