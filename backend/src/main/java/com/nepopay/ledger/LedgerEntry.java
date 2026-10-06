package com.nepopay.ledger;

import com.nepopay.account.Account;
import com.nepopay.transaction.Transaction;
import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.Immutable;

/** One immutable accounting fact. The database also blocks UPDATE and DELETE with a trigger. */
@Entity
@Immutable
@Table(name = "ledger_entries")
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id")
    private Transaction transaction;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id")
    private Account account;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EntryType entryType;

    @Column(nullable = false)
    private long amountKobo;

    @Column(nullable = false)
    private long balanceAfterKobo;

    @Column(nullable = false)
    private Instant createdAt;

    protected LedgerEntry() {
    }

    public LedgerEntry(Transaction transaction, Account account, EntryType entryType,
                       long amountKobo, long balanceAfterKobo) {
        this.transaction = transaction;
        this.account = account;
        this.entryType = entryType;
        this.amountKobo = amountKobo;
        this.balanceAfterKobo = balanceAfterKobo;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public EntryType getEntryType() { return entryType; }
    public long getAmountKobo() { return amountKobo; }
    public long getBalanceAfterKobo() { return balanceAfterKobo; }
    public Instant getCreatedAt() { return createdAt; }
}
