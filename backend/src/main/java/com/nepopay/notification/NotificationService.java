package com.nepopay.notification;

import com.nepopay.user.User;
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
}
