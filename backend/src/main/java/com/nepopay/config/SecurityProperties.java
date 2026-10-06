package com.nepopay.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "nepopay.security")
public record SecurityProperties(
        String jwtSecret,
        long accessTokenMinutes,
        long refreshTokenDays,
        boolean cookieSecure,
        String cookieSameSite) {

    public SecurityProperties {
        if (jwtSecret == null || jwtSecret.length() < 32) {
            throw new IllegalStateException("nepopay.security.jwt-secret must be at least 32 characters (set JWT_SECRET)");
        }
        if (accessTokenMinutes <= 0 || refreshTokenDays <= 0) {
            throw new IllegalStateException("Token lifetimes must be positive");
        }
    }
}
