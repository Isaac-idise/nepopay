package com.nepopay.fraud;

import com.nepopay.common.Money;
import java.time.Instant;
import java.util.UUID;

/** Everything a rule is allowed to know about the transfer being scored. */
public record TransferContext(
        UUID senderUserId,
        UUID senderAccountId,
        UUID recipientUserId,
        UUID recipientAccountId,
        Money amount,
        long senderBalanceKobo,
        Instant senderAccountCreatedAt,
        Instant now) {
}
