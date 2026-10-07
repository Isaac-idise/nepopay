package com.nepopay.account;

import java.time.Instant;

public record AccountSnapshot(long balanceKobo, Instant createdAt) {
}
