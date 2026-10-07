package com.nepopay.fraud;

import com.nepopay.transaction.Transaction;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "fraud_alerts")
public class FraudAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id")
    private Transaction transaction;

    /** Read-only copy of the foreign key, so the id is available without touching the Transaction proxy. */
    @Column(name = "transaction_id", insertable = false, updatable = false)
    private UUID transactionId;

    @Column(nullable = false)
    private int riskScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RiskLevel riskLevel;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String reasons;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertStatus status;

    private UUID reviewedBy;
    private Instant reviewedAt;
    private String reviewNote;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected FraudAlert() {
    }

    public FraudAlert(Transaction transaction, int riskScore, RiskLevel riskLevel, String reasonsJson, AlertStatus status) {
        this.transaction = transaction;
        this.riskScore = riskScore;
        this.riskLevel = riskLevel;
        this.reasons = reasonsJson;
        this.status = status;
        this.createdAt = Instant.now();
    }

    public void review(AlertStatus outcome, UUID adminId, String note) {
        this.status = outcome;
        this.reviewedBy = adminId;
        this.reviewedAt = Instant.now();
        this.reviewNote = note;
    }

    public UUID getId() { return id; }
    public Transaction getTransaction() { return transaction; }
    public UUID getTransactionId() { return transactionId; }
    public int getRiskScore() { return riskScore; }
    public RiskLevel getRiskLevel() { return riskLevel; }
    public String getReasons() { return reasons; }
    public AlertStatus getStatus() { return status; }
    public Instant getReviewedAt() { return reviewedAt; }
    public String getReviewNote() { return reviewNote; }
    public Instant getCreatedAt() { return createdAt; }
}
