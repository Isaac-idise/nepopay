package com.nepopay.transfer;

import java.time.Instant;

/** status SUCCESSFUL = delivered. status FLAGGED = money is held for a safety review (HTTP 202). */
public record TransferResponse(
        String reference,
        String status,
        long amountKobo,
        String recipientUsername,
        String recipientDisplayName,
        String description,
        Instant createdAt,
        boolean idempotentReplay,
        String message) {
}
