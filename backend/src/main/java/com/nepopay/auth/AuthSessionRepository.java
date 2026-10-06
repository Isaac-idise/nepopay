package com.nepopay.auth;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {

    /** Locked so two simultaneous refreshes with the same token cannot both succeed. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from AuthSession s where s.tokenHash = :hash")
    Optional<AuthSession> findByTokenHashForUpdate(@Param("hash") String hash);

    @Modifying
    @Query("update AuthSession s set s.revokedAt = :now where s.familyId = :familyId and s.revokedAt is null")
    int revokeFamily(@Param("familyId") UUID familyId, @Param("now") Instant now);
}
