package com.nepopay.transaction;

import com.nepopay.account.Account;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, updatable = false)
    private String reference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_account_id")
    private Account sender;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receiver_account_id")
    private Account receiver;

    @Column(nullable = false, updatable = false)
    private long amountKobo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionStatus status;

    private String description;

    @Column(nullable = false)
    private int riskScore;

    private String failureReason;

    @Column(nullable = false)
    private boolean archived;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected Transaction() {
    }

    public static Transaction successful(String reference, Account sender, Account receiver,
                                         long amountKobo, TransactionType type, String description) {
        Transaction t = new Transaction();
        t.reference = reference;
        t.sender = sender;
        t.receiver = receiver;
        t.amountKobo = amountKobo;
        t.type = type;
        t.status = TransactionStatus.SUCCESSFUL;
        t.description = description;
        return t;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getReference() { return reference; }
    public Account getSender() { return sender; }
    public Account getReceiver() { return receiver; }
    public long getAmountKobo() { return amountKobo; }
    public TransactionType getType() { return type; }
    public TransactionStatus getStatus() { return status; }
    public String getDescription() { return description; }
    public Instant getCreatedAt() { return createdAt; }
}
