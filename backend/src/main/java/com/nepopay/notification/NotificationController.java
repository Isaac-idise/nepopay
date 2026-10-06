package com.nepopay.notification;

import com.nepopay.auth.CurrentUser;
import com.nepopay.common.ApiResponse;
import com.nepopay.common.PageResponse;
import com.nepopay.common.Paging;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    public record NotificationView(UUID id, String title, String message, String type, boolean read, Instant createdAt) {
    }

    public record UnreadCount(long unread) {
    }

    public record Updated(int updated) {
    }

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<PageResponse<NotificationView>> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var result = service.list(CurrentUser.id(jwt), Paging.of(page, size))
                .map(n -> new NotificationView(n.getId(), n.getTitle(), n.getMessage(),
                        n.getType().name(), n.isRead(), n.getCreatedAt()));
        return ApiResponse.ok(PageResponse.of(result));
    }

    @GetMapping("/unread-count")
    public ApiResponse<UnreadCount> unread(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(new UnreadCount(service.unreadCount(CurrentUser.id(jwt))));
    }

    @PostMapping("/{id}/read")
    public ApiResponse<Void> markRead(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        service.markRead(CurrentUser.id(jwt), id);
        return ApiResponse.<Void>ok(null);
    }

    @PostMapping("/read-all")
    public ApiResponse<Updated> readAll(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(new Updated(service.markAllRead(CurrentUser.id(jwt))));
    }
}
