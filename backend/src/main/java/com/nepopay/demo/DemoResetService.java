package com.nepopay.demo;

import com.nepopay.account.AccountRef;
import com.nepopay.account.AccountRepository;
import com.nepopay.account.AccountService;
import com.nepopay.account.AccountType;
import com.nepopay.audit.AuditService;
import com.nepopay.beneficiary.BeneficiaryService;
import com.nepopay.common.ErrorCode;
import com.nepopay.common.NepOpayException;
import com.nepopay.ledger.LedgerService;
import com.nepopay.notification.NotificationService;
import com.nepopay.notification.NotificationType;
import com.nepopay.transaction.Transaction;
import com.nepopay.transaction.TransactionType;
import com.nepopay.user.PinService;
import com.nepopay.user.User;
import com.nepopay.user.UserRepository;
import com.nepopay.user.UserStatus;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Puts a demo account back to its starting state WITHOUT rewriting history:
 *  - the balance is corrected by a normal DEMO_RESET ledger transaction against SYSTEM_FUNDING,
 *  - old transactions are hidden from THIS account's history only (the other person's history is untouched),
 *  - beneficiaries and notifications are cleared; the audit log is kept.
 * Everything is simulated money, and the ledger stays balanced.
 */
@Service
public class DemoResetService {

    private final PinService pins;
    private final TransactionTemplate tx;
    private final UserRepository users;
    private final AccountRepository accounts;
    private final AccountService accountService;
    private final LedgerService ledger;
    private final DemoFundingService demoFunding;
    private final BeneficiaryService beneficiaries;
    private final NotificationService notifications;
    private final AuditService audit;

    public DemoResetService(PinService pins, TransactionTemplate tx, UserRepository users,
                            AccountRepository accounts, AccountService accountService, LedgerService ledger,
                            DemoFundingService demoFunding, BeneficiaryService beneficiaries,
                            NotificationService notifications, AuditService audit) {
        this.pins = pins;
        this.tx = tx;
        this.users = users;
        this.accounts = accounts;
        this.accountService = accountService;
        this.ledger = ledger;
        this.demoFunding = demoFunding;
        this.beneficiaries = beneficiaries;
        this.notifications = notifications;
        this.audit = audit;
    }

    public DemoResetResponse reset(UUID userId, String pin) {
        pins.verify(userId, pin); // a reset is deliberate: it needs the PIN, with the usual lockout
        return tx.execute(status -> doReset(userId));
    }

    private DemoResetResponse doReset(UUID userId) {
        User user = users.findByIdForUpdate(userId)
                .orElseThrow(() -> new NepOpayException(ErrorCode.UNAUTHORIZED));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new NepOpayException(ErrorCode.ACCOUNT_SUSPENDED);
        }
        AccountRef account = accounts.findUserAccountRef(userId)
                .orElseThrow(() -> new NepOpayException(ErrorCode.FORBIDDEN, "You do not have an active account."));
        UUID fundingId = accounts.findIdByAccountType(AccountType.SYSTEM_FUNDING)
                .orElseThrow(() -> new IllegalStateException("SYSTEM_FUNDING account is missing"));

        Instant marker = Instant.now(); // taken BEFORE posting, so the reset entry itself stays visible
        Optional<Transaction> adjustment = ledger.adjustBalanceTo(account.id(), fundingId,
                demoFunding.welcomeAmount(), TransactionType.DEMO_RESET, "Demo account reset");
        accountService.markHistoryReset(account.id(), marker);

        beneficiaries.deleteAll(userId);
        notifications.deleteAll(userId);
        notifications.send(user, NotificationType.SYSTEM, "Demo account reset",
                "Your demo account was reset to " + demoFunding.welcomeAmount() + ".");
        audit.record(userId, "DEMO_RESET", "ACCOUNT", null,
                Map.of("adjustmentKobo", adjustment.map(Transaction::getAmountKobo).orElse(0L)));

        return new DemoResetResponse(demoFunding.welcomeAmount().kobo(),
                adjustment.map(Transaction::getReference).orElse(null));
    }
}
