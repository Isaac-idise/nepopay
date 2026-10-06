package com.nepopay.user;

import com.nepopay.audit.AuditService;
import com.nepopay.common.ErrorCode;
import com.nepopay.common.NepOpayException;
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
    private final int maxAttempts;
    private final long lockMinutes;

    public PinService(UserRepository users, PasswordEncoder encoder, AuditService audit,
                      @Value("${nepopay.pin.max-failed-attempts:3}") int maxAttempts,
                      @Value("${nepopay.pin.lock-minutes:15}") long lockMinutes) {
        this.users = users;
        this.encoder = encoder;
        this.audit = audit;
        this.maxAttempts = maxAttempts;
        this.lockMinutes = lockMinutes;
    }

    @Transactional(noRollbackFor = NepOpayException.class)
    public void verify(UUID userId, String rawPin) {
        // Row lock: two parallel guesses cannot both read "0 failures" and dodge the counter.
        User user = users.findByIdForUpdate(userId)
                .orElseThrow(() -> new NepOpayException(ErrorCode.UNAUTHORIZED));
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
        audit.record(userId, "PIN_FAILED", "USER", userId.toString(), Map.of("consecutiveFailures", attempts));
        if (user.isPinLocked(now)) {
            audit.record(userId, "PIN_LOCKED", "USER", userId.toString(), null);
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
