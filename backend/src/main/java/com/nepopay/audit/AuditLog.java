package com.nepopay.audit;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Immutable
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false)
    private String action;

    private String entityType;

    private String entityId;

    @JdbcTypeCode(SqlTypes.JSON)
    private String metadata;

    @Column(nullable = false)
    private Instant createdAt;

    protected AuditLog() {
    }

    public AuditLog(UUID userId, String action, String entityType, String entityId, String metadataJson) {
        this.userId = userId;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.metadata = metadataJson;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getAction() { return action; }
}
