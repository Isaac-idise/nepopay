package com.nepopay.user;

import com.nepopay.audit.AuditService;
import com.nepopay.common.ErrorCode;
import com.nepopay.common.NepOpayException;
import com.nepopay.notification.NotificationService;
import com.nepopay.notification.NotificationType;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transaction PIN check with lockout. Runs in its OWN transaction and commits failures
 * (noRollbackFor), so a wrong guess is counted even though the request fails.
 */
@Service
public class PinService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final NotificationService notifications;
    private final int maxAttempts;
    private final long lockMinutes;

    public PinService(UserRepository users, PasswordEncoder encoder, AuditService audit,
                      NotificationService notifications,
                      @Value("${nepopay.pin.max-failed-attempts:3}") int maxAttempts,
                      @Value("${nepopay.pin.lock-minutes:15}") long lockMinutes) {
        this.users = users;
        this.encoder = encoder;
        this.audit = audit;
        this.notifications = notifications;
        this.maxAttempts = maxAttempts;
        this.lockMinutes = lockMinutes;
    }

    @Transactional(noRollbackFor = NepOpayException.class)
    public void verify(UUID userId, String rawPin) {
        check(lock(userId), rawPin);
    }

    /** Changing the PIN requires the current one, with the same lockout rules as a transfer. */
    @Transactional(noRollbackFor = NepOpayException.class)
    public void change(UUID userId, String currentPin, String newPin) {
        User user = lock(userId);
        check(user, currentPin);
        if (encoder.matches(newPin, user.getPinHash())) {
            throw new NepOpayException(ErrorCode.VALIDATION_ERROR, "New PIN must be different from the current PIN.");
        }
        user.changePinHash(encoder.encode(newPin));
        audit.record(userId, "PIN_CHANGED", "USER", userId.toString(), null);
        notifications.send(user, NotificationType.SECURITY, "Transaction PIN changed",
                "Your transaction PIN was changed. If this was not you, contact support.");
    }

    private User lock(UUID userId) {
        // Row lock: two parallel guesses cannot both read "0 failures" and dodge the counter.
        return users.findByIdForUpdate(userId).orElseThrow(() -> new NepOpayException(ErrorCode.UNAUTHORIZED));
    }

    private void check(User user, String rawPin) {
        Instant now = Instant.now();
        user.clearExpiredPinLock(now);

        if (user.isPinLocked(now)) {
            throw locked(user, now);
        }
        if (encoder.matches(rawPin, user.getPinHash())) {
            user.resetPinFailures();
            return;
        }

        int attempts = user.recordPinFailure(maxAttempts, now.plus(lockMinutes, ChronoUnit.MINUTES));
        audit.record(user.getId(), "PIN_FAILED", "USER", user.getId().toString(),
                Map.of("consecutiveFailures", attempts));
        if (user.isPinLocked(now)) {
            audit.record(user.getId(), "PIN_LOCKED", "USER", user.getId().toString(), null);
            throw locked(user, now);
        }
        throw new NepOpayException(ErrorCode.INVALID_PIN,
                "Invalid transaction PIN. " + (maxAttempts - attempts) + " attempt(s) remaining.");
    }

    private NepOpayException locked(User user, Instant now) {
        long seconds = Math.max(1, Duration.between(now, user.getPinLockedUntil()).toSeconds());
        long minutes = (seconds + 59) / 60;
        return new NepOpayException(ErrorCode.PIN_LOCKED,
                "Transfers are locked after too many wrong PINs. Try again in about " + minutes + " minute(s).");
    }
}
