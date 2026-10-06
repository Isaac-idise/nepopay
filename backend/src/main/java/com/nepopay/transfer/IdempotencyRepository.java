package com.nepopay.transfer;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IdempotencyRepository extends JpaRepository<IdempotencyRecord, UUID> {

    Optional<IdempotencyRecord> findByUserIdAndIdemKey(UUID userId, String idemKey);

    @Modifying
    @Query("delete from IdempotencyRecord r where r.userId = :userId and r.idemKey = :key and r.status = :status")
    int deleteByStatus(@Param("userId") UUID userId, @Param("key") String key,
                       @Param("status") IdempotencyRecord.Status status);
}
