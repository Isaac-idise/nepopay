package com.nepopay.admin;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class AdminDtos {

    private AdminDtos() {
    }

    // ---- users ----
    public record AdminUserView(String username, String displayName, String email, String phone, String role,
                                String status, Instant createdAt, String accountNumber, Long balanceKobo) {
    }

    public record StatusChangeRequest(@NotBlank String status, @Size(max = 200) String reason) {
    }

    // ---- transactions ----
    public record AdminTransactionView(String reference, String type, String status, long amountKobo, int riskScore,
                                       String sender, String receiver, String description, Instant createdAt) {
    }

    public record AdminEntryView(String account, String entryType, long amountKobo, long balanceAfterKobo, Instant createdAt) {
    }

    public record AdminTransactionDetail(AdminTransactionView transaction, List<AdminEntryView> ledgerEntries) {
    }

    // ---- audit ----
    public record AuditView(long id, String action, String username, String entityType, String entityId,
                            JsonNode metadata, Instant createdAt) {
    }

    // ---- dashboard ----
    public record UserStats(long total, long active, long suspended, long admins, long registeredToday) {
    }

    public record TransferStats(long total, long successful, long flagged, long reversed,
                                long failedAttemptsToday, long failedAttemptsAllTime) {
    }

    public record VolumeStats(long todayKobo, long last7DaysKobo, long allTimeKobo) {
    }

    public record FraudStats(long open, long info, long approved, long rejected, long heldKobo) {
    }

    public record Dashboard(UserStats users, TransferStats transfers, VolumeStats volume, FraudStats fraud,
                            List<AuditView> recentAudit, Instant generatedAt) {
    }

    // ---- reconciliation ----
    public record AccountDrift(String accountNumber, String accountType, String username, long balanceKobo, long ledgerKobo) {
    }

    public record UnbalancedTransaction(String reference, long debitsKobo, long creditsKobo) {
    }

    public record ReconciliationReport(boolean ok, Instant checkedAt, long totalBalanceKobo,
                                       List<AccountDrift> accountDrift, List<UnbalancedTransaction> unbalancedTransactions,
                                       long transactionsWithoutEntries, long holdBalanceKobo, long flaggedTotalKobo,
                                       boolean holdMatchesFlagged, long staleIdempotencyKeys) {
    }
}
