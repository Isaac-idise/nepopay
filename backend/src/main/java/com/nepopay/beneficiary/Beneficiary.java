package com.nepopay.beneficiary;

import com.nepopay.account.Account;
import com.nepopay.user.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "beneficiaries")
public class Beneficiary {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "beneficiary_account_id")
    private Account beneficiaryAccount;

    private String nickname;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Beneficiary() {
    }

    public Beneficiary(User user, Account beneficiaryAccount, String nickname) {
        this.user = user;
        this.beneficiaryAccount = beneficiaryAccount;
        this.nickname = nickname;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public Account getBeneficiaryAccount() { return beneficiaryAccount; }
    public String getNickname() { return nickname; }
    public Instant getCreatedAt() { return createdAt; }
}
