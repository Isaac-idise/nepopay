package com.nepopay.auth;

public record TokenResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        String username,
        String displayName,
        String role) {
}
