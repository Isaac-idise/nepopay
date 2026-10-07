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
import com.nepopay.transaction.TransactionStateMachine;
import com.nepopay.transaction.TransactionStatus;
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
 * THE ONLY place money moves. Every movement is a balanced DEBIT + CREDIT inside the caller's database
 * transaction (MANDATORY propagation), so the caller's later failure rolls the money movement back too.
 *
 *  post()          sender -> receiver                       (normal transfer)
 *  postHeld()      sender -> SYSTEM_HOLD, receiver not paid (flagged transfer: money is parked)
 *  settleHeld()    SYSTEM_HOLD -> receiver                  (admin approves)
 *  refundHeld()    SYSTEM_HOLD -> sender, as a REVERSAL     (admin rejects)
 *  adjustBalanceTo()  user <-> system account               (demo reset)
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
        requirePositive(amount);
        if (senderId.equals(receiverId)) {
            throw new IllegalArgumentException("Sender and receiver must differ");
        }
        Map<UUID, Account> locked = lockInOrder(senderId, receiverId);
        Account sender = locked.get(senderId);
        Account receiver = locked.get(receiverId);
        requireFunds(sender, amount);

        Transaction tx = transactions.save(Transaction.start(
                references.next(), sender, receiver, amount.kobo(), type, description));
        move(tx, sender, receiver, amount);
        tx.transition(TransactionStatus.SUCCESSFUL);
        return tx;
    }

    /**
     * The transaction still says sender -> receiver (that is what the customer asked for), but the money
     * only goes as far as SYSTEM_HOLD and the status is FLAGGED until a human decides.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Transaction postHeld(UUID senderId, UUID holdId, UUID receiverId, Money amount,
                                TransactionType type, String description) {
        requirePositive(amount);
        Map<UUID, Account> locked = lockInOrder(senderId, holdId);
        Account sender = locked.get(senderId);
        Account hold = locked.get(holdId);
        requireFunds(sender, amount);

        // getReferenceById: a placeholder for the receiver, nothing is loaded or locked.
        Transaction tx = transactions.save(Transaction.start(
                references.next(), sender, accounts.getReferenceById(receiverId), amount.kobo(), type, description));
        move(tx, sender, hold, amount);
        tx.transition(TransactionStatus.FLAGGED);
        return tx;
    }

    /** Approval: the held money continues to the receiver, as extra entries on the SAME transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void settleHeld(UUID transactionId, UUID holdId) {
        Transaction tx = transactions.findById(transactionId).orElseThrow(() -> new IllegalStateException("Transaction missing"));
        TransactionStateMachine.require(tx.getStatus(), TransactionStatus.SUCCESSFUL);
        UUID receiverId = transactions.findReceiverAccountId(transactionId).orElseThrow();

        Map<UUID, Account> locked = lockInOrder(holdId, receiverId);
        move(tx, locked.get(holdId), locked.get(receiverId), Money.ofKobo(tx.getAmountKobo()));
        tx.transition(TransactionStatus.SUCCESSFUL);
    }

    /** Rejection: a NEW REVERSAL transaction returns the money; the original is marked REVERSED. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Transaction refundHeld(UUID transactionId, UUID holdId, String description) {
        Transaction original = transactions.findById(transactionId).orElseThrow(() -> new IllegalStateException("Transaction missing"));
        TransactionStateMachine.require(original.getStatus(), TransactionStatus.REVERSED);
        UUID senderId = transactions.findSenderAccountId(transactionId).orElseThrow();
        Money amount = Money.ofKobo(original.getAmountKobo());

        Map<UUID, Account> locked = lockInOrder(holdId, senderId);
        Account hold = locked.get(holdId);
        Account sender = locked.get(senderId);

        Transaction reversal = transactions.save(Transaction.start(
                references.next(), hold, sender, amount.kobo(), TransactionType.REVERSAL, description));
        reversal.linkTo(original.getId());
        move(reversal, hold, sender, amount);
        reversal.transition(TransactionStatus.SUCCESSFUL);
        original.transition(TransactionStatus.REVERSED);
        return reversal;
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
        Account from = current < target.kobo() ? system : user;
        Account to = current < target.kobo() ? user : system;
        Money diff = Money.ofKobo(Math.abs(target.kobo() - current));

        Transaction tx = transactions.save(Transaction.start(references.next(), from, to, diff.kobo(), type, description));
        move(tx, from, to, diff);
        tx.transition(TransactionStatus.SUCCESSFUL);
        return Optional.of(tx);
    }

    /** One DEBIT and one CREDIT, balances updated, both entries recorded against `tx`. */
    private void move(Transaction tx, Account from, Account to, Money amount) {
        from.debit(amount.kobo());
        to.credit(amount.kobo());
        entries.save(new LedgerEntry(tx, from, EntryType.DEBIT, amount.kobo(), from.getBalanceKobo()));
        entries.save(new LedgerEntry(tx, to, EntryType.CREDIT, amount.kobo(), to.getBalanceKobo()));
    }

    private static void requirePositive(Money amount) {
        if (amount.isZero()) {
            throw new IllegalArgumentException("Ledger amount must be greater than zero");
        }
    }

    /** Checked AFTER locking, so no one can spend the same money between check and debit. */
    private static void requireFunds(Account sender, Money amount) {
        if (sender.getAccountType() == AccountType.USER && sender.getBalanceKobo() < amount.kobo()) {
            throw new NepOpayException(ErrorCode.INSUFFICIENT_BALANCE,
                    "Insufficient balance. Your available balance is "
                            + Money.ofKobo(sender.getBalanceKobo()) + ".");
        }
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
