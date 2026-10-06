package com.nepopay.transaction;

import java.time.Instant;
import java.util.List;

public final class TransactionDtos {

    private TransactionDtos() {
    }

    /** The other side of a transaction. username is null for NepOpay itself (e.g. demo funds). */
    public record Party(String username, String displayName) {
    }

    public record TransactionView(String reference, String type, String status, String direction,
                                  long amountKobo, Party counterparty, String description, Instant createdAt) {
    }

    public record EntryView(String entryType, long amountKobo, long balanceAfterKobo, Instant createdAt) {
    }

    public record TransactionDetail(String reference, String type, String status, String direction,
                                    long amountKobo, Party counterparty, String description, Instant createdAt,
                                    List<EntryView> ledgerEntries) {
    }
}
