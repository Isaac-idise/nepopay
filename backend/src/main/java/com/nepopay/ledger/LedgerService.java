package com.nepopay.ledger;

import com.nepopay.account.Account;
import com.nepopay.account.AccountRepository;
import com.nepopay.account.AccountType;
import com.nepopay.common.ErrorCode;
import com.nepopay.common.Money;
import com.nepopay.common.NepOpayException;
import com.nepopay.transaction.Transaction;
import com.nepopay.transaction.TransactionReferenceGenerator;
import com.nepopay.transaction.TransactionRepository;
import com.nepopay.transaction.TransactionType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * THE ONLY place money moves. One call = one balanced transaction: one DEBIT, one CREDIT,
 * both balances updated, all inside the caller's database transaction (MANDATORY propagation),
 * so the caller's later failure rolls the money movement back too.
 */
@Service
public class LedgerService {

    private final AccountRepository accounts;
    private final TransactionRepository transactions;
    private final LedgerEntryRepository entries;
    private final TransactionReferenceGenerator references;

    public LedgerService(AccountRepository accounts, TransactionRepository transactions,
                         LedgerEntryRepository entries, TransactionReferenceGenerator references) {
        this.accounts = accounts;
        this.transactions = transactions;
        this.entries = entries;
        this.references = references;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Transaction post(UUID senderId, UUID receiverId, Money amount,
                            TransactionType type, String description) {
        if (amount.isZero()) {
            throw new IllegalArgumentException("Ledger amount must be greater than zero");
        }
        if (senderId.equals(receiverId)) {
            throw new IllegalArgumentException("Sender and receiver must differ");
        }

        Map<UUID, Account> locked = lockInOrder(senderId, receiverId);
        Account sender = locked.get(senderId);
        Account receiver = locked.get(receiverId);

        // Checked AFTER locking, so no one can spend the same money between check and debit.
        if (sender.getAccountType() == AccountType.USER && sender.getBalanceKobo() < amount.kobo()) {
            throw new NepOpayException(ErrorCode.INSUFFICIENT_BALANCE,
                    "Insufficient balance. Your available balance is "
                            + Money.ofKobo(sender.getBalanceKobo()) + ".");
        }
        return record(sender, receiver, amount, type, description);
    }

    /**
     * Moves a user account to exactly `target`, against a system account. The difference is worked out
     * INSIDE the lock, so a transfer arriving at the same moment cannot make it wrong.
     * Returns empty when the balance is already the target.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Transaction> adjustBalanceTo(UUID userAccountId, UUID systemAccountId, Money target,
                                                 TransactionType type, String description) {
        Map<UUID, Account> locked = lockInOrder(userAccountId, systemAccountId);
        Account user = locked.get(userAccountId);
        Account system = locked.get(systemAccountId);

        long current = user.getBalanceKobo();
        if (current == target.kobo()) {
            return Optional.empty();
        }
        if (current < target.kobo()) {
            return Optional.of(record(system, user, Money.ofKobo(target.kobo() - current), type, description));
        }
        return Optional.of(record(user, system, Money.ofKobo(current - target.kobo()), type, description));
    }

    private Transaction record(Account sender, Account receiver, Money amount,
                               TransactionType type, String description) {
        sender.debit(amount.kobo());
        receiver.credit(amount.kobo());

        Transaction tx = transactions.save(Transaction.successful(
                references.next(), sender, receiver, amount.kobo(), type, description));
        entries.save(new LedgerEntry(tx, sender, EntryType.DEBIT, amount.kobo(), sender.getBalanceKobo()));
        entries.save(new LedgerEntry(tx, receiver, EntryType.CREDIT, amount.kobo(), receiver.getBalanceKobo()));
        return tx;
    }

    /** Always lock the "smaller" account id first. Two opposite transfers then queue up instead of deadlocking. */
    private Map<UUID, Account> lockInOrder(UUID a, UUID b) {
        List<UUID> ordered = Stream.of(a, b).sorted().toList();
        Map<UUID, Account> result = new HashMap<>();
        for (UUID id : ordered) {
            result.put(id, accounts.findByIdForUpdate(id)
                    .orElseThrow(() -> new IllegalStateException("Account not found: " + id)));
        }
        return result;
    }
}
