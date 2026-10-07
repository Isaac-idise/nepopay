package com.nepopay.auth;

import com.nepopay.audit.AuditService;
import com.nepopay.common.ErrorCode;
import com.nepopay.common.NepOpayException;
import com.nepopay.config.SecurityProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Refresh-token sessions with rotation: every refresh kills the old token and issues a new one.
 * If an already-used token shows up again, someone copied it, so the whole family is revoked.
 */
@Service
public class SessionService {

    public record Rotation(UUID userId, String newRawToken) {
    }

    private final SecureRandom random = new SecureRandom();
    private final AuthSessionRepository sessions;
    private final SecurityProperties props;
    private final AuditService audit;

    public SessionService(AuthSessionRepository sessions, SecurityProperties props, AuditService audit) {
        this.sessions = sessions;
        this.props = props;
        this.audit = audit;
    }

    /** Returns the RAW token (shown to the client once); only its hash is saved. */
    @Transactional
    public String start(UUID userId, UUID familyId, String userAgent) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        sessions.save(new AuthSession(userId, familyId, sha256(raw),
                Instant.now().plus(props.refreshTokenDays(), ChronoUnit.DAYS), truncate(userAgent)));
        return raw;
    }

    @Transactional
    public Rotation rotate(String rawToken, String userAgent) {
        AuthSession session = sessions.findByTokenHashForUpdate(sha256(rawToken))
                .orElseThrow(() -> new NepOpayException(ErrorCode.SESSION_EXPIRED));
        Instant now = Instant.now();

        if (session.isRevoked()) {
            sessions.revokeFamily(session.getFamilyId(), now);
            audit.record(session.getUserId(), "TOKEN_REUSE_DETECTED", "SESSION",
                    session.getFamilyId().toString(), null);
            throw new NepOpayException(ErrorCode.SESSION_EXPIRED);
        }
        if (session.isExpired(now)) {
            throw new NepOpayException(ErrorCode.SESSION_EXPIRED);
        }

        session.revoke(now);
        String next = start(session.getUserId(), session.getFamilyId(), userAgent);
        return new Rotation(session.getUserId(), next);
    }

    @Transactional
    public void revokeFamilyOf(String rawToken) {
        sessions.findByTokenHashForUpdate(sha256(rawToken)).ifPresent(s -> {
            sessions.revokeFamily(s.getFamilyId(), Instant.now());
            audit.record(s.getUserId(), "LOGOUT", "SESSION", s.getFamilyId().toString(), null);
        });
    }

    @Transactional
    public void revokeFamily(UUID familyId) {
        sessions.revokeFamily(familyId, Instant.now());
    }

    /** Used on password change and when an admin suspends someone: every refresh token dies. */
    @Transactional
    public void revokeAllForUser(UUID userId) {
        sessions.revokeAllForUser(userId, Instant.now());
    }

    static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String truncate(String s) {
        return s == null ? null : (s.length() > 255 ? s.substring(0, 255) : s);
    }
}
