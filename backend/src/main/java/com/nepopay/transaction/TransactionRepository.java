package com.nepopay.transaction;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    Optional<Transaction> findByReference(String reference);

    @Query(value = """
            select t from Transaction t
            join fetch t.sender s left join fetch s.user
            join fetch t.receiver r left join fetch r.user
            where (s.id = :accountId or r.id = :accountId) and t.archived = false
            order by t.createdAt desc, t.id desc
            """,
            countQuery = """
            select count(t) from Transaction t
            where (t.sender.id = :accountId or t.receiver.id = :accountId) and t.archived = false
            """)
    Page<Transaction> findHistory(@Param("accountId") UUID accountId, Pageable pageable);

    @Query("""
            select coalesce(sum(t.amountKobo), 0) from Transaction t
            where t.sender.id = :accountId
              and t.type = com.nepopay.transaction.TransactionType.P2P_TRANSFER
              and t.status = com.nepopay.transaction.TransactionStatus.SUCCESSFUL
              and t.createdAt >= :since
            """)
    long sumSentSince(@Param("accountId") UUID accountId, @Param("since") Instant since);
}
