package com.nepopay.auth;

public record RegisterResponse(
        String username,
        String displayName,
        String accountNumber,
        long balanceKobo,
        String currency,
        String welcomeTransactionReference) {
}
