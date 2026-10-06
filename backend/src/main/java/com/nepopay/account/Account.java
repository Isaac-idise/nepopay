package com.nepopay.account;

import com.nepopay.user.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, unique = true, updatable = false)
    private String accountNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private AccountType accountType;

    @Column(nullable = false)
    private long balanceKobo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountStatus status = AccountStatus.ACTIVE;

    @Version
    private Long version;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    /** Transactions older than this are hidden from THIS account's history (set by the demo reset). */
    @Column(name = "history_reset_at")
    private Instant historyResetAt;

    protected Account() {
    }

    public static Account forUser(User user, String accountNumber) {
        Account a = new Account();
        a.user = user;
        a.accountNumber = accountNumber;
        a.accountType = AccountType.USER;
        return a;
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

    /** RULE: only LedgerService may call debit/credit, so every balance change has a ledger entry. */
    public void debit(long kobo) {
        balanceKobo = Math.subtractExact(balanceKobo, kobo);
    }

    /** RULE: only LedgerService may call debit/credit, so every balance change has a ledger entry. */
    public void credit(long kobo) {
        balanceKobo = Math.addExact(balanceKobo, kobo);
    }

    public void markHistoryReset(Instant at) { this.historyResetAt = at; }
    public Instant getHistoryResetAt() { return historyResetAt; }
    public UUID getId() { return id; }
    public User getUser() { return user; }
    public String getAccountNumber() { return accountNumber; }
    public AccountType getAccountType() { return accountType; }
    public long getBalanceKobo() { return balanceKobo; }
    public AccountStatus getStatus() { return status; }
}
