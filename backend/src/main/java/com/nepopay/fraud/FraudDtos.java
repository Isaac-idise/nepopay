package com.nepopay.fraud;

import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class FraudDtos {

    private FraudDtos() {
    }

    public record PartyView(String username, String displayName) {
    }

    public record TransactionSummary(String reference, String type, String status, long amountKobo,
                                     String description, Instant createdAt, PartyView sender, PartyView receiver) {
    }

    public record AlertView(UUID id, String status, int riskScore, String riskLevel, List<RuleHit> reasons,
                            TransactionSummary transaction, Instant createdAt, Instant reviewedAt, String reviewNote) {
    }

    public record ReviewRequest(@Size(max = 200) String note) {
    }
}
