package com.nepopay.audit;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    long countByUserIdAndActionAndCreatedAtAfter(UUID userId, String action, Instant since);
}
