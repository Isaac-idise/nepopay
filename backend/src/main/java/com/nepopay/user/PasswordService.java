package com.nepopay.user;

import com.nepopay.audit.AuditService;
import com.nepopay.auth.SessionService;
import com.nepopay.common.ErrorCode;
import com.nepopay.common.NepOpayException;
import com.nepopay.notification.NotificationService;
import com.nepopay.notification.NotificationType;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final SessionService sessions;
    private final AuditService audit;
    private final NotificationService notifications;

    public PasswordService(UserRepository users, PasswordEncoder encoder, SessionService sessions,
                           AuditService audit, NotificationService notifications) {
        this.users = users;
        this.encoder = encoder;
        this.sessions = sessions;
        this.audit = audit;
        this.notifications = notifications;
    }

    /** noRollbackFor: a failed attempt still leaves its audit record. */
    @Transactional(noRollbackFor = NepOpayException.class)
    public void change(UUID userId, String currentPassword, String newPassword) {
        User user = users.findByIdForUpdate(userId).orElseThrow(() -> new NepOpayException(ErrorCode.UNAUTHORIZED));
        if (!encoder.matches(currentPassword, user.getPasswordHash())) {
            audit.record(userId, "PASSWORD_CHANGE_FAILED", "USER", userId.toString(), null);
            throw new NepOpayException(ErrorCode.INVALID_PASSWORD);
        }
        if (encoder.matches(newPassword, user.getPasswordHash())) {
            throw new NepOpayException(ErrorCode.VALIDATION_ERROR, "New password must be different from the current password.");
        }
        user.changePasswordHash(encoder.encode(newPassword));
        sessions.revokeAllForUser(userId); // every device must log in again
        audit.record(userId, "PASSWORD_CHANGED", "USER", userId.toString(), null);
        notifications.send(user, NotificationType.SECURITY, "Password changed",
                "Your password was changed and you were signed out of all devices. If this was not you, contact support.");
    }
}
