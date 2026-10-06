package com.nepopay.auth;

import com.nepopay.account.Account;
import com.nepopay.account.AccountService;
import com.nepopay.audit.AuditService;
import com.nepopay.common.ErrorCode;
import com.nepopay.common.NepOpayException;
import com.nepopay.demo.DemoFundingService;
import com.nepopay.notification.NotificationService;
import com.nepopay.notification.NotificationType;
import com.nepopay.transaction.Transaction;
import com.nepopay.user.PhoneNumbers;
import com.nepopay.user.User;
import com.nepopay.user.UserRepository;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationService {

    private static final Set<String> RESERVED_USERNAMES =
            Set.of("admin", "administrator", "nepopay", "support", "system", "root", "help", "security");

    private final UserRepository users;
    private final AccountService accountService;
    private final DemoFundingService demoFunding;
    private final NotificationService notifications;
    private final AuditService audit;
    private final PasswordEncoder encoder;

    public RegistrationService(UserRepository users, AccountService accountService,
                               DemoFundingService demoFunding, NotificationService notifications,
                               AuditService audit, PasswordEncoder encoder) {
        this.users = users;
        this.accountService = accountService;
        this.demoFunding = demoFunding;
        this.notifications = notifications;
        this.audit = audit;
        this.encoder = encoder;
    }

    /** Everything below is ONE database transaction: either the whole account exists, funded, or nothing does. */
    @Transactional
    public RegisterResponse register(RegisterRequest req) {
        String username = req.username().trim().toLowerCase(Locale.ROOT);
        String email = req.email().trim().toLowerCase(Locale.ROOT);
        String phone = PhoneNumbers.normalize(req.phone());

        if (RESERVED_USERNAMES.contains(username) || users.existsByUsernameIgnoreCase(username)) {
            throw new NepOpayException(ErrorCode.USERNAME_TAKEN);
        }
        if (users.existsByEmailIgnoreCase(email)) {
            throw new NepOpayException(ErrorCode.EMAIL_TAKEN);
        }
        if (users.existsByPhone(phone)) {
            throw new NepOpayException(ErrorCode.PHONE_TAKEN);
        }

        User user = users.save(User.newCustomer(
                username, req.firstName().trim(), req.lastName().trim(), email, phone,
                encoder.encode(req.password()), encoder.encode(req.pin())));

        Account account = accountService.openUserAccount(user);
        Transaction welcome = demoFunding.creditWelcomeFunds(account);

        notifications.send(user, NotificationType.WELCOME, "Welcome to NepOpay",
                "Your account is ready. We've credited " + demoFunding.welcomeAmount()
                        + " in demo funds. All money in NepOpay is simulated.");
        audit.record(user.getId(), "REGISTER", "USER", user.getId().toString(),
                Map.of("username", username));

        return new RegisterResponse(username, user.getDisplayName(), account.getAccountNumber(),
                account.getBalanceKobo(), "NGN", welcome.getReference());
    }
}
