package com.nepopay.admin;

import com.nepopay.account.Account;
import com.nepopay.account.AccountRepository;
import com.nepopay.account.AccountType;
import com.nepopay.admin.AdminDtos.AdminUserView;
import com.nepopay.audit.AuditService;
import com.nepopay.auth.SessionService;
import com.nepopay.common.ErrorCode;
import com.nepopay.common.NepOpayException;
import com.nepopay.user.User;
import com.nepopay.user.UserRepository;
import com.nepopay.user.UserRole;
import com.nepopay.user.UserSearchService;
import com.nepopay.user.UserStatus;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminUserService {

    private final UserRepository users;
    private final AccountRepository accounts;
    private final SessionService sessions;
    private final AuditService audit;

    public AdminUserService(UserRepository users, AccountRepository accounts, SessionService sessions, AuditService audit) {
        this.users = users;
        this.accounts = accounts;
        this.sessions = sessions;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Page<AdminUserView> list(String q, String status, Pageable pageable) {
        List<UserStatus> statuses = "ALL".equalsIgnoreCase(status) || status == null
                ? Arrays.asList(UserStatus.values())
                : List.of(parseStatus(status));
        String pattern = "%" + UserSearchService.escapeLike(q == null ? "" : q.trim().toLowerCase(Locale.ROOT)) + "%";
        return users.adminSearch(statuses, pattern, pageable)
                .map(row -> view((User) row[0], (Account) row[1]));
    }

    @Transactional(readOnly = true)
    public AdminUserView get(String username) {
        User u = find(username);
        Account a = accounts.findByUserIdAndAccountType(u.getId(), AccountType.USER).orElse(null);
        return view(u, a);
    }

    /**
     * Suspending blocks login and refresh at once and blocks all money movement immediately (transfers check the
     * database). An access token the person already holds can still READ for up to 15 minutes.
     */
    @Transactional
    public AdminUserView changeStatus(UUID adminId, String username, String newStatus, String reason) {
        UserStatus target = parseStatus(newStatus);
        User found = find(username);
        if (found.getRole() == UserRole.ADMIN) {
            throw new NepOpayException(ErrorCode.FORBIDDEN, "Admin accounts cannot be changed here.");
        }
        if (target == UserStatus.SUSPENDED && (reason == null || reason.isBlank())) {
            throw new NepOpayException(ErrorCode.VALIDATION_ERROR, "A reason is required to suspend an account.");
        }
        User user = users.findByIdForUpdate(found.getId()).orElseThrow();
        if (user.getStatus() != target) {
            user.changeStatus(target);
            if (target == UserStatus.SUSPENDED) {
                sessions.revokeAllForUser(user.getId());
            }
            audit.record(adminId, target == UserStatus.SUSPENDED ? "USER_SUSPENDED" : "USER_REACTIVATED", "USER",
                    user.getUsername(), reason == null || reason.isBlank() ? null : Map.of("reason", reason.trim()));
        }
        Account a = accounts.findByUserIdAndAccountType(user.getId(), AccountType.USER).orElse(null);
        return view(user, a);
    }

    private User find(String username) {
        String handle = username.trim().startsWith("@") ? username.trim().substring(1) : username.trim();
        return users.findByUsernameIgnoreCase(handle)
                .orElseThrow(() -> new NepOpayException(ErrorCode.NOT_FOUND, "User not found."));
    }

    private static UserStatus parseStatus(String s) {
        try {
            return UserStatus.valueOf(s.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new NepOpayException(ErrorCode.VALIDATION_ERROR, "status must be ACTIVE or SUSPENDED (or ALL when listing).");
        }
    }

    private static AdminUserView view(User u, Account a) {
        return new AdminUserView(u.getUsername(), u.getDisplayName(), u.getEmail(), u.getPhone(), u.getRole().name(),
                u.getStatus().name(), u.getCreatedAt(),
                a == null ? null : a.getAccountNumber(), a == null ? null : a.getBalanceKobo());
    }
}
