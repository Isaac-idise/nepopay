package com.nepopay.auth;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** One refresh token. Only its SHA-256 hash is stored, so a database leak does not hand out live sessions. */
@Entity
@Table(name = "sessions")
public class AuthSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, updatable = false)
    private UUID familyId;

    @Column(nullable = false, unique = true, updatable = false)
    private String tokenHash;

    private String userAgent;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false, updatable = false)
    private Instant expiresAt;

    private Instant revokedAt;

    protected AuthSession() {
    }

    public AuthSession(UUID userId, UUID familyId, String tokenHash, Instant expiresAt, String userAgent) {
        this.userId = userId;
        this.familyId = familyId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.userAgent = userAgent;
        this.createdAt = Instant.now();
    }

    public boolean isRevoked() { return revokedAt != null; }
    public boolean isExpired(Instant now) { return !expiresAt.isAfter(now); }
    public void revoke(Instant now) { this.revokedAt = now; }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public UUID getFamilyId() { return familyId; }
}
