package com.nepopay.user;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String phone;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String pinHash;

    @Column(nullable = false)
    private int pinFailedAttempts;

    private Instant pinLockedUntil;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role = UserRole.USER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status = UserStatus.ACTIVE;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected User() {
    }

    public static User newCustomer(String username, String firstName, String lastName, String email,
                                   String phone, String passwordHash, String pinHash) {
        User u = new User();
        u.username = username;
        u.firstName = firstName;
        u.lastName = lastName;
        u.email = email;
        u.phone = phone;
        u.passwordHash = passwordHash;
        u.pinHash = pinHash;
        return u;
    }

    public boolean isPinLocked(Instant now) {
        return pinLockedUntil != null && pinLockedUntil.isAfter(now);
    }

    /** After a lock has expired the slate is wiped clean. */
    public void clearExpiredPinLock(Instant now) {
        if (pinLockedUntil != null && !pinLockedUntil.isAfter(now)) {
            pinLockedUntil = null;
            pinFailedAttempts = 0;
        }
    }

    /** Returns the number of consecutive failures; locks the PIN when maxAttempts is reached. */
    public int recordPinFailure(int maxAttempts, Instant lockUntil) {
        pinFailedAttempts++;
        if (pinFailedAttempts >= maxAttempts) {
            pinLockedUntil = lockUntil;
        }
        return pinFailedAttempts;
    }

    public void changePinHash(String newPinHash) {
        this.pinHash = newPinHash;
        resetPinFailures();
    }

    public void resetPinFailures() {
        pinFailedAttempts = 0;
        pinLockedUntil = null;
    }

    /** Admins are never created through public registration; only the startup seeder uses this. */
    public static User newAdmin(String username, String firstName, String lastName, String email,
                                String phone, String passwordHash, String pinHash) {
        User u = newCustomer(username, firstName, lastName, email, phone, passwordHash, pinHash);
        u.role = UserRole.ADMIN;
        return u;
    }

    public void changePasswordHash(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
    }

    public void changeStatus(UserStatus newStatus) {
        this.status = newStatus;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getUsername() { return username; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getPasswordHash() { return passwordHash; }
    public String getPinHash() { return pinHash; }
    public int getPinFailedAttempts() { return pinFailedAttempts; }
    public Instant getPinLockedUntil() { return pinLockedUntil; }
    public UserRole getRole() { return role; }
    public UserStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public String getDisplayName() { return firstName + " " + lastName; }
}
