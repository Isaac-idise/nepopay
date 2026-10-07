package com.nepopay.fraud;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FraudAlertRepository extends JpaRepository<FraudAlert, UUID> {

    @Query(value = """
            select a from FraudAlert a
            join fetch a.transaction t join fetch t.sender s left join fetch s.user
            join fetch t.receiver r left join fetch r.user
            where a.status = :status order by a.createdAt desc
            """,
            countQuery = "select count(a) from FraudAlert a where a.status = :status")
    Page<FraudAlert> findByStatus(@Param("status") AlertStatus status, Pageable pageable);

    @Query(value = """
            select a from FraudAlert a
            join fetch a.transaction t join fetch t.sender s left join fetch s.user
            join fetch t.receiver r left join fetch r.user
            order by a.createdAt desc
            """,
            countQuery = "select count(a) from FraudAlert a")
    Page<FraudAlert> findEvery(Pageable pageable);

    @Query("""
            select a from FraudAlert a
            join fetch a.transaction t join fetch t.sender s left join fetch s.user
            join fetch t.receiver r left join fetch r.user
            where a.id = :id
            """)
    Optional<FraudAlert> findDetail(@Param("id") UUID id);

    /** Locked while being reviewed: two admins cannot approve and reject the same alert at once. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from FraudAlert a where a.id = :id")
    Optional<FraudAlert> findByIdForUpdate(@Param("id") UUID id);
}
