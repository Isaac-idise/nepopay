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

    /**
     * What one account may see: everything it sent (including held/reversed transfers), but money it is
     * RECEIVING only once it has really arrived (SUCCESSFUL). `since` = the account's history-reset marker.
     */
    @Query(value = """
            select t from Transaction t
            join fetch t.sender s left join fetch s.user
            join fetch t.receiver r left join fetch r.user
            where (s.id = :accountId
                   or (r.id = :accountId and t.status = com.nepopay.transaction.TransactionStatus.SUCCESSFUL))
              and t.archived = false and t.createdAt >= :since
            order by t.createdAt desc, t.id desc
            """,
            countQuery = """
            select count(t) from Transaction t
            where (t.sender.id = :accountId
                   or (t.receiver.id = :accountId and t.status = com.nepopay.transaction.TransactionStatus.SUCCESSFUL))
              and t.archived = false and t.createdAt >= :since
            """)
    Page<Transaction> findHistory(@Param("accountId") UUID accountId, @Param("since") Instant since, Pageable pageable);

    /** Money that has left the sender today: completed AND held transfers both count. */
    @Query("""
            select coalesce(sum(t.amountKobo), 0) from Transaction t
            where t.sender.id = :accountId
              and t.type = com.nepopay.transaction.TransactionType.P2P_TRANSFER
              and t.status in (com.nepopay.transaction.TransactionStatus.SUCCESSFUL,
                               com.nepopay.transaction.TransactionStatus.FLAGGED)
              and t.createdAt >= :since
            """)
    long sumSentSince(@Param("accountId") UUID accountId, @Param("since") Instant since);

    @Query("""
            select count(t) from Transaction t
            where t.sender.id = :senderAccountId and t.receiver.id = :receiverAccountId
              and t.type = com.nepopay.transaction.TransactionType.P2P_TRANSFER
              and t.status in (com.nepopay.transaction.TransactionStatus.SUCCESSFUL,
                               com.nepopay.transaction.TransactionStatus.FLAGGED)
            """)
    long countPaidBetween(@Param("senderAccountId") UUID senderAccountId, @Param("receiverAccountId") UUID receiverAccountId);

    @Query("""
            select count(t) from Transaction t
            where t.sender.id = :accountId
              and t.type = com.nepopay.transaction.TransactionType.P2P_TRANSFER
              and t.status in (com.nepopay.transaction.TransactionStatus.SUCCESSFUL,
                               com.nepopay.transaction.TransactionStatus.FLAGGED)
              and t.createdAt >= :since
            """)
    long countSentSince(@Param("accountId") UUID accountId, @Param("since") Instant since);

    /** Scalar id lookups: they never initialise an Account proxy before the ledger has locked the row. */
    @Query("select t.receiver.id from Transaction t where t.id = :id")
    Optional<UUID> findReceiverAccountId(@Param("id") UUID id);

    @Query("select t.sender.id from Transaction t where t.id = :id")
    Optional<UUID> findSenderAccountId(@Param("id") UUID id);
}
