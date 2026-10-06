package com.nepopay.auth;

import com.nepopay.audit.AuditService;
import com.nepopay.common.ErrorCode;
import com.nepopay.common.NepOpayException;
import com.nepopay.user.User;
import com.nepopay.user.UserRepository;
import com.nepopay.user.UserStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    public record AuthResult(TokenResponse body, String refreshToken) {
    }

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final TokenService tokens;
    private final SessionService sessions;
    private final AuditService audit;
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder encoder, TokenService tokens,
                       SessionService sessions, AuditService audit) {
        this.users = users;
        this.encoder = encoder;
        this.tokens = tokens;
        this.sessions = sessions;
        this.audit = audit;
        this.dummyHash = encoder.encode("not-a-real-password");
    }

    /**
     * noRollbackFor: a failed login throws, but its audit record must still be saved.
     */
    @Transactional(noRollbackFor = NepOpayException.class)
    public AuthResult login(LoginRequest req, String userAgent) {
        String id = req.identifier().trim();
        Optional<User> found = id.contains("@")
                ? users.findByEmailIgnoreCase(id)
                : users.findByUsernameIgnoreCase(id);

        // Always run one BCrypt comparison, even for unknown users, so response time does not reveal who exists.
        boolean passwordOk = encoder.matches(req.password(), found.map(User::getPasswordHash).orElse(dummyHash));

        if (found.isEmpty() || !passwordOk) {
            // The typed identifier is NOT logged: people sometimes paste passwords into the wrong box.
            audit.record(found.map(User::getId).orElse(null), "LOGIN_FAILED", "USER",
                    found.map(u -> u.getId().toString()).orElse(null), null);
            throw new NepOpayException(ErrorCode.INVALID_CREDENTIALS);
        }
        User user = found.get();
        if (user.getStatus() != UserStatus.ACTIVE) {
            audit.record(user.getId(), "LOGIN_BLOCKED", "USER", user.getId().toString(), null);
            throw new NepOpayException(ErrorCode.ACCOUNT_SUSPENDED);
        }

        String refresh = sessions.start(user.getId(), UUID.randomUUID(), userAgent);
        audit.record(user.getId(), "LOGIN", "USER", user.getId().toString(), null);
        return new AuthResult(body(user), refresh);
    }

    @Transactional(noRollbackFor = NepOpayException.class)
    public AuthResult refresh(String rawRefreshToken, String userAgent) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new NepOpayException(ErrorCode.SESSION_EXPIRED);
        }
        SessionService.Rotation rotation = sessions.rotate(rawRefreshToken, userAgent);
        User user = users.findById(rotation.userId())
                .orElseThrow(() -> new NepOpayException(ErrorCode.SESSION_EXPIRED));
        if (user.getStatus() != UserStatus.ACTIVE) {
            sessions.revokeFamilyOf(rotation.newRawToken());
            throw new NepOpayException(ErrorCode.ACCOUNT_SUSPENDED);
        }
        return new AuthResult(body(user), rotation.newRawToken());
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            sessions.revokeFamilyOf(rawRefreshToken);
        }
    }

    private TokenResponse body(User user) {
        TokenService.AccessToken at = tokens.issue(user);
        return new TokenResponse(at.value(), "Bearer", at.expiresInSeconds(),
                user.getUsername(), user.getDisplayName(), user.getRole().name());
    }
}
