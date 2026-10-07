package com.nepopay.fraud;

import com.nepopay.account.AccountRepository;
import com.nepopay.account.AccountType;
import com.nepopay.audit.AuditService;
import com.nepopay.common.ErrorCode;
import com.nepopay.common.Money;
import com.nepopay.common.NepOpayException;
import com.nepopay.fraud.FraudDtos.AlertView;
import com.nepopay.ledger.LedgerService;
import com.nepopay.notification.NotificationService;
import com.nepopay.notification.NotificationType;
import com.nepopay.transaction.Transaction;
import com.nepopay.user.User;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** An admin decides what happens to a held transfer. Each decision is one atomic transaction. */
@Service
public class FraudReviewService {

    private final FraudAlertRepository alerts;
    private final FraudAlertQueryService query;
    private final AccountRepository accounts;
    private final LedgerService ledger;
    private final NotificationService notifications;
    private final AuditService audit;

    public FraudReviewService(FraudAlertRepository alerts, FraudAlertQueryService query, AccountRepository accounts,
                              LedgerService ledger, NotificationService notifications, AuditService audit) {
        this.alerts = alerts;
        this.query = query;
        this.accounts = accounts;
        this.ledger = ledger;
        this.notifications = notifications;
        this.audit = audit;
    }

    /** Release the held money to the receiver. */
    @Transactional
    public AlertView approve(UUID adminId, UUID alertId, String note) {
        FraudAlert alert = openAlertForUpdate(alertId);
        ledger.settleHeld(alert.getTransactionId(), holdAccountId());
        alert.review(AlertStatus.APPROVED, adminId, clean(note));

        Transaction tx = alert.getTransaction();
        User sender = tx.getSender().getUser();
        User receiver = tx.getReceiver().getUser();
        Money amount = Money.ofKobo(tx.getAmountKobo());
        notifications.send(sender, NotificationType.TRANSFER_SENT, "Transfer approved",
                "Your transfer of " + amount + " to " + receiver.getDisplayName() + " (@" + receiver.getUsername()
                        + ") was approved and delivered. Reference: " + tx.getReference());
        notifications.send(receiver, NotificationType.TRANSFER_RECEIVED, "Money received",
                "You received " + amount + " from " + sender.getDisplayName() + " (@" + sender.getUsername()
                        + "). Reference: " + tx.getReference());
        audit.record(adminId, "FRAUD_APPROVED", "FRAUD_ALERT", alertId.toString(), Map.of("transaction", tx.getReference()));
        return query.view(alert);
    }

    /** Return the held money to the sender (as a REVERSAL transaction). */
    @Transactional
    public AlertView reject(UUID adminId, UUID alertId, String note) {
        if (note == null || note.isBlank()) {
            throw new NepOpayException(ErrorCode.VALIDATION_ERROR, "A reason is required to reject a transfer.");
        }
        FraudAlert alert = openAlertForUpdate(alertId);
        ledger.refundHeld(alert.getTransactionId(), holdAccountId(), "Reversal of held transfer");
        alert.review(AlertStatus.REJECTED, adminId, clean(note));

        Transaction tx = alert.getTransaction();
        User sender = tx.getSender().getUser();
        User receiver = tx.getReceiver().getUser();
        notifications.send(sender, NotificationType.FRAUD_ALERT, "Transfer declined",
                "Your transfer of " + Money.ofKobo(tx.getAmountKobo()) + " to " + receiver.getDisplayName() + " (@"
                        + receiver.getUsername() + ") was declined after review and the money has been returned to "
                        + "your account. Reference: " + tx.getReference());
        audit.record(adminId, "FRAUD_REJECTED", "FRAUD_ALERT", alertId.toString(), Map.of("transaction", tx.getReference()));
        return query.view(alert);
    }

    private FraudAlert openAlertForUpdate(UUID alertId) {
        FraudAlert alert = alerts.findByIdForUpdate(alertId)
                .orElseThrow(() -> new NepOpayException(ErrorCode.NOT_FOUND, "Alert not found."));
        if (alert.getStatus() != AlertStatus.OPEN) {
            throw new NepOpayException(ErrorCode.ALERT_ALREADY_REVIEWED);
        }
        return alert;
    }

    private UUID holdAccountId() {
        return accounts.findIdByAccountType(AccountType.SYSTEM_HOLD)
                .orElseThrow(() -> new IllegalStateException("SYSTEM_HOLD account is missing"));
    }

    private static String clean(String note) {
        return note == null || note.isBlank() ? null : note.trim();
    }
}
