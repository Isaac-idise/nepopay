package com.nepopay.transfer;

import com.nepopay.account.AccountRef;
import com.nepopay.account.AccountRepository;
import com.nepopay.account.AccountStatus;
import com.nepopay.audit.AuditService;
import com.nepopay.common.ErrorCode;
import com.nepopay.common.Money;
import com.nepopay.common.NepOpayException;
import com.nepopay.ledger.LedgerService;
import com.nepopay.notification.NotificationService;
import com.nepopay.notification.NotificationType;
import com.nepopay.transaction.Transaction;
import com.nepopay.transaction.TransactionRepository;
import com.nepopay.transaction.TransactionType;
import com.nepopay.user.User;
import com.nepopay.user.UserRepository;
import com.nepopay.user.UserStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The money step: ONE database transaction. Any exception rolls back debit, credit, entries and notifications. */
@Service
public class TransferProcessor {

    private static final ZoneId LAGOS = ZoneId.of("Africa/Lagos");

    private final UserRepository users;
    private final AccountRepository accounts;
    private final TransactionRepository transactions;
    private final LedgerService ledger;
    private final NotificationService notifications;
    private final AuditService audit;
    private final IdempotencyService idempotency;
    private final TransferLimits limits;

    public TransferProcessor(UserRepository users, AccountRepository accounts, TransactionRepository transactions,
                             LedgerService ledger, NotificationService notifications, AuditService audit,
                             IdempotencyService idempotency, TransferLimits limits) {
        this.users = users;
        this.accounts = accounts;
        this.transactions = transactions;
        this.ledger = ledger;
        this.notifications = notifications;
        this.audit = audit;
        this.idempotency = idempotency;
        this.limits = limits;
    }

    @Transactional
    public TransferResponse execute(UUID senderUserId, TransferRequest req, String idempotencyKey) {
        // Lock the sender's user row first: one sender's transfers run one at a time, so the daily-limit
        // check always sees every earlier committed transfer. (Account rows are locked later, by the ledger.)
        User sender = users.findByIdForUpdate(senderUserId)
                .orElseThrow(() -> new NepOpayException(ErrorCode.UNAUTHORIZED));
        if (sender.getStatus() != UserStatus.ACTIVE) {
            throw new NepOpayException(ErrorCode.ACCOUNT_SUSPENDED);
        }

        String handle = req.recipientUsername().trim();
        if (handle.startsWith("@")) {
            handle = handle.substring(1);
        }
        // Same error for "no such user" and "suspended user": do not reveal which.
        User recipient = users.findByUsernameIgnoreCase(handle)
                .filter(u -> u.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new NepOpayException(ErrorCode.RECIPIENT_UNAVAILABLE));
        if (recipient.getId().equals(sender.getId())) {
            throw new NepOpayException(ErrorCode.SELF_TRANSFER);
        }

        // Scalar lookups only: Account ENTITIES must first be loaded by the ledger's FOR UPDATE read.
        AccountRef from = accounts.findUserAccountRef(sender.getId())
                .orElseThrow(() -> new NepOpayException(ErrorCode.FORBIDDEN, "You do not have an active account."));
        AccountRef to = accounts.findUserAccountRef(recipient.getId())
                .filter(a -> a.status() == AccountStatus.ACTIVE)
                .orElseThrow(() -> new NepOpayException(ErrorCode.RECIPIENT_UNAVAILABLE));
        if (from.status() != AccountStatus.ACTIVE) {
            throw new NepOpayException(ErrorCode.FORBIDDEN, "Your account is not active.");
        }

        Money amount = Money.ofKobo(req.amountKobo());
        checkLimits(from.id(), amount);

        String description = req.description() == null ? null : req.description().trim();
        Transaction tx = ledger.post(from.id(), to.id(), amount, TransactionType.P2P_TRANSFER, description);

        notifications.send(sender, NotificationType.TRANSFER_SENT, "Transfer successful",
                "You sent " + amount + " to " + recipient.getDisplayName() + " (@" + recipient.getUsername()
                        + "). Reference: " + tx.getReference());
        notifications.send(recipient, NotificationType.TRANSFER_RECEIVED, "Money received",
                "You received " + amount + " from " + sender.getDisplayName() + " (@" + sender.getUsername()
                        + "). Reference: " + tx.getReference());
        audit.record(sender.getId(), "TRANSFER", "TRANSACTION", tx.getReference(),
                Map.of("amountKobo", amount.kobo(), "recipient", recipient.getUsername()));

        idempotency.complete(senderUserId, idempotencyKey, tx.getReference()); // commits WITH the money

        return toResponse(tx, recipient, false);
    }

    /** Result of a request that already succeeded earlier (same idempotency key). */
    @Transactional(readOnly = true)
    public TransferResponse replay(String reference) {
        Transaction tx = transactions.findByReference(reference)
                .orElseThrow(() -> new IllegalStateException("Transaction missing for idempotent replay"));
        return toResponse(tx, tx.getReceiver().getUser(), true);
    }

    private void checkLimits(UUID fromAccountId, Money amount) {
        if (amount.kobo() < limits.minTransferKobo()) {
            throw new NepOpayException(ErrorCode.VALIDATION_ERROR,
                    "Minimum transfer is " + Money.ofKobo(limits.minTransferKobo()) + ".");
        }
        if (amount.kobo() > limits.perTransferKobo()) {
            throw new NepOpayException(ErrorCode.LIMIT_EXCEEDED,
                    "Maximum per transfer is " + Money.ofKobo(limits.perTransferKobo()) + ".");
        }
        Instant startOfToday = LocalDate.now(LAGOS).atStartOfDay(LAGOS).toInstant();
        long sentToday = transactions.sumSentSince(fromAccountId, startOfToday);
        if (sentToday + amount.kobo() > limits.dailyKobo()) {
            long left = Math.max(0, limits.dailyKobo() - sentToday);
            throw new NepOpayException(ErrorCode.LIMIT_EXCEEDED,
                    "Daily transfer limit of " + Money.ofKobo(limits.dailyKobo())
                            + " would be exceeded. You have " + Money.ofKobo(left) + " left today.");
        }
    }

    private TransferResponse toResponse(Transaction tx, User recipient, boolean replay) {
        return new TransferResponse(tx.getReference(), tx.getStatus().name(), tx.getAmountKobo(),
                recipient.getUsername(), recipient.getDisplayName(), tx.getDescription(),
                tx.getCreatedAt(), replay);
    }
}
