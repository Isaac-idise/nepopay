package com.nepopay.transfer;

import java.time.Instant;

public record TransferResponse(
        String reference,
        String status,
        long amountKobo,
        String recipientUsername,
        String recipientDisplayName,
        String description,
        Instant createdAt,
        boolean idempotentReplay) {
}
