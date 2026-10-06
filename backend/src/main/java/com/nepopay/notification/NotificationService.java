package com.nepopay.notification;

import com.nepopay.common.ErrorCode;
import com.nepopay.common.NepOpayException;
import com.nepopay.user.User;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Notification text is stored in plain, professional English. The frontend personality layer
 * can restyle it using the type; the banking engine never depends on Pidgin text.
 */
@Service
public class NotificationService {

    private final NotificationRepository repository;

    public NotificationService(NotificationRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void send(User user, NotificationType type, String title, String message) {
        repository.save(new Notification(user, type, title, message));
    }

    @Transactional(readOnly = true)
    public Page<Notification> list(UUID userId, Pageable pageable) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID userId) {
        return repository.countByUserIdAndReadFalse(userId);
    }

    @Transactional
    public void markRead(UUID userId, UUID notificationId) {
        repository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new NepOpayException(ErrorCode.NOT_FOUND, "Notification not found."))
                .markRead();
    }

    @Transactional
    public int markAllRead(UUID userId) {
        return repository.markAllRead(userId);
    }

    @Transactional
    public int deleteAll(UUID userId) {
        return repository.deleteAllForUser(userId);
    }
}
